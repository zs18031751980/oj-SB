package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.ContestClarification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ContestClarificationRepository extends JpaRepository<ContestClarification, Integer> { List<ContestClarification> findByContestIdOrderByIdDesc(Integer contestId); }
