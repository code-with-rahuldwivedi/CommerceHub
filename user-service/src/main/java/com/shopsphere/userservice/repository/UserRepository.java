package com.shopsphere.userservice.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Page<User> findByEmailContainingIgnoreCase(String fragment, Pageable pageable);

    long countByRoleAndEnabledTrue(Role role);

    // Atomic counters: no read-modify-write race between concurrent failed logins.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.failedAttempts = u.failedAttempts + 1 where u.email = :email")
    int incrementFailedAttempts(@Param("email") String email);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.lockedUntil = :until, u.failedAttempts = 0 "
         + "where u.email = :email and u.failedAttempts >= :max")
    int lockIfThresholdReached(@Param("email") String email,
                               @Param("until") LocalDateTime until,
                               @Param("max") int max);

    @Modifying
    @Query("update User u set u.failedAttempts = 0, u.lockedUntil = null where u.id = :id")
    int resetLoginState(@Param("id") Long id);
}
