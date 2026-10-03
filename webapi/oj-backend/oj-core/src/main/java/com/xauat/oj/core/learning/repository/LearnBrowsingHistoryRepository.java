package com.xauat.oj.core.learning.repository;

import com.xauat.oj.core.learning.domain.LearnBrowsingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LearnBrowsingHistoryRepository extends JpaRepository<LearnBrowsingHistory, Integer> {
    List<LearnBrowsingHistory> findByUser_IdOrderByBrowsedAtDesc(Integer userId);
    void deleteByUser_Id(Integer userId);

    @Modifying
    @Query("delete from LearnBrowsingHistory h where h.browsedAt < :cutoff")
    int deleteBefore(@Param("cutoff") LocalDateTime cutoff);
}
