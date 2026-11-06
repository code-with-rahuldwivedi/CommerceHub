package com.shopsphere.userservice.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.shopsphere.userservice.exception.InvalidRequestException;

/**
 * Password rules (NIST 800-63B style): length, not a well-known password, not derived from the
 * e-mail address. No arbitrary composition rules. The BCrypt 72-BYTE limit is enforced here
 * (multi-byte characters count more than one byte).
 */
@Component
public class PasswordPolicy {

    static final int MIN_LENGTH = 8;
    static final int MAX_BYTES = 72;

    private final Set<String> commonPasswords = new HashSet<>();

    public PasswordPolicy() {
        try (InputStream in = PasswordPolicy.class.getResourceAsStream("/common-passwords.txt")) {
            if (in == null) {
                throw new IllegalStateException("common-passwords.txt missing from classpath");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                reader.lines()
                        .map(line -> line.trim().toLowerCase(Locale.ROOT))
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(commonPasswords::add);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void validate(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new InvalidRequestException("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidRequestException("Password must be at most " + MAX_BYTES + " bytes");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (commonPasswords.contains(lower)) {
            throw new InvalidRequestException("Password is too common, choose a less predictable one");
        }
        if (email != null) {
            int at = email.indexOf('@');
            String local = (at > 0 ? email.substring(0, at) : email).toLowerCase(Locale.ROOT);
            if (local.length() >= 4 && lower.contains(local)) {
                throw new InvalidRequestException("Password must not contain your email name");
            }
        }
    }
}
