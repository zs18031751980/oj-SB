package com.xauat.oj.core.discussion.repository;

import com.xauat.oj.core.discussion.domain.Discussion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiscussionRepository extends JpaRepository<Discussion, Integer> {
    List<Discussion> findAllByOrderByCreatedAtDescIdDesc();
    List<Discussion> findByCategoryOrderByCreatedAtDescIdDesc(String category);
}
