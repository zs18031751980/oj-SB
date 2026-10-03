package com.xauat.oj.core.usercode.repository;

import com.xauat.oj.core.usercode.domain.UserCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserCodeRepository extends JpaRepository<UserCode, Integer> {
    Optional<UserCode> findByUser_IdAndProblemIdAndLanguage(Integer userId, Integer problemId, String language);
    List<UserCode> findByUser_IdOrderByUpdatedAtDescIdDesc(Integer userId);
    Optional<UserCode> findFirstByUser_IdOrderByUpdatedAtAscIdAsc(Integer userId);

    @Query("select count(distinct c.problemId) from UserCode c where c.user.id = :userId")
    long countDistinctProblems(@Param("userId") Integer userId);
}
