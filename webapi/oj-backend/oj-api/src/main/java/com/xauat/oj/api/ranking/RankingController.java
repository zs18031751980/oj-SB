package com.xauat.oj.api.ranking;

import com.xauat.oj.core.ranking.domain.UserJudgeStats;
import com.xauat.oj.core.ranking.repository.UserJudgeStatsRepository;
import com.xauat.oj.core.user.repository.UserRepository;
import org.springframework.web.bind.annotation.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rankings")
public class RankingController {
    private final UserJudgeStatsRepository stats;
    private final UserRepository users;
    public RankingController(UserJudgeStatsRepository stats, UserRepository users) { this.stats = stats; this.users = users; }

    @GetMapping({"", "/"})
    public List<Map<String, Object>> list() {
        return stats.findAll().stream().sorted(Comparator.comparingInt(UserJudgeStats::getRank)).map(this::view).toList();
    }

    @GetMapping("/user/{id}")
    public Map<String, Object> user(@PathVariable Integer id) {
        return stats.findById(id).map(this::view).orElseGet(() -> {
            var u = users.findById(id).orElse(null);
            return Map.of("user_id", id, "rank", 0, "solved_count", 0, "rating", 0,
                    "username", u == null ? "" : (u.getUsername() == null ? "" : u.getUsername()),
                    "avatar_url", u == null ? "" : (u.getAvatarUrl() == null ? "" : u.getAvatarUrl()));
        });
    }

    private Map<String, Object> view(UserJudgeStats item) {
        var u = users.findById(item.getUserId()).orElse(null);
        return Map.of("user_id", item.getUserId(), "rank", item.getRank(), "solved_count", item.getSolvedCount(), "rating", item.getRating(),
                "username", u == null ? "" : (u.getUsername() == null ? "" : u.getUsername()),
                "avatar_url", u == null ? "" : (u.getAvatarUrl() == null ? "" : u.getAvatarUrl()));
    }
}
