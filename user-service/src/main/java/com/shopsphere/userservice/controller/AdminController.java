package com.shopsphere.userservice.controller;

import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopsphere.userservice.dto.AdminUserResponse;
import com.shopsphere.userservice.dto.ChangeRoleRequest;
import com.shopsphere.userservice.dto.SetEnabledRequest;
import com.shopsphere.userservice.security.JwtPrincipal;
import com.shopsphere.userservice.service.AdminUserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminUserService adminUsers;

    public AdminController(AdminUserService adminUsers) {
        this.adminUsers = adminUsers;
    }

    @GetMapping("/users")
    public PagedModel<AdminUserResponse> listUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 100) String email) {
        return new PagedModel<>(adminUsers.list(page, size, email));
    }

    @GetMapping("/users/{id}")
    public AdminUserResponse getUser(@PathVariable long id) {
        return adminUsers.get(id);
    }

    @PutMapping("/users/{id}/enabled")
    public ResponseEntity<Void> setEnabled(@AuthenticationPrincipal JwtPrincipal admin,
                                           @PathVariable long id,
                                           @Valid @RequestBody SetEnabledRequest request) {
        adminUsers.setEnabled(admin.userId(), id, request.enabled());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<Void> changeRole(@AuthenticationPrincipal JwtPrincipal admin,
                                           @PathVariable long id,
                                           @Valid @RequestBody ChangeRoleRequest request) {
        adminUsers.changeRole(admin.userId(), id, request.role());
        return ResponseEntity.noContent().build();
    }

    /** Clears a lockout / failed-attempt counter. */
    @PostMapping("/users/{id}/unlock")
    public ResponseEntity<Void> unlock(@AuthenticationPrincipal JwtPrincipal admin, @PathVariable long id) {
        adminUsers.unlock(admin.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
