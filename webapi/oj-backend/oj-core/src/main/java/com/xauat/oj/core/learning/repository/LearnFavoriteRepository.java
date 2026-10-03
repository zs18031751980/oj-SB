package com.xauat.oj.core.learning.repository;

import com.xauat.oj.core.learning.domain.LearnFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LearnFavoriteRepository extends JpaRepository<LearnFavorite, Integer> {
    List<LearnFavorite> findByUser_IdOrderByIdDesc(Integer userId);
    boolean existsByUser_IdAndResourceId(Integer userId, String resourceId);
    void deleteByUser_IdAndResourceId(Integer userId, String resourceId);
}
