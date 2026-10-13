package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.ContestEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
/**
 * 赛事事件仓储。
 * <p>管理赛事事件日志实体 {@link ContestEvent}，用于事件回放与增量（游标）拉取。</p>
 */
public interface ContestEventRepository extends JpaRepository<ContestEvent, Integer> {
    /**
     * 查询某赛事的全部事件，按主键倒序（最新事件在前）。
     *
     * @param contestId 赛事 ID
     * @return 事件列表，无数据时返回空列表
     */
    List<ContestEvent> findByContest_IdOrderByIdDesc(Integer contestId);

    /**
     * 增量查询某赛事中主键大于指定游标的最多 100 条事件，按主键正序。
     *
     * <p>用于前端轮询/长连接按游标 {@code after} 拉取新事件。</p>
     *
     * @param contestId 赛事 ID
     * @param after 游标，仅返回主键大于该值的事件
     * @return 最多 100 条事件列表，无新事件时返回空列表
     */
    List<ContestEvent> findTop100ByContest_IdAndIdGreaterThanOrderByIdAsc(Integer contestId, Integer after);
}
