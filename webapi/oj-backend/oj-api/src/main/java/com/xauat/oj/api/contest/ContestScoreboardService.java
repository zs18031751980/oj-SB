package com.xauat.oj.api.contest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.core.contest.domain.Contest;
import com.xauat.oj.core.contest.domain.ContestScoreboardSnapshot;
import com.xauat.oj.core.contest.domain.ContestSubmission;
import com.xauat.oj.core.contest.repository.ContestParticipantRepository;
import com.xauat.oj.core.contest.repository.ContestProblemRepository;
import com.xauat.oj.core.contest.repository.ContestScoreboardSnapshotRepository;
import com.xauat.oj.core.contest.repository.ContestSubmissionRepository;
import com.xauat.oj.core.user.domain.User;
import com.xauat.oj.core.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 比赛榜单计算与快照持久化。API 只读快照；后台投影负责在版本变化时重建。
 */
@Service
public class ContestScoreboardService {
    public static final String LIVE = "LIVE";
    public static final String PUBLIC_FREEZE = "PUBLIC_FREEZE";
    public static final String FINAL = "FINAL";

    private final ContestProblemRepository problems;
    private final ContestSubmissionRepository submissions;
    private final ContestParticipantRepository participants;
    private final ContestScoreboardSnapshotRepository snapshots;
    private final UserRepository users;
    private final ObjectMapper mapper;
    private final EntryResolver entryResolver;

    public ContestScoreboardService(ContestProblemRepository problems, ContestSubmissionRepository submissions,
                                    ContestParticipantRepository participants, ContestScoreboardSnapshotRepository snapshots,
                                    UserRepository users, ObjectMapper mapper, EntryResolver entryResolver) {
        this.problems = problems; this.submissions = submissions; this.participants = participants;
        this.snapshots = snapshots; this.users = users; this.mapper = mapper; this.entryResolver = entryResolver;
    }

    public String kindFor(Contest contest, boolean jury) {
        if (FINAL.equals(contest.getLifecycleState()) || "FINALIZED".equals(contest.getLifecycleState())) {
            return contest.getFinalRevision() > 0 ? FINAL + ":" + contest.getFinalRevision() : FINAL;
        }
        return contest.getFreezeTime() != null && LocalDateTime.now().isAfter(contest.getFreezeTime()) && !jury ? PUBLIC_FREEZE : LIVE;
    }

    public boolean isFrozen(Contest contest) {
        if (contest.getFreezeTime() == null || contest.getThawedAt() != null) return false;
        if (List.of("DRAFT", "READY", "CANCELLED").contains(contest.getLifecycleState())) return false;
        return !LocalDateTime.now().isBefore(contest.getFreezeTime());
    }

