package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ContestParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 赛事参赛者仓储。
 * <p>管理赛事报名/参赛记录实体 {@link ContestParticipant} 的持久化与查询。</p>
 */
public interface ContestParticipantRepository extends JpaRepository<ContestParticipant, Integer> {
    /**
     * 查询某赛事中指定用户的参赛记录。
     *
     * @param contestId 赛事 ID
     * @param userId 用户 ID
     * @return 匹配的参赛记录，未找到时返回空 {@code Optional}
     */
    Optional<ContestParticipant> findByContest_IdAndUser_Id(Integer contestId, Integer userId);

    /**
     * 统计某赛事的参赛人数。
     *
     * @param contestId 赛事 ID
     * @return 参赛记录条数
     */
    long countByContest_Id(Integer contestId);

    /**
     * 删除指定赛事下的全部参赛记录。
     *
     * @param contestId 赛事 ID
     * @return 被删除的记录条数
     */
    @Modifying
    @Query("delete from ContestParticipant p where p.contest.id = :contestId")
    int deleteByContestId(@Param("contestId")
    Integer contestId);
}
