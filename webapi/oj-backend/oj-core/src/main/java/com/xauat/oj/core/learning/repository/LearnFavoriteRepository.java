package com.xauat.oj.core.learning.repository;

import com.xauat.oj.core.learning.domain.LearnFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LearnFavoriteRepository extends JpaRepository<LearnFavorite, Integer> {
    List<LearnFavorite> findByUserIdOrderByIdDesc(Integer userId);
    boolean existsByUserIdAndResourceId(Integer userId, String resourceId);
    void deleteByUserIdAndResourceId(Integer userId, String resourceId);
}
