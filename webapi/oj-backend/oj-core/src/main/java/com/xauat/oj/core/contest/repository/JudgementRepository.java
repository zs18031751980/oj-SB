package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.Judgement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface JudgementRepository extends JpaRepository<Judgement,Integer> { List<Judgement> findBySubmissionIdOrderByIdDesc(Integer submissionId); }
