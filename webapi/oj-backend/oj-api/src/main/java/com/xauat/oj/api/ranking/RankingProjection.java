package com.xauat.oj.api.ranking;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 普通排行榜投影：周期性把 submissions 的 AC 记录聚合到 user_judge_stats，
 * API 读取时只做索引分页，避免实时全表扫描。全量替换保证读方始终看到完整版本。
 */
@Component
public class RankingProjection {
    private final JdbcTemplate jdbc;

    public RankingProjection(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Scheduled(fixedDelayString = "${oj.ranking.refresh-ms:15000}", initialDelayString = "${oj.ranking.initial-delay-ms:3000}")
    public void refresh() { refreshIfStale(10); }

    @Transactional
    public void refreshIfStale(int minAgeSeconds) {
        LocalDateTime builtAt = jdbc.query("select built_at from ranking_projection_state where id = 1",
                rs -> rs.next() ? rs.getTimestamp("built_at") == null ? null : rs.getTimestamp("built_at").toLocalDateTime() : null);
        if (builtAt != null && builtAt.isAfter(LocalDateTime.now().minusSeconds(minAgeSeconds))) return;
        jdbc.update("insert into ranking_projection_state (id, built_at) values (1, now()) "
                + "on conflict (id) do nothing");
        jdbc.update("delete from user_judge_stats");
        jdbc.update("""
                insert into user_judge_stats
                    (user_id, rank, solved_count, rating, easy_count, medium_count, hard_count, created_at, updated_at)
                with solved as (
                    select distinct s.user_id, s.problem_id, coalesce(p.difficulty, '简单') as difficulty
                    from submissions s join problems p on p.id = s.problem_id where s.status = 'AC'
                ), stats as (
                    select user_id, count(*) as solved_count,
                        sum(case difficulty when '困难' then 30 when '中等' then 20 else 10 end) as rating,
                        sum(case when difficulty = '简单' then 1 else 0 end) as easy_count,
                        sum(case when difficulty = '中等' then 1 else 0 end) as medium_count,
                        sum(case when difficulty = '困难' then 1 else 0 end) as hard_count
                    from solved group by user_id
                )
                select user_id, row_number() over (order by rating desc, user_id), solved_count, rating,
                    easy_count, medium_count, hard_count, current_timestamp, current_timestamp from stats
                """);
        jdbc.update("update ranking_projection_state set built_at = now(), updated_at = now() where id = 1");
    }
}
