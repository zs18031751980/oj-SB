package com.xauat.oj.api.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.core.contest.domain.Contest;
import com.xauat.oj.core.contest.domain.ContestProblem;
import com.xauat.oj.core.contest.repository.ContestProblemRepository;
import com.xauat.oj.core.contest.repository.ContestRepository;
import com.xauat.oj.core.contest.repository.ContestTestcaseRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 题库目录：静态题目 + 已结束比赛的题目库。比赛题目使用 {@link StaticProblemCatalog#LIBRARY_ID_BASE}
 * 编码主键，避免与静态题号冲突，并保持旧后端的字段命名。
 */
@Service
public class ProblemCatalogService {
    private final StaticProblemCatalog staticCatalog;
    private final ContestRepository contests;
    private final ContestProblemRepository contestProblems;
    private final ContestTestcaseRepository contestTestcases;
    private final ObjectMapper mapper;

    public ProblemCatalogService(StaticProblemCatalog staticCatalog, ContestRepository contests,
                                 ContestProblemRepository contestProblems, ContestTestcaseRepository contestTestcases,
                                 ObjectMapper mapper) {
        this.staticCatalog = staticCatalog;
        this.contests = contests;
        this.contestProblems = contestProblems;
        this.contestTestcases = contestTestcases;
        this.mapper = mapper;
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        staticCatalog.all().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.add(staticSummary(entry.getValue())));
        for (Contest contest : endedContests()) {
            for (ContestProblem problem : contestProblems.findByContest_IdOrderBySortOrderAscIdAsc(contest.getId())) {
                result.add(librarySummary(contest, problem));
            }
        }
        return result;
    }

    public Optional<Map<String, Object>> detail(Integer id) {
        Map<String, Object> staticProblem = staticCatalog.find(id).orElse(null);
        if (staticProblem != null) return Optional.of(staticDetail(staticProblem));
        if (id != null && id >= StaticProblemCatalog.LIBRARY_ID_BASE) {
            int contestProblemId = id - StaticProblemCatalog.LIBRARY_ID_BASE;
            return contestProblems.findById(contestProblemId)
                    .map(problem -> contests.findById(problem.getContestId()).orElse(null))
                    .filter(contest -> contest != null && isEnded(contest))
                    .map(contest -> libraryDetail(contest, contestProblems.findById(contestProblemId).orElseThrow()));
        }
        return Optional.empty();
    }

    /** 供题库提交使用：校验题目存在且所属比赛已结束。 */
    public Optional<ContestProblem> findLibraryProblem(Integer contestProblemId) {
        if (contestProblemId == null) return Optional.empty();
        return contestProblems.findById(contestProblemId)
                .filter(problem -> contests.findById(problem.getContestId()).filter(this::isEnded).isPresent());
    }

    public boolean isEnded(Contest contest) {
        if (contest == null || !contest.isPublicContest()) return false;
        String lifecycle = contest.getLifecycleState();
        if (lifecycle != null && List.of("DRAFT", "READY", "CANCELLED").contains(lifecycle)) return false;
        if ("FINALIZED".equals(lifecycle)) return true;
        LocalDateTime end = contest.getEndTime();
        return end != null && !end.isAfter(LocalDateTime.now());
    }

    public List<Contest> endedContests() {
        return contests.findAll().stream().filter(this::isEnded)
                .sorted(Comparator.comparing(Contest::getId)).toList();
    }

    private Map<String, Object> staticSummary(Map<String, Object> raw) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", raw.get("id"));
        summary.put("sourceNumber", raw.get("sourceNumber"));
        summary.put("category", fallback(raw.get("category"), "general"));
        summary.put("categoryLabel", fallback(raw.get("categoryLabel"), "通用题库"));
        summary.put("title", raw.get("title"));
        summary.put("difficulty", raw.get("difficulty"));
        summary.put("tags", raw.getOrDefault("tags", List.of()));
        summary.put("interactive", raw.getOrDefault("interactive", false));
        summary.put("judgeable", raw.getOrDefault("judgeable", true));
        summary.put("timeLimit", raw.get("timeLimit"));
        summary.put("memoryLimit", raw.get("memoryLimit"));
        return summary;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> staticDetail(Map<String, Object> raw) {
        Map<String, Object> detail = new LinkedHashMap<>(raw);
        Object testCases = detail.remove("testCases");
        detail.put("testCaseCount", testCases instanceof List<?> list ? list.size() : 0);
        detail.putIfAbsent("category", "general");
        detail.putIfAbsent("categoryLabel", "通用题库");
        detail.putIfAbsent("interactive", false);
        detail.putIfAbsent("judgeable", true);
        return detail;
    }

    private Map<String, Object> librarySummary(Contest contest, ContestProblem problem) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", StaticProblemCatalog.LIBRARY_ID_BASE + problem.getId());
        summary.put("sourceNumber", problem.getId());
        summary.put("category", "contest-" + contest.getId());
        summary.put("categoryLabel", contest.getTitle());
        summary.put("title", problem.getTitle());
        summary.put("difficulty", problem.getDifficulty());
        summary.put("tags", List.of());
        summary.put("interactive", false);
        summary.put("judgeable", true);
        summary.put("timeLimit", problem.getTimeLimit());
        summary.put("memoryLimit", problem.getMemoryLimit());
        return summary;
    }

    private Map<String, Object> libraryDetail(Contest contest, ContestProblem problem) {
        Map<String, Object> detail = librarySummary(contest, problem);
        detail.put("description", problem.getDescription());
        detail.put("inputFormat", problem.getInputDesc());
        detail.put("outputFormat", problem.getOutputDesc());
        detail.put("samples", parseSamples(problem.getSamples()));
        detail.put("testCaseCount", contestTestcases.findByContestProblem_IdOrderBySortOrderAscIdAsc(problem.getId()).size());
        detail.put("isLibrary", true);
        detail.put("contestProblemId", problem.getId());
        detail.put("contestId", contest.getId());
        detail.put("contestTitle", contest.getTitle());
        return detail;
    }

    private List<Map<String, Object>> parseSamples(String raw) {
        try {
            if (raw == null || raw.isBlank()) return List.of();
            return mapper.readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Object fallback(Object value, Object defaultValue) {
        return value == null ? defaultValue : value;
    }
}
