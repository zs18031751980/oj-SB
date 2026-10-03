package com.xauat.oj.core.favorite.repository;

import com.xauat.oj.core.favorite.domain.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Integer> {
    List<Favorite> findByUser_IdOrderByIdDesc(Integer userId);
    boolean existsByUser_IdAndProblemId(Integer userId, Integer problemId);
    void deleteByUser_IdAndProblemId(Integer userId, Integer problemId);
}
