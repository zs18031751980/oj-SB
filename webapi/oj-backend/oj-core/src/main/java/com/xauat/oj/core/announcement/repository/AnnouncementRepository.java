package com.xauat.oj.core.announcement.repository;

import com.xauat.oj.core.announcement.domain.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Integer> {
    List<Announcement> findByPublishedTrueOrderByPublishedAtDescCreatedAtDesc();
}
