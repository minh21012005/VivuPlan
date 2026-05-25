package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.AdminDto;
import com.vivuplan.vivuplan_be.entity.PaymentOrder;
import com.vivuplan.vivuplan_be.entity.Role;
import com.vivuplan.vivuplan_be.entity.Trip;
import com.vivuplan.vivuplan_be.entity.User;
import com.vivuplan.vivuplan_be.repository.PaymentOrderRepository;
import com.vivuplan.vivuplan_be.repository.RoleRepository;
import com.vivuplan.vivuplan_be.repository.TripRepository;
import com.vivuplan.vivuplan_be.repository.UserRepository;
import com.vivuplan.vivuplan_be.repository.UserWalletRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TripRepository tripRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final UserWalletRepository userWalletRepository;

    @Transactional(readOnly = true)
    public AdminDto.StatsResponse getStats() {
        AdminDto.StatsResponse stats = new AdminDto.StatsResponse();
        stats.setTotalUsers(userRepository.count());
        stats.setAdminUsers(userRepository.countByRoleName(Role.RoleName.ADMIN));
        stats.setTotalTrips(tripRepository.count());
        stats.setPublicTrips(tripRepository.countByIsPublicTrue());
        stats.setDraftTrips(tripRepository.countByStatus(Trip.TripStatus.DRAFT));
        stats.setPlannedTrips(tripRepository.countByStatus(Trip.TripStatus.PLANNED));
        stats.setCompletedTrips(tripRepository.countByStatus(Trip.TripStatus.COMPLETED));
        stats.setPaidOrders(paymentOrderRepository.countByStatus(PaymentOrder.Status.PAID));
        stats.setTotalRevenue(paymentOrderRepository.sumPaidAmountByStatus(PaymentOrder.Status.PAID));
        return stats;
    }

    @Transactional(readOnly = true)
    public Page<AdminDto.UserSummary> listUsers(int page, int size, String q, String role, String provider) {
        return userRepository.findAll(
                userSpec(q, parseRoleOrNull(role), parseProviderOrNull(provider)),
                PageRequest.of(page, clampPageSize(size), Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(AdminDto.UserSummary::from);
    }

    @Transactional(readOnly = true)
    public AdminDto.UserDetail getUserDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
        AdminDto.UserDetail detail = new AdminDto.UserDetail();
        detail.setUser(AdminDto.UserSummary.from(user));
        detail.setWallet(AdminDto.WalletSummary.from(userWalletRepository.findByUserId(userId).orElse(null)));
        detail.setTotalTrips(tripRepository.countByUserId(userId));
        detail.setPaidOrders(paymentOrderRepository.countByUserIdAndStatus(userId, PaymentOrder.Status.PAID));
        detail.setTotalPaid(paymentOrderRepository.sumPaidAmountByUserIdAndStatus(userId, PaymentOrder.Status.PAID));
        detail.setRecentTrips(tripRepository.findTop8ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(AdminDto.TripSummary::from)
                .toList());
        detail.setRecentOrders(paymentOrderRepository.findTop8ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(AdminDto.TransactionSummary::from)
                .toList());
        return detail;
    }

    @Transactional(readOnly = true)
    public Page<AdminDto.TripSummary> listTrips(int page, int size, String q, String status, String visibility) {
        return tripRepository.findAll(
                tripSpec(q, parseTripStatusOrNull(status), parseVisibilityOrNull(visibility)),
                PageRequest.of(page, clampPageSize(size), Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(AdminDto.TripSummary::from);
    }

    @Transactional(readOnly = true)
    public User getTripOwner(Long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Lịch trình không tồn tại"))
                .getUser();
    }

    @Transactional(readOnly = true)
    public Page<AdminDto.TransactionSummary> listTransactions(int page, int size, String q, String status) {
        return paymentOrderRepository.findAll(
                transactionSpec(q, parsePaymentStatusOrNull(status)),
                PageRequest.of(page, clampPageSize(size), Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(AdminDto.TransactionSummary::from);
    }

    @Transactional
    public AdminDto.UserSummary updateUserRole(Long actorUserId, Long userId, String role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
        Role newRole = getOrCreateRole(parseRole(role));
        Role userRole = getOrCreateRole(Role.RoleName.USER);
        if (actorUserId != null
                && actorUserId.equals(userId)
                && user.hasRole(Role.RoleName.ADMIN)
                && newRole.getName() == Role.RoleName.USER) {
            throw new IllegalArgumentException("Không thể tự hạ quyền tài khoản admin đang đăng nhập");
        }
        if (user.hasRole(Role.RoleName.ADMIN)
                && newRole.getName() == Role.RoleName.USER
                && userRepository.countByRoleName(Role.RoleName.ADMIN) <= 1) {
            throw new IllegalArgumentException("Không thể hạ quyền admin cuối cùng của hệ thống");
        }
        user.getRoles().clear();
        user.getRoles().add(userRole);
        if (newRole.getName() == Role.RoleName.ADMIN) {
            user.getRoles().add(newRole);
        }
        return AdminDto.UserSummary.from(userRepository.save(user));
    }

    @Transactional
    public AdminDto.UserSummary updateUserLock(Long actorUserId, Long userId, Boolean locked) {
        if (locked == null) {
            throw new IllegalArgumentException("Trạng thái khóa không hợp lệ");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));

        if (actorUserId != null && actorUserId.equals(userId) && locked) {
            throw new IllegalArgumentException("Không thể tự khóa tài khoản admin đang đăng nhập");
        }

        if (locked
                && user.hasRole(Role.RoleName.ADMIN)
                && userRepository.countUnlockedByRoleName(Role.RoleName.ADMIN) <= 1) {
            throw new IllegalArgumentException("Không thể khóa admin cuối cùng của hệ thống");
        }

        user.setAccountLocked(locked);
        return AdminDto.UserSummary.from(userRepository.save(user));
    }

    private Role.RoleName parseRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role không được để trống");
        }
        try {
            return Role.RoleName.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Role không hợp lệ. Chỉ hỗ trợ USER hoặc ADMIN");
        }
    }

    private Role.RoleName parseRoleOrNull(String role) {
        if (role == null || role.isBlank() || "ALL".equalsIgnoreCase(role)) return null;
        return parseRole(role);
    }

    private User.AuthProvider parseProviderOrNull(String provider) {
        if (provider == null || provider.isBlank() || "ALL".equalsIgnoreCase(provider)) return null;
        try {
            return User.AuthProvider.valueOf(provider.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Provider không hợp lệ. Chỉ hỗ trợ LOCAL hoặc GOOGLE");
        }
    }

    private Trip.TripStatus parseTripStatusOrNull(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) return null;
        try {
            return Trip.TripStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái lịch trình không hợp lệ");
        }
    }

    private Boolean parseVisibilityOrNull(String visibility) {
        if (visibility == null || visibility.isBlank() || "ALL".equalsIgnoreCase(visibility)) return null;
        return switch (visibility.trim().toUpperCase(Locale.ROOT)) {
            case "PUBLIC" -> true;
            case "PRIVATE" -> false;
            default -> throw new IllegalArgumentException("Bộ lọc hiển thị không hợp lệ");
        };
    }

    private PaymentOrder.Status parsePaymentStatusOrNull(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) return null;
        try {
            return PaymentOrder.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái giao dịch không hợp lệ");
        }
    }

    private Specification<User> userSpec(String q, Role.RoleName role, User.AuthProvider provider) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();
            String keyword = normalizeKeyword(q);
            if (keyword != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), keyword),
                        cb.like(cb.lower(root.get("email")), keyword)
                ));
            }
            if (role != null) {
                predicates.add(cb.equal(root.join("roles", JoinType.LEFT).get("name"), role));
            }
            if (provider != null) {
                predicates.add(cb.equal(root.get("provider"), provider));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<Trip> tripSpec(String q, Trip.TripStatus status, Boolean visibility) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            String keyword = normalizeKeyword(q);
            if (keyword != null) {
                var userJoin = root.join("user", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("destination")), keyword),
                        cb.like(cb.lower(root.get("departure")), keyword),
                        cb.like(cb.lower(userJoin.get("email")), keyword)
                ));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (visibility != null) {
                predicates.add(cb.equal(root.get("isPublic"), visibility));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<PaymentOrder> transactionSpec(String q, PaymentOrder.Status status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            String keyword = normalizeKeyword(q);
            if (keyword != null) {
                var userJoin = root.join("user", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("orderCode")), keyword),
                        cb.like(cb.lower(root.get("packageCode")), keyword),
                        cb.like(cb.lower(userJoin.get("email")), keyword)
                ));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String normalizeKeyword(String q) {
        if (q == null || q.isBlank()) return null;
        return "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private Role getOrCreateRole(Role.RoleName roleName) {
        return roleRepository.findByName(roleName).orElseGet(() ->
                roleRepository.save(Role.builder()
                        .name(roleName)
                        .description(roleName == Role.RoleName.ADMIN ? "System administrator" : "Standard user")
                        .build())
        );
    }

    private int clampPageSize(int size) {
        if (size < 1) return 20;
        return Math.min(size, 100);
    }
}
