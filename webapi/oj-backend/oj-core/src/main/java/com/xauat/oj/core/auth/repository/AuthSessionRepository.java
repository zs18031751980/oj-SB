package com.xauat.oj.core.auth.repository;

import com.xauat.oj.core.auth.domain.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 认证会话仓储。
 * <p>管理登录会话实体 {@link AuthSession}，主键为字符串类型的会话 ID，支持刷新令牌轮换与撤销。</p>
 */
public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    /**
     * 根据刷新令牌哈希查询未撤销的会话。
     *
     * <p>同时匹配当前刷新令牌哈希 {@code refreshHash} 与上一轮刷新令牌哈希 {@code previousRefreshHash}，
     * 用于刷新后的宽限期兼容；仅返回 {@code revoked = false} 的会话。</p>
     *
     * @param refreshHash 刷新令牌的哈希值
     * @return 匹配的未撤销会话，未找到时返回空 {@code Optional}
     */
    @Query("select s from AuthSession s where s.revoked = false and (s.refreshHash = :hash or s.previousRefreshHash = :hash)")
    Optional<AuthSession> findByRefreshHashAndRevokedFalse(@Param("hash") String refreshHash);

    /**
     * 根据会话 ID 查询未撤销的会话。
     *
     * @param id 会话 ID
     * @return 匹配的未撤销会话，未找到或已撤销时返回空 {@code Optional}
     */
    Optional<AuthSession> findByIdAndRevokedFalse(String id);

    /**
     * 刷新 / 恢复必须串行化，避免并发刷新把同一会话轮换两次。
     *
     * <p>以悲观写锁（{@code PESSIMISTIC_WRITE}）按会话 ID 查询，调用方需在事务中持有锁直至刷新完成。</p>
     *
     * @param id 会话 ID
     * @return 加锁查询到的会话，不存在时返回空 {@code Optional}
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.id = :id")
    Optional<AuthSession> findForUpdateById(@Param("id") String id);

    /**
     * 撤销指定用户的全部会话。
     *
     * @param userId 用户 ID
     * @return 被更新（撤销）的会话条数
     */
    @Modifying
    @Query("update AuthSession s set s.revoked = true where s.user.id = :userId")
    int revokeByUserId(@Param("userId") Integer userId);
}
