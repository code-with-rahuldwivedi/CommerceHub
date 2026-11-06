package com.shopsphere.userservice.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.userservice.dto.AdminUserResponse;
import com.shopsphere.userservice.entity.AuditAction;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.OperationNotAllowedException;
import com.shopsphere.userservice.exception.UserNotFoundException;
import com.shopsphere.userservice.repository.UserRepository;

/** Administrative operations. Every mutation is written to the audit trail with the acting admin. */
@Service
public class AdminUserService {

    private final UserRepository users;
    private final RefreshTokenService refreshTokens;
    private final AuditService audit;
    private final Clock clock;

    public AdminUserService(UserRepository users, RefreshTokenService refreshTokens,
                            AuditService audit, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.clock = clock;
    }

    /** Sort is fixed (by id): callers must never sort by e.g. the password hash column. */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> list(int page, int size, String emailFilter) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id"));
        LocalDateTime now = LocalDateTime.now(clock);
        Page<User> result = (emailFilter == null || emailFilter.isBlank())
                ? users.findAll(pageable)
                : users.findByEmailContainingIgnoreCase(emailFilter.trim(), pageable);
        return result.map(u -> AdminUserResponse.from(u, now));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(long id) {
        return AdminUserResponse.from(find(id), LocalDateTime.now(clock));
    }

    @Transactional
    public void setEnabled(long actorId, long targetId, boolean enabled) {
        if (actorId == targetId && !enabled) {
            throw new OperationNotAllowedException("You cannot disable your own account");
        }
        User user = find(targetId);
        if (!enabled && user.getRole() == Role.ADMIN && user.isEnabled()
                && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new OperationNotAllowedException("Cannot disable the last active administrator");
        }
        user.setEnabled(enabled);
        if (!enabled) {
            refreshTokens.revokeAllForUser(targetId); // cut off new access tokens immediately
        }
        audit.record(enabled ? AuditAction.USER_ENABLED : AuditAction.USER_DISABLED, actorId, targetId, null);
    }

    @Transactional
    public void changeRole(long actorId, long targetId, Role newRole) {
        if (actorId == targetId) {
            throw new OperationNotAllowedException("You cannot change your own role");
        }
        User user = find(targetId);
        Role oldRole = user.getRole();
        if (oldRole == newRole) {
            return;
        }
        if (oldRole == Role.ADMIN && user.isEnabled() && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new OperationNotAllowedException("Cannot demote the last active administrator");
        }
        user.setRole(newRole);
        audit.record(AuditAction.ROLE_CHANGED, actorId, targetId, oldRole + " -> " + newRole);
    }

    @Transactional
    public void unlock(long actorId, long targetId) {
        if (!users.existsById(targetId)) {
            throw new UserNotFoundException();
        }
        users.resetLoginState(targetId);
        audit.record(AuditAction.ACCOUNT_UNLOCKED, actorId, targetId, null);
    }

    private User find(long id) {
        return users.findById(id).orElseThrow(UserNotFoundException::new);
    }
}
