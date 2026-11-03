package com.apexcare.auth.controller;

import com.apexcare.auth.dto.ApiResponse;
import com.apexcare.auth.dto.UpdateUserRequest;
import com.apexcare.auth.dto.UserResponse;
import com.apexcare.auth.security.JwtPrincipal;
import com.apexcare.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // -----------------------------------------------------------------
    // ADMIN — user administration
    // -----------------------------------------------------------------

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users (ADMIN only)")

    public ResponseEntity<ApiResponse<Page<UserResponse>>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Users retrieved",
                userService.findAll(pageable, principal)));
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user by id (ADMIN only)")

    public ResponseEntity<ApiResponse<UserResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("User retrieved",
                userService.findById(id)));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user (ADMIN only)",
            description = "Updates profile fields. Role, enabled status, and "
                    + "patient-record linking require dedicated endpoints.")

    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok("User updated",
                userService.update(id, request, principal)));
    }

    @PostMapping("/users/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve / Accept pending staff user (ADMIN only)")
    public ResponseEntity<ApiResponse<UserResponse>> approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Staff account approved successfully",
                userService.approveUser(id, principal)));
    }

    // -----------------------------------------------------------------
    // STAFF — doctor lookup (for booking flows, dropdowns, validation)
    // -----------------------------------------------------------------

    @GetMapping("/doctors")
    @Operation(summary = "List active doctors",
            description = "Available to public and authenticated users — used by "
                    + "booking UIs and the appointment-service.")
    public ResponseEntity<ApiResponse<List<UserResponse>>> doctors() {
        return ResponseEntity.ok(ApiResponse.ok("Doctors retrieved",
                userService.findDoctors()));
    }

    @GetMapping("/doctors/{id}")
    @Operation(summary = "Validate a doctor by id",
            description = "Used by the appointment-service to validate a doctor "
                    + "before booking.")
    public ResponseEntity<ApiResponse<UserResponse>> doctor(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Doctor retrieved",
                userService.findDoctor(id)));
    }
}
