package com.xauat.oj.core.auth.repository;

import com.xauat.oj.core.auth.domain.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    @Query("select s from AuthSession s where s.revoked = false and (s.refreshHash = :hash or s.previousRefreshHash = :hash)")
    Optional<AuthSession> findByRefreshHashAndRevokedFalse(@Param("hash") String refreshHash);
    Optional<AuthSession> findByIdAndRevokedFalse(String id);
}
