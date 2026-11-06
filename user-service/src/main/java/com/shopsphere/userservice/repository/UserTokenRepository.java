package com.shopsphere.userservice.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopsphere.userservice.entity.TokenType;
import com.shopsphere.userservice.entity.UserToken;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    Optional<UserToken> findByTokenHash(String tokenHash);

    boolean existsByUserIdAndTypeAndCreatedAtAfter(Long userId, TokenType type, LocalDateTime since);

    /** Returns 0 if the token was already used (lost a race / replay). */
    @Modifying
    @Query("update UserToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update UserToken t set t.usedAt = :now "
         + "where t.userId = :userId and t.type = :type and t.usedAt is null")
    int invalidateOutstanding(@Param("userId") Long userId,
                              @Param("type") TokenType type,
                              @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from UserToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
