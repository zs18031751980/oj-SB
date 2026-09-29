package com.xauat.oj.core.favorite.repository;

import com.xauat.oj.core.favorite.domain.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Integer> {
    List<Favorite> findByUserIdOrderByIdDesc(Integer userId);
    boolean existsByUserIdAndProblemId(Integer userId, Integer problemId);
    void deleteByUserIdAndProblemId(Integer userId, Integer problemId);
}
