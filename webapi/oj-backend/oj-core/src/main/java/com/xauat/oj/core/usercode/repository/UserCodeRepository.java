package com.xauat.oj.core.usercode.repository;

import com.xauat.oj.core.usercode.domain.UserCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserCodeRepository extends JpaRepository<UserCode, Integer> {
    Optional<UserCode> findByUserIdAndProblemIdAndLanguage(Integer userId, Integer problemId, String language);
}
