package com.xauat.oj.core.announcement.repository;

import com.xauat.oj.core.announcement.domain.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * 公告仓储。
 * <p>管理公告实体 {@link Announcement} 的持久化与查询，用于站点公告的发布与前台展示。</p>
 */
public interface AnnouncementRepository extends JpaRepository<Announcement, Integer> {
    /**
     * 查询所有已发布（{@code published = true}）的公告。
     *
     * <p>按发布时间 {@code publishedAt} 倒序排列；发布时间相同再按创建时间 {@code createdAt} 倒序，
     * 使最新公告排在最前。未发布的公告不会返回。</p>
     *
     * @return 已发布公告列表，无匹配时返回空列表
     */
    List<Announcement> findByPublishedTrueOrderByPublishedAtDescCreatedAtDesc();
}
