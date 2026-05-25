package com.vivuplan.vivuplan_be.controller;

import com.vivuplan.vivuplan_be.dto.AdminDto;
import com.vivuplan.vivuplan_be.dto.TripDto;
import com.vivuplan.vivuplan_be.service.AdminService;
import com.vivuplan.vivuplan_be.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final TripService tripService;

    @GetMapping("/stats")
    public ResponseEntity<AdminDto.StatsResponse> stats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @GetMapping("/users")
    public ResponseEntity<Page<AdminDto.UserSummary>> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String provider) {
        return ResponseEntity.ok(adminService.listUsers(page, size, q, role, provider));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminDto.UserDetail> userDetail(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUserDetail(id));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<AdminDto.UserSummary> updateUserRole(
            @PathVariable Long id,
            @RequestBody AdminDto.UpdateUserRoleRequest req,
            Authentication auth) {
        Long actorUserId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(adminService.updateUserRole(actorUserId, id, req.getRole()));
    }

    @PatchMapping("/users/{id}/lock")
    public ResponseEntity<AdminDto.UserSummary> updateUserLock(
            @PathVariable Long id,
            @RequestBody AdminDto.UpdateUserLockRequest req,
            Authentication auth) {
        Long actorUserId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(adminService.updateUserLock(actorUserId, id, req.getLocked()));
    }

    @GetMapping("/trips")
    public ResponseEntity<Page<AdminDto.TripSummary>> trips(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String visibility) {
        return ResponseEntity.ok(adminService.listTrips(page, size, q, status, visibility));
    }

    @GetMapping("/trips/{id}")
    public ResponseEntity<AdminDto.TripDetail> tripDetail(@PathVariable Long id) {
        return ResponseEntity.ok(AdminDto.TripDetail.of(
                tripService.getTripForAdmin(id),
                adminService.getTripOwner(id)
        ));
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<AdminDto.TransactionSummary>> transactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminService.listTransactions(page, size, q, status));
    }
}
