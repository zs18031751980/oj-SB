package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.ContestTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ContestTeamMemberRepository extends JpaRepository<ContestTeamMember,Integer> { List<ContestTeamMember> findByContestIdOrderByIdAsc(Integer contestId); }
