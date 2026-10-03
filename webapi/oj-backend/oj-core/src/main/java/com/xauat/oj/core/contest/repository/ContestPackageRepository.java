package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ContestPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ContestPackageRepository extends JpaRepository<ContestPackage, String> {
    List<ContestPackage> findTop50ByValidationStateInOrderByUpdatedAtAsc(Collection<String> states);
    List<ContestPackage> findByProblem_Id(Integer problemId);
}
