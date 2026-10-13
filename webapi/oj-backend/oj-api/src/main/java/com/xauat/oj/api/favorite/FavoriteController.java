package com.xauat.oj.api.favorite;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.core.favorite.domain.Favorite;
import com.xauat.oj.core.favorite.repository.FavoriteRepository;
import com.xauat.oj.core.problem.domain.Problem;
import com.xauat.oj.core.problem.repository.ProblemRepository;
import jakarta.transaction.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * 题目收藏接口（{@code /favorites}）：需登录，按用户维度读写收藏关系。
 */
@RestController
@RequestMapping("/favorites")
public class FavoriteController {
    private final CurrentUser currentUser;
    private final FavoriteRepository favorites;
    private final ProblemRepository problems;
    public FavoriteController(CurrentUser currentUser, FavoriteRepository favorites, ProblemRepository problems) {
        this.currentUser = currentUser;
        this.favorites = favorites;
        this.problems = problems;
    }

    /**
     * 列出当前用户收藏的题目。
     *
     * @param authorization 认证头
     * @return 收藏列表（含题目标题、难度）与总数
     */
    @GetMapping({"", "/"})
    public Map<String, Object> list(@RequestHeader(value = "Authorization", required = false)
    String authorization) {
        List<Map<String, Object>> data = favorites
                .findByUser_IdOrderByIdDesc(currentUser.require(authorization).getId()).stream()
                .map(favorite -> favoriteView(favorite, problems.findById(favorite.getProblemId()).orElse(null)))
                .toList();
        return Map.of("data", data, "total", data.size());
    }
    /** 构造收藏项视图；题目已被删除时标题置空、难度为 null。 */
    private Map<String, Object> favoriteView(Favorite favorite, Problem problem) {
        return Map.of("problem_id", favorite.getProblemId(), "problem_title", problem == null ? "" : problem.getTitle(),
                "difficulty", problem == null ? null : problem.getDifficulty(), "tags", List.of(), "favorited_at",
                favorite.getCreatedAt() == null ? null : favorite.getCreatedAt().toString());
    }

    /**
     * 添加收藏（幂等）。
     *
     * @param authorization 认证头
     * @param problemId     题目 id
     * @return 收藏状态
     */
    @PostMapping("/{problemId}")
    public Map<String, Object> add(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer problemId) {
        var user = currentUser.require(authorization);
        if (!favorites.existsByUser_IdAndProblemId(user.getId(), problemId))
            favorites.save(Favorite.of(user, problemId));
        return Map.of("problem_id", problemId, "favorited", true);
    }

    /**
     * 取消收藏（幂等）。
     *
     * @param authorization 认证头
     * @param problemId     题目 id
     * @return 收藏状态
     */
    @DeleteMapping("/{problemId}")
    @Transactional
    public Map<String, Object> remove(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer problemId) {
        favorites.deleteByUser_IdAndProblemId(currentUser.require(authorization).getId(), problemId);
        return Map.of("problem_id", problemId, "favorited", false);
    }

    /**
     * 查询单题收藏状态。
     *
     * @param authorization 认证头
     * @param problemId     题目 id
     * @return 收藏状态
     */
    @GetMapping("/{problemId}/status")
    public Map<String, Object> status(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer problemId) {
        return Map.of("problem_id", problemId, "favorited",
                favorites.existsByUser_IdAndProblemId(currentUser.require(authorization).getId(), problemId));
    }
}
