package com.xauat.oj.api.ranking;

import com.xauat.oj.core.ranking.domain.UserJudgeStats;
import com.xauat.oj.core.ranking.repository.UserJudgeStatsRepository;
import org.springframework.web.bind.annotation.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rankings")
public class RankingController {
    private final UserJudgeStatsRepository stats;
    public RankingController(UserJudgeStatsRepository stats) { this.stats = stats; }

    @GetMapping({"", "/"})
    public List<Map<String, Object>> list() {
        return stats.findAll().stream().sorted(Comparator.comparingInt(UserJudgeStats::getRank)).map(this::view).toList();
    }

    @GetMapping("/user/{id}")
    public Map<String, Object> user(@PathVariable Integer id) {
        return stats.findById(id).map(this::view).orElse(Map.of("user_id", id, "rank", 0, "solved_count", 0, "rating", 0));
    }

    private Map<String, Object> view(UserJudgeStats item) { return Map.of("user_id", item.getUserId(), "rank", item.getRank(), "solved_count", item.getSolvedCount(), "rating", item.getRating()); }
}
