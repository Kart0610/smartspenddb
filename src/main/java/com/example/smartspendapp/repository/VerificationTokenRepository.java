package com.example.smartspendapp.repository;

import com.example.smartspendapp.model.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    // existing: find by token (UUID style)
    Optional<VerificationToken> findByToken(String token);

    // new: find by OTP (numeric code string)
    Optional<VerificationToken> findByOtp(String otp);
    
    /**
     * Find a valid (not expired) token for a user of a given type (e.g. "EMAIL_VERIFICATION" or "OTP")
     * matching a specific token string.
     */
    @Query("SELECT v FROM VerificationToken v WHERE v.user.id = :userId " +
           "AND v.token = :token AND v.type = :type AND v.expiresAt > :now")
    Optional<VerificationToken> findValidTokenForUserAndType(
            @Param("userId") Long userId,
            @Param("token") String token,
            @Param("type") String type,
            @Param("now") Instant now
    );

    /**
     * Optional helper: find the most recent token for a user and type (useful to check latest OTP).
     * Spring Data will implement this from method name.
     */
    Optional<VerificationToken> findTopByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type);
}
