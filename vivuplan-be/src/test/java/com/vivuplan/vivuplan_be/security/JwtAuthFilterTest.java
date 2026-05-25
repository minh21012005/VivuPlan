package com.vivuplan.vivuplan_be.security;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(userRepository.existsActiveById(7L)).thenReturn(true);
        when(jwtUtil.getEmail("token")).thenReturn("user@example.com");
        when(jwtUtil.getRoles("token")).thenReturn(List.of("USER"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(7L);
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
        when(userRepository.existsActiveById(7L)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
