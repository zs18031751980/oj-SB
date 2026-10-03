package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.ContestTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ContestTeamMemberRepository extends JpaRepository<ContestTeamMember, Integer> {
    List<ContestTeamMember> findByContest_IdOrderByIdAsc(Integer contestId);
    java.util.Optional<ContestTeamMember> findByContest_IdAndUser_Id(Integer contestId, Integer userId);
}
