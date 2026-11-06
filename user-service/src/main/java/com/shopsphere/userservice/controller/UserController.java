package com.shopsphere.userservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopsphere.userservice.dto.ChangePasswordRequest;
import com.shopsphere.userservice.dto.UpdateProfileRequest;
import com.shopsphere.userservice.dto.UserResponse;
import com.shopsphere.userservice.security.JwtPrincipal;
import com.shopsphere.userservice.service.AccountService;
import com.shopsphere.userservice.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final AccountService accountService;

    public UserController(UserService userService, AccountService accountService) {
        this.userService = userService;
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal JwtPrincipal principal) {
        return userService.getById(principal.userId());
    }

    @PatchMapping("/me")
    public UserResponse updateProfile(@AuthenticationPrincipal JwtPrincipal principal,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateName(principal.userId(), request.name());
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal JwtPrincipal principal,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(principal.userId(), request);
        return ResponseEntity.noContent().build();
    }

    /** Sign out of every device (revokes all refresh tokens). */
    @DeleteMapping("/me/sessions")
    public ResponseEntity<Void> revokeAllSessions(@AuthenticationPrincipal JwtPrincipal principal) {
        accountService.revokeAllSessions(principal.userId());
        return ResponseEntity.noContent().build();
    }
}
