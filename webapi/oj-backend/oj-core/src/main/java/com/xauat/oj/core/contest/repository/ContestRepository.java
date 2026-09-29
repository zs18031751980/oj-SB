package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.Contest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContestRepository extends JpaRepository<Contest, Integer> {
}
