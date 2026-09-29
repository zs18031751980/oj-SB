package com.xauat.oj.core.learning.repository;

import com.xauat.oj.core.learning.domain.LearnBrowsingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LearnBrowsingHistoryRepository extends JpaRepository<LearnBrowsingHistory, Integer> {
    List<LearnBrowsingHistory> findByUserIdOrderByBrowsedAtDesc(Integer userId);
    void deleteByUserId(Integer userId);
}
