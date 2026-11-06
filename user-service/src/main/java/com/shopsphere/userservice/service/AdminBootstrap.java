package com.shopsphere.userservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.shopsphere.userservice.config.AppProperties;
import com.shopsphere.userservice.entity.AuditAction;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.repository.UserRepository;
import com.shopsphere.userservice.util.Emails;
import com.shopsphere.userservice.util.LogSanitizer;

/**
 * Creates the first ADMIN from ADMIN_EMAIL / ADMIN_PASSWORD if (and only if) that
 * account does not exist yet. Remove the env vars after the first successful start.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties.Bootstrap props;
    private final AuditService audit;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder,
                          AppProperties appProperties, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.props = appProperties.bootstrap();
        this.audit = audit;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isBlank(props.adminEmail()) || isBlank(props.adminPassword())) {
            return;
        }
        if (props.adminPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("app.bootstrap.admin-password must be at least "
                    + MIN_PASSWORD_LENGTH + " characters");
        }

        String email = Emails.normalize(props.adminEmail());
        if (users.existsByEmail(email)) {
            return;
        }

        User admin = new User();
        admin.setName(isBlank(props.adminName()) ? "Administrator" : props.adminName().trim());
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(props.adminPassword()));
        admin.setRole(Role.ADMIN);
        admin.setEmailVerified(true);

        try {
            User saved = users.save(admin);
            audit.record(AuditAction.ADMIN_BOOTSTRAPPED, null, saved.getId(), null);
            log.info("Bootstrap admin created: {}", LogSanitizer.maskEmail(email));
        } catch (DataIntegrityViolationException e) {
            // another instance created it first: fine
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
