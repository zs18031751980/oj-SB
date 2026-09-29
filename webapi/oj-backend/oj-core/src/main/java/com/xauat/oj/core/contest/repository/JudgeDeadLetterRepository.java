package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.JudgeDeadLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JudgeDeadLetterRepository extends JpaRepository<JudgeDeadLetter, Integer> {
    List<JudgeDeadLetter> findTop100ByOrderByIdDesc();
}
