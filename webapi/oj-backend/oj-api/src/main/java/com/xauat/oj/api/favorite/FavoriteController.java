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

@RestController
@RequestMapping("/favorites")
public class FavoriteController {
    private final CurrentUser currentUser;
    private final FavoriteRepository favorites;
    private final ProblemRepository problems;
    public FavoriteController(CurrentUser currentUser, FavoriteRepository favorites, ProblemRepository problems) { this.currentUser = currentUser; this.favorites = favorites; this.problems = problems; }

    @GetMapping({"", "/"})
    public Map<String, Object> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        List<Map<String, Object>> data = favorites.findByUserIdOrderByIdDesc(currentUser.require(authorization).getId()).stream()
                .map(favorite -> favoriteView(favorite, problems.findById(favorite.getProblemId()).orElse(null))).toList();
        return Map.of("data", data, "total", data.size());
    }
    private Map<String, Object> favoriteView(Favorite favorite, Problem problem) { return Map.of("problem_id", favorite.getProblemId(), "problem_title", problem == null ? "" : problem.getTitle(), "difficulty", problem == null ? null : problem.getDifficulty(), "tags", List.of(), "favorited_at", favorite.getCreatedAt() == null ? null : favorite.getCreatedAt().toString()); }

    @PostMapping("/{problemId}")
    public Map<String, Object> add(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer problemId) {
        var user = currentUser.require(authorization);
        if (!favorites.existsByUserIdAndProblemId(user.getId(), problemId)) favorites.save(Favorite.of(user, problemId));
        return Map.of("problem_id", problemId, "favorited", true);
    }

    @DeleteMapping("/{problemId}")
    @Transactional
    public Map<String, Object> remove(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer problemId) {
        favorites.deleteByUserIdAndProblemId(currentUser.require(authorization).getId(), problemId);
        return Map.of("problem_id", problemId, "favorited", false);
    }

    @GetMapping("/{problemId}/status")
    public Map<String, Object> status(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer problemId) {
        return Map.of("problem_id", problemId, "favorited", favorites.existsByUserIdAndProblemId(currentUser.require(authorization).getId(), problemId));
    }
}
