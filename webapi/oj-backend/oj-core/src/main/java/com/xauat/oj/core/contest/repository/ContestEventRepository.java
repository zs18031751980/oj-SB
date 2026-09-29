package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.ContestEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ContestEventRepository extends JpaRepository<ContestEvent, Integer> { List<ContestEvent> findByContestIdOrderByIdDesc(Integer contestId); List<ContestEvent> findTop100ByContestIdAndIdGreaterThanOrderByIdAsc(Integer contestId, Integer after); }