    public Map<String, Object> buildBody(Contest contest, String kind) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", "ACM".equalsIgnoreCase(contest.getContestType()) ? "acm" : "oi");
        body.put("contest_type", contest.getContestType() == null ? "" : contest.getContestType());
        body.put("problem_indexes", problems.findByContest_IdOrderBySortOrderAscIdAsc(contest.getId()).stream()
                .map(com.xauat.oj.core.contest.domain.ContestProblem::getProblemIndex).toList());
        body.put("rankings", scoreboardRows(contest, kind));
        return body;
    }

    /**
     * 计算并更新快照。快照版本记录构建时的 {@code scoreboard_requested_version}，
     * 供投影判断是否需要重建；FINAL 快照一旦生成即不可变。
     */
    public ContestScoreboardSnapshot ensure(Contest contest, String kind) {
        try {
            var existing = snapshots.findByContest_IdAndSnapshotKind(contest.getId(), kind);
            if (existing.isPresent() && kind.startsWith(FINAL)) return existing.get();
            String payload = mapper.writeValueAsString(buildBody(contest, kind));
            int version = contest.getScoreboardRequestedVersion();
            ContestScoreboardSnapshot snapshot = existing.orElseGet(() -> ContestScoreboardSnapshot.create(contest, kind, payload, version));
            if (!payload.equals(snapshot.getPayload()) || snapshot.getScoreboardVersion() != version) {
                snapshot.replace(payload, version);
            }
            return snapshots.save(snapshot);
        } catch (Exception exception) {
            throw new IllegalStateException("排行榜生成失败", exception);
        }
    }

    public Map<String, Object> bodyOf(ContestScoreboardSnapshot snapshot) {
        try { return mapper.readValue(snapshot.getPayload(), new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (Exception exception) { return Map.of(); }
    }

    private List<Map<String, Object>> scoreboardRows(Contest contest, String kind) {
        var source = submissions.findByContest_IdOrderByIdDesc(contest.getId()).stream()
                .sorted(Comparator.comparing(ContestSubmission::getId))
                .filter(s -> s.getContestId().equals(contest.getId()) && s.isContestEligible() && s.getRejudgeOfId() == null
                        && s.getStatus() != null
                        && !"Pending".equals(s.getStatus()) && !"Judging".equals(s.getStatus())
                        && (LIVE.equals(kind) || s.getSubmittedAt() == null || contest.getFreezeTime() == null
                        || s.getSubmittedAt().isBefore(contest.getFreezeTime())))
                .toList();
        Map<Integer, LinkedHashMap<String, ProblemScore>> perUser = new HashMap<>();
        participants.findAll().stream().filter(p -> contest.getId().equals(p.getContestId()))
                .forEach(p -> perUser.putIfAbsent(entryResolver.entryUser(contest.getId(), p.getUserId()), new LinkedHashMap<>()));
        for (var submission : source) {
            Integer entry = entryResolver.entryUser(contest.getId(), submission.getUserId());
            var byProblem = perUser.computeIfAbsent(entry, ignored -> new LinkedHashMap<>());
            byProblem.computeIfAbsent(submission.getProblemIndex(), ProblemScore::new)
                    .accept(submission, contest.getStartTime(), "ACM".equalsIgnoreCase(contest.getContestType()), contest.getPenaltyTime());
        }
        var rows = perUser.entrySet().stream().map(entry -> {
            int solved = entry.getValue().values().stream().mapToInt(ProblemScore::solved).sum();
            int score = "OI".equalsIgnoreCase(contest.getContestType())
                    ? entry.getValue().values().stream().mapToInt(ProblemScore::oiScore).sum() : solved;
            int penalty = entry.getValue().values().stream().mapToInt(ProblemScore::penalty).sum();
            List<Map<String, Object>> problems = entry.getValue().values().stream().map(ProblemScore::asMap).toList();
            return new Row(entry.getKey(), score, solved, penalty, problems);
        }).sorted(Comparator.comparingInt(Row::score).reversed().thenComparingInt(Row::penalty).thenComparingInt(Row::userId)).toList();
        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 0; int previousScore = Integer.MIN_VALUE;
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            if (row.score() != previousScore) rank = i + 1;
            previousScore = row.score();
            User user = users.findById(row.userId()).orElse(null);
            result.add(Map.of("rank", rank, "user_id", row.userId(),
                    "username", user == null ? "" : (user.getUsername() == null ? "" : user.getUsername()),
                    "avatar_url", user == null ? "" : (user.getAvatarUrl() == null ? "" : user.getAvatarUrl()),
                    "solved_count", row.solved(), "penalty", row.penalty(), "score", row.score(), "problems", row.problems()));
        }
        return result;
    }

    private static final class ProblemScore {
        private final String index; private boolean solved; private int submissions; private int penalty;
        private int best; private int passed; private int total; private String status; private Integer solveMinutes;
        ProblemScore(String index) { this.index = index; }
        void accept(ContestSubmission s, LocalDateTime start, boolean acm, int penaltyMinutes) {
            submissions++;
            int pass = s.getTotal() == 0 ? 0 : Math.max(0, s.getPassed());
            int tot = Math.max(0, s.getTotal());
            passed = Math.max(passed, pass); total = Math.max(total, tot);
            status = (s.getVerdict() == null || s.getVerdict().isBlank()) ? s.getStatus() : s.getVerdict();
            int percent = tot == 0 ? 0 : (s.getScore() > 0 ? s.getScore() : pass * 100 / tot);
            if (solved) return;
            if ("AC".equalsIgnoreCase(s.getVerdict()) || "Accepted".equalsIgnoreCase(s.getVerdict()) || "AC".equalsIgnoreCase(s.getStatus())) {
                solved = true;
                int minutes = start == null || s.getSubmittedAt() == null ? 0 : (int) java.time.Duration.between(start, s.getSubmittedAt()).toMinutes();
                solveMinutes = minutes;
                penalty = acm ? minutes + (submissions - 1) * penaltyMinutes : 0;
                best = Math.max(best, percent);
            } else {
                best = Math.max(best, percent);
            }
        }
        int solved() { return solved ? 1 : 0; }
        int penalty() { return penalty; }
        int oiScore() { return best; }
        Map<String, Object> asMap() {
            var m = new HashMap<String, Object>();
            m.put("problem_index", index); m.put("solved", solved); m.put("passed", passed); m.put("total", total);
            m.put("score", best); m.put("status", status == null ? "" : status); m.put("submissions", submissions);
            m.put("solve_minutes", solveMinutes);
            return m;
        }
    }

    private record Row(Integer userId, int score, int solved, int penalty, List<Map<String, Object>> problems) {}
}
