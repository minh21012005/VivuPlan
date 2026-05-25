package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_users_email", columnList = "email"),
                @Index(name = "idx_users_google_id", columnList = "google_id"),
                @Index(name = "idx_users_created_at", columnList = "created_at")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column
    private String password;

    @Column
    private String avatarUrl;

    @Column(unique = true)
    private String googleId;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_user_roles_user_role", columnNames = {"user_id", "role_id"}),
            indexes = {
                    @Index(name = "idx_user_roles_user", columnList = "user_id"),
                    @Index(name = "idx_user_roles_role", columnList = "role_id")
            }
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    @Column(nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean accountLocked = false;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<Trip> trips = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum AuthProvider { LOCAL, GOOGLE }

    public Set<String> getRoleNames() {
        return roles == null ? Set.of() : roles.stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toSet());
    }

    public String getPrimaryRoleName() {
        Set<String> names = getRoleNames();
        return names.contains(Role.RoleName.ADMIN.name()) ? Role.RoleName.ADMIN.name() : Role.RoleName.USER.name();
    }

    public boolean hasRole(Role.RoleName roleName) {
        return getRoleNames().contains(roleName.name());
    }

    public boolean isAccountLocked() {
        return Boolean.TRUE.equals(accountLocked);
    }
}
