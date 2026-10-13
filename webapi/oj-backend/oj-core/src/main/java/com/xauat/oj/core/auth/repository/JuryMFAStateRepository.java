package com.xauat.oj.core.auth.repository;

import com.xauat.oj.core.auth.domain.JuryMFAState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 评委会（Jury）多因素认证（MFA）状态仓储。
 * <p>管理评委用户的 MFA 状态实体 {@link JuryMFAState} 的持久化与加锁查询。</p>
 */
public interface JuryMFAStateRepository extends JpaRepository<JuryMFAState, Integer> {
    /**
     * 以悲观写锁（{@code PESSIMISTIC_WRITE}）按用户 ID 查询 MFA 状态。
     *
     * <p>加锁用于串行化同一用户的 MFA 校验/更新操作，避免并发修改导致状态不一致；
     * 调用方需在事务中持有该锁直至更新完成。</p>
     *
     * @param userId 用户 ID
     * @return 该用户的 MFA 状态，不存在时返回空 {@code Optional}
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from JuryMFAState s where s.userId = :userId")
    Optional<JuryMFAState> findForUpdateByUserId(@Param("userId") Integer userId);
}
