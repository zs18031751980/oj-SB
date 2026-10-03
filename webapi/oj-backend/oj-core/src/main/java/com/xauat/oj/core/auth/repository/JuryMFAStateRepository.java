package com.xauat.oj.core.auth.repository;

import com.xauat.oj.core.auth.domain.JuryMFAState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JuryMFAStateRepository extends JpaRepository<JuryMFAState, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from JuryMFAState s where s.userId = :userId")
    Optional<JuryMFAState> findForUpdateByUserId(@Param("userId") Integer userId);
}
