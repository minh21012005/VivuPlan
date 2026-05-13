package com.vivuplan.vivuplan_be.controller;

import com.vivuplan.vivuplan_be.dto.AdminDto;
import com.vivuplan.vivuplan_be.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    public ResponseEntity<AdminDto.StatsResponse> stats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @GetMapping("/users")
    public ResponseEntity<Page<AdminDto.UserSummary>> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.listUsers(page, size));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<AdminDto.UserSummary> updateUserRole(
            @PathVariable Long id,
            @RequestBody AdminDto.UpdateUserRoleRequest req) {
        return ResponseEntity.ok(adminService.updateUserRole(id, req.getRole()));
    }

    @GetMapping("/trips")
    public ResponseEntity<Page<AdminDto.TripSummary>> trips(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.listTrips(page, size));
    }
}
