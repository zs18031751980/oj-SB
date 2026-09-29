package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.ContestRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ContestRoleRepository extends JpaRepository<ContestRole,Integer> { List<ContestRole> findByContestIdOrderByIdAsc(Integer contestId); }
