package com.xauat.oj.core.auth.repository;

import com.xauat.oj.core.auth.domain.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    @Query("select s from AuthSession s where s.revoked = false and (s.refreshHash = :hash or s.previousRefreshHash = :hash)")
    Optional<AuthSession> findByRefreshHashAndRevokedFalse(@Param("hash") String refreshHash);

    Optional<AuthSession> findByIdAndRevokedFalse(String id);

    /** 刷新 / 恢复必须串行化，避免并发刷新把同一会话轮换两次。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.id = :id")
    Optional<AuthSession> findForUpdateById(@Param("id") String id);

    @Modifying
    @Query("update AuthSession s set s.revoked = true where s.user.id = :userId")
    int revokeByUserId(@Param("userId") Integer userId);
}
