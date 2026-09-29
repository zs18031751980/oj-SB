package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.RejudgeBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RejudgeBatchRepository extends JpaRepository<RejudgeBatch, Integer> { List<RejudgeBatch> findByContestIdOrderByIdDesc(Integer contestId); long countByContestIdAndState(Integer contestId, String state); }
