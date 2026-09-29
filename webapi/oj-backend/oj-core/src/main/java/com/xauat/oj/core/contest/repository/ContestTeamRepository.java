package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.ContestTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ContestTeamRepository extends JpaRepository<ContestTeam,Integer> { List<ContestTeam> findByContestIdOrderByIdAsc(Integer contestId); }
