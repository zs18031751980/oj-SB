package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.ContestAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ContestAuditRepository extends JpaRepository<ContestAudit, Integer> {
    List<ContestAudit> findByContest_IdOrderByIdDesc(Integer contestId);
    List<ContestAudit> findTop1000ByIdGreaterThanOrderByIdAsc(Integer id);
}
