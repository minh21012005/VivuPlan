package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.AdminDto;
import com.vivuplan.vivuplan_be.entity.AiUsageLog;
import com.vivuplan.vivuplan_be.entity.PaymentOrder;
import com.vivuplan.vivuplan_be.entity.Role;
import com.vivuplan.vivuplan_be.entity.Trip;
import com.vivuplan.vivuplan_be.entity.User;
import com.vivuplan.vivuplan_be.repository.AiUsageLogRepository;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TripRepository tripRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final UserWalletRepository userWalletRepository;
    private final AiUsageLogRepository aiUsageLogRepository;

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
    public Page<AdminDto.TripSummary> listTrips(int page, int size, String q) {
        return tripRepository.findAll(
                tripSpec(q),
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

    @Transactional(readOnly = true)
    public AdminDto.AiCostSummaryResponse aiCostSummary(
            LocalDate from,
            LocalDate to,
            String operation,
            String status) {
        List<AiUsageLog> logs = filterAiUsageLogs(
                findAiUsageInRange(from, to),
                parseAiOperationOrNull(operation),
                parseAiStatusOrNull(status));
        AdminDto.AiCostSummaryResponse response = new AdminDto.AiCostSummaryResponse();
        fillTotals(response, logs);
        response.setRequests(countRequests(logs));
        long failedRequests = countFailedRequests(logs);
        long retryAttempts = logs.stream().filter(log -> safeInt(log.getAttemptNumber()) > 1).count();
        response.setRetryRate(response.getAttempts() > 0
                ? roundDouble(retryAttempts * 100.0 / response.getAttempts())
                : 0);
        response.setErrorRate(response.getRequests() > 0
                ? roundDouble(failedRequests * 100.0 / response.getRequests())
                : 0);
        response.setAvgDurationMs(avgRequestDurationMs(logs));
        response.setOperationBreakdown(groupBreakdown(logs, log -> log.getOperation().name(), this::aiOperationLabel));
        response.setAverageCosts(buildAverageCosts(logs));
        response.setOperationHealth(buildOperationHealth(logs));
        response.setTopCostRequests(buildTopCostRequests(logs));
        return response;
    }

    @Transactional(readOnly = true)
    public List<AdminDto.AiCostDaily> aiCostDaily(
            LocalDate from,
            LocalDate to,
            String operation,
            String status) {
        List<AiUsageLog> logs = filterAiUsageLogs(
                findAiUsageInRange(from, to),
                parseAiOperationOrNull(operation),
                parseAiStatusOrNull(status));
        Map<LocalDate, List<AiUsageLog>> byDate = logs.stream()
                .filter(log -> log.getCreatedAt() != null)
                .collect(Collectors.groupingBy(log -> log.getCreatedAt().toLocalDate(), LinkedHashMap::new, Collectors.toList()));

        List<AdminDto.AiCostDaily> result = new ArrayList<>();
        LocalDate start = normalizeFrom(from);
        LocalDate end = normalizeTo(to);
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            List<AiUsageLog> dayLogs = byDate.getOrDefault(date, List.of());
            AdminDto.AiCostDaily item = new AdminDto.AiCostDaily();
            item.setDate(date.toString());
            item.setTotalCostVnd(sumCostVnd(dayLogs));
            result.add(item);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Page<AdminDto.AiUsageEvent> aiUsageEvents(
            int page,
            int size,
            LocalDate from,
            LocalDate to,
            String operation,
            String status,
            String q) {
        AiUsageLog.Operation parsedOperation = parseAiOperationOrNull(operation);
        AiUsageLog.Status parsedStatus = parseAiStatusOrNull(status);
        LocalDateTime fromTime = normalizeFrom(from).atStartOfDay();
        LocalDateTime toTime = normalizeTo(to).plusDays(1).atStartOfDay();
        return aiUsageLogRepository.findAll(
                aiUsageSpec(fromTime, toTime, parsedOperation, parsedStatus, q),
                PageRequest.of(page, clampPageSize(size), Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(this::toAiUsageEvent);
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

    private List<AiUsageLog> findAiUsageInRange(LocalDate from, LocalDate to) {
        LocalDateTime fromTime = normalizeFrom(from).atStartOfDay();
        LocalDateTime toTime = normalizeTo(to).plusDays(1).atStartOfDay();
        return aiUsageLogRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(fromTime, toTime);
    }

    private List<AiUsageLog> filterAiUsageLogs(
            List<AiUsageLog> logs,
            AiUsageLog.Operation operation,
            AiUsageLog.Status status) {
        return logs.stream()
                .filter(log -> operation == null || log.getOperation() == operation)
                .filter(log -> status == null || log.getStatus() == status)
                .toList();
    }

    private void fillTotals(AdminDto.AiCostSummaryResponse response, List<AiUsageLog> logs) {
        response.setAttempts(logs.size());
        response.setTotalCostVnd(sumCostVnd(logs));
        response.setPromptTokens(sumPrompt(logs));
        response.setOutputTokens(sumOutput(logs));
        response.setThinkingTokens(sumThinking(logs));
        response.setTotalTokens(sumTotalTokens(logs));
    }

    private List<AdminDto.AiCostBreakdown> groupBreakdown(
            List<AiUsageLog> logs,
            java.util.function.Function<AiUsageLog, String> keyFn,
            java.util.function.Function<String, String> labelFn) {
        return logs.stream()
                .collect(Collectors.groupingBy(log -> nullToUnknown(keyFn.apply(log)), HashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, List<AiUsageLog>>comparingByKey())
                .map(entry -> {
                    AdminDto.AiCostBreakdown item = new AdminDto.AiCostBreakdown();
                    item.setKey(entry.getKey());
                    item.setLabel(labelFn.apply(entry.getKey()));
                    item.setAttempts(entry.getValue().size());
                    item.setTotalCostVnd(sumCostVnd(entry.getValue()));
                    return item;
                })
                .toList();
    }

    private List<AdminDto.AiOperationAverage> buildAverageCosts(List<AiUsageLog> logs) {
        return logs.stream()
                .collect(Collectors.groupingBy(AiUsageLog::getOperation, HashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                .map(entry -> {
                    long operations = entry.getValue().stream()
                            .map(AiUsageLog::getRequestId)
                            .filter(value -> value != null && !value.isBlank())
                            .distinct()
                            .count();
                    long costVnd = sumCostVnd(entry.getValue());
                    AdminDto.AiOperationAverage item = new AdminDto.AiOperationAverage();
                    item.setOperation(entry.getKey().name());
                    item.setLabel(aiOperationLabel(entry.getKey().name()));
                    item.setOperations(operations);
                    item.setAvgCostVnd(operations > 0 ? Math.round((double) costVnd / operations) : 0);
                    return item;
                })
                .toList();
    }

    private List<AdminDto.AiOperationHealth> buildOperationHealth(List<AiUsageLog> logs) {
        return logs.stream()
                .collect(Collectors.groupingBy(AiUsageLog::getOperation, HashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                .map(entry -> {
                    List<AiUsageLog> operationLogs = entry.getValue();
                    long requests = countRequests(operationLogs);
                    long failedRequests = countFailedRequests(operationLogs);
                    long retryAttempts = operationLogs.stream().filter(log -> safeInt(log.getAttemptNumber()) > 1).count();
                    AdminDto.AiOperationHealth item = new AdminDto.AiOperationHealth();
                    item.setOperation(entry.getKey().name());
                    item.setLabel(aiOperationLabel(entry.getKey().name()));
                    item.setRequests(requests);
                    item.setAttempts(operationLogs.size());
                    item.setRetryRate(operationLogs.isEmpty()
                            ? 0
                            : roundDouble(retryAttempts * 100.0 / operationLogs.size()));
                    item.setErrorRate(requests > 0 ? roundDouble(failedRequests * 100.0 / requests) : 0);
                    item.setAvgDurationMs(avgRequestDurationMs(operationLogs));
                    item.setMaxDurationMs(maxRequestDurationMs(operationLogs));
                    item.setTotalCostVnd(sumCostVnd(operationLogs));
                    return item;
                })
                .toList();
    }

    private List<AdminDto.AiRequestSummary> buildTopCostRequests(List<AiUsageLog> logs) {
        return groupByRequest(logs).values().stream()
                .map(this::toAiRequestSummary)
                .sorted(Comparator.comparingLong(AdminDto.AiRequestSummary::getTotalCostVnd).reversed()
                        .thenComparing(AdminDto.AiRequestSummary::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();
    }

    private AdminDto.AiRequestSummary toAiRequestSummary(List<AiUsageLog> requestLogs) {
        AiUsageLog latest = latestAttempt(requestLogs);
        AiUsageLog first = requestLogs.stream()
                .min(Comparator.comparing(AiUsageLog::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(latest);
        AdminDto.AiRequestSummary dto = new AdminDto.AiRequestSummary();
        dto.setRequestId(latest.getRequestId());
        dto.setOperation(latest.getOperation() != null ? latest.getOperation().name() : null);
        dto.setStatus(requestSucceeded(requestLogs) ? AiUsageLog.Status.SUCCESS.name()
                : latest.getStatus() != null ? latest.getStatus().name() : null);
        dto.setUserId(latest.getUser() != null ? latest.getUser().getId() : null);
        dto.setUserEmail(latest.getUser() != null ? latest.getUser().getEmail() : null);
        dto.setTripId(latest.getTrip() != null ? latest.getTrip().getId() : null);
        dto.setAttempts(requestLogs.size());
        dto.setTotalCostVnd(sumCostVnd(requestLogs));
        dto.setTotalTokens(sumTotalTokens(requestLogs));
        dto.setCreatedAt(first.getCreatedAt() != null ? first.getCreatedAt().toString() : null);
        return dto;
    }

    private Map<String, List<AiUsageLog>> groupByRequest(List<AiUsageLog> logs) {
        return logs.stream()
                .filter(log -> log.getRequestId() != null && !log.getRequestId().isBlank())
                .collect(Collectors.groupingBy(AiUsageLog::getRequestId, LinkedHashMap::new, Collectors.toList()));
    }

    private AiUsageLog latestAttempt(List<AiUsageLog> logs) {
        return logs.stream()
                .max(Comparator.comparingInt(log -> safeInt(log.getAttemptNumber())))
                .orElseThrow();
    }

    private long countRequests(List<AiUsageLog> logs) {
        return groupByRequest(logs).size();
    }

    private long countFailedRequests(List<AiUsageLog> logs) {
        return groupByRequest(logs).values().stream()
                .filter(requestLogs -> !requestSucceeded(requestLogs))
                .count();
    }

    private boolean requestSucceeded(List<AiUsageLog> requestLogs) {
        return requestLogs.stream().anyMatch(log -> log.getStatus() == AiUsageLog.Status.SUCCESS);
    }

    private long avgRequestDurationMs(List<AiUsageLog> logs) {
        List<Long> durations = groupByRequest(logs).values().stream()
                .map(this::sumDurationMs)
                .filter(value -> value > 0)
                .toList();
        if (durations.isEmpty()) {
            return 0;
        }
        return Math.round(durations.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private long maxRequestDurationMs(List<AiUsageLog> logs) {
        return groupByRequest(logs).values().stream()
                .mapToLong(this::sumDurationMs)
                .max()
                .orElse(0);
    }

    private long sumDurationMs(List<AiUsageLog> logs) {
        return logs.stream()
                .mapToLong(log -> log.getDurationMs() != null && log.getDurationMs() > 0 ? log.getDurationMs() : 0)
                .sum();
    }

    private AdminDto.AiUsageEvent toAiUsageEvent(AiUsageLog log) {
        AdminDto.AiUsageEvent dto = new AdminDto.AiUsageEvent();
        dto.setId(log.getId());
        dto.setRequestId(log.getRequestId());
        dto.setAttemptNumber(log.getAttemptNumber());
        dto.setOperation(log.getOperation() != null ? log.getOperation().name() : null);
        dto.setStatus(log.getStatus() != null ? log.getStatus().name() : null);
        dto.setUserId(log.getUser() != null ? log.getUser().getId() : null);
        dto.setUserEmail(log.getUser() != null ? log.getUser().getEmail() : null);
        dto.setTripId(log.getTrip() != null ? log.getTrip().getId() : null);
        dto.setModel(log.getModel());
        dto.setFinishReason(log.getFinishReason());
        dto.setDurationMs(log.getDurationMs());
        dto.setPromptTokens(log.getPromptTokens());
        dto.setOutputTokens(log.getOutputTokens());
        dto.setThinkingTokens(log.getThinkingTokens());
        dto.setTotalTokens(log.getTotalTokens());
        dto.setMaxOutputTokens(log.getMaxOutputTokens());
        dto.setThinkingBudget(log.getThinkingBudget());
        dto.setEstimatedCostVnd(log.getEstimatedCostVnd() != null ? log.getEstimatedCostVnd() : 0);
        dto.setEstimatedCostUsd(log.getEstimatedCostUsd() != null ? log.getEstimatedCostUsd().doubleValue() : 0);
        dto.setErrorCode(log.getErrorCode());
        dto.setErrorMessage(log.getErrorMessage());
        dto.setCreatedAt(log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
        return dto;
    }

    private Specification<AiUsageLog> aiUsageSpec(
            LocalDateTime from,
            LocalDateTime to,
            AiUsageLog.Operation operation,
            AiUsageLog.Status status,
            String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            predicates.add(cb.lessThan(root.get("createdAt"), to));
            if (operation != null) {
                predicates.add(cb.equal(root.get("operation"), operation));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            String keyword = normalizeKeyword(q);
            if (keyword != null) {
                var userJoin = root.join("user", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("requestId")), keyword),
                        cb.like(cb.lower(root.get("model")), keyword),
                        cb.like(cb.lower(userJoin.get("email")), keyword)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private long sumCostVnd(List<AiUsageLog> logs) {
        return logs.stream().mapToLong(log -> log.getEstimatedCostVnd() != null ? log.getEstimatedCostVnd() : 0L).sum();
    }

    private long sumPrompt(List<AiUsageLog> logs) {
        return logs.stream().mapToLong(log -> safeInt(log.getPromptTokens())).sum();
    }

    private long sumOutput(List<AiUsageLog> logs) {
        return logs.stream().mapToLong(log -> safeInt(log.getOutputTokens())).sum();
    }

    private long sumThinking(List<AiUsageLog> logs) {
        return logs.stream().mapToLong(log -> safeInt(log.getThinkingTokens())).sum();
    }

    private long sumTotalTokens(List<AiUsageLog> logs) {
        return logs.stream().mapToLong(log -> safeInt(log.getTotalTokens())).sum();
    }

    private int safeInt(Integer value) {
        return value != null && value > 0 ? value : 0;
    }

    private double roundDouble(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private LocalDate normalizeFrom(LocalDate from) {
        return from != null ? from : LocalDate.now().minusDays(29);
    }

    private LocalDate normalizeTo(LocalDate to) {
        return to != null ? to : LocalDate.now();
    }

    private AiUsageLog.Operation parseAiOperationOrNull(String operation) {
        if (operation == null || operation.isBlank() || "ALL".equalsIgnoreCase(operation)) return null;
        try {
            return AiUsageLog.Operation.valueOf(operation.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Loai AI call khong hop le");
        }
    }

    private AiUsageLog.Status parseAiStatusOrNull(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) return null;
        try {
            return AiUsageLog.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trang thai AI call khong hop le");
        }
    }

    private String aiOperationLabel(String operation) {
        return switch (operation) {
            case "PLAN_GENERATION" -> "Tao lich trinh";
            case "DAY_REGENERATION" -> "Chinh ngay";
            case "DESTINATION_SUGGESTION" -> "Goi y diem den";
            default -> operation;
        };
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

    private Specification<Trip> tripSpec(String q) {
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
