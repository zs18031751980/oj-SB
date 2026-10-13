package com.xauat.oj.core.usercode.repository;

import com.xauat.oj.core.usercode.domain.UserCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 用户代码仓储。
 * <p>管理用户在题目下保存的代码草稿实体 {@link UserCode}，支持按题目/语言检索与统计。</p>
 */
public interface UserCodeRepository extends JpaRepository<UserCode, Integer> {
    /**
     * 查询某用户在指定题目、指定语言下保存的代码。
     *
     * @param userId 用户 ID
     * @param problemId 题目 ID
     * @param language 编程语言
     * @return 匹配的代码记录，未找到时返回空 {@code Optional}
     */
    Optional<UserCode> findByUser_IdAndProblemIdAndLanguage(Integer userId, Integer problemId, String language);

    /**
     * 查询某用户的全部代码，按更新时间倒序、主键倒序排列。
     *
     * @param userId 用户 ID
     * @return 代码列表，最近更新在前，无数据时返回空列表
     */
    List<UserCode> findByUser_IdOrderByUpdatedAtDescIdDesc(Integer userId);

    /**
     * 查询某用户最早更新的第一条代码，按更新时间正序、主键正序。
     *
     * @param userId 用户 ID
     * @return 最早更新的代码记录，不存在时返回空 {@code Optional}
     */
    Optional<UserCode> findFirstByUser_IdOrderByUpdatedAtAscIdAsc(Integer userId);

    /**
     * 统计某用户保存过代码的不同题目数量。
     *
     * <p>使用 {@code count(distinct c.problemId)} 去重，同一题目多语言只计一次。</p>
     *
     * @param userId 用户 ID
     * @return 不同题目数量
     */
    @Query("select count(distinct c.problemId) from UserCode c where c.user.id = :userId")
    long countDistinctProblems(@Param("userId")
    Integer userId);
}
