package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.ContestScoreboardSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
/**
 * 赛事记分板快照仓储。
 * <p>管理赛事记分板快照实体 {@link ContestScoreboardSnapshot} 的持久化与按类型查询。</p>
 */
public interface ContestScoreboardSnapshotRepository extends JpaRepository<ContestScoreboardSnapshot, Integer> {
    /**
     * 查询某赛事指定类型的记分板快照。
     *
     * @param contestId 赛事 ID
     * @param snapshotKind 快照类型
     * @return 匹配的快照，未找到时返回空 {@code Optional}
     */
    Optional<ContestScoreboardSnapshot> findByContest_IdAndSnapshotKind(Integer contestId, String snapshotKind);
}
