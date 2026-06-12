package com.vivuplan.vivuplan_be.security;

import com.vivuplan.vivuplan_be.entity.Role;
import com.vivuplan.vivuplan_be.entity.User;
import com.vivuplan.vivuplan_be.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenForUnlockedUserSetsAuthentication() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer token");

        when(jwtUtil.isValid("token")).thenReturn(true);
        when(jwtUtil.getUserId("token")).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(false, Role.RoleName.USER)));

        filter.doFilterInternal(request, response, filterChain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(7L);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void currentDatabaseRolesOverrideStaleAdminRoleInToken() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer token");

        when(jwtUtil.isValid("token")).thenReturn(true);
        when(jwtUtil.getUserId("token")).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(false, Role.RoleName.USER)));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        verify(jwtUtil, never()).getRoles("token");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void validTokenForLockedUserDoesNotSetAuthentication() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer token");

        when(jwtUtil.isValid("token")).thenReturn(true);
        when(jwtUtil.getUserId("token")).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(true, Role.RoleName.ADMIN)));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    private User user(boolean locked, Role.RoleName roleName) {
        return User.builder()
                .id(7L)
                .name("User")
                .email("user@example.com")
                .accountLocked(locked)
                .roles(Set.of(Role.builder().name(roleName).build()))
                .build();
    }
}
