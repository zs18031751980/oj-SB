package com.xauat.oj.api.usercode;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.core.usercode.domain.UserCode;
import com.xauat.oj.core.usercode.repository.UserCodeRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户代码草稿接口（{@code /user/code}）：保存/读取每个用户最近编辑的题目代码。
 *
 * <p>为控制存储量，每个用户最多保留 {@link #MAX_PROBLEMS_PER_USER} 道题；
 * 超出时淘汰更新时间最早的一条。</p>
 */
@RestController
@RequestMapping("/user")
public class UserCodeController {
    /** 每个用户最多保留的题目代码数。 */
    private static final int MAX_PROBLEMS_PER_USER = 5;

    private final CurrentUser currentUser;
    private final UserCodeRepository codes;
    public UserCodeController(CurrentUser currentUser, UserCodeRepository codes) {
        this.currentUser = currentUser;
        this.codes = codes;
    }

    /**
     * 保存代码草稿（同一用户+题目+语言覆盖更新）。
     *
     * <p>超出题目数上限时，先淘汰更新最旧的一条再插入新记录。</p>
     *
     * @param authorization 认证头
     * @param request       题目 id、语言与代码
     * @return 保存结果
     */
    @PutMapping("/code")
    @Transactional
    public Map<String, Object> save(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @Valid @RequestBody
            CodeRequest request) {
        var user = currentUser.require(authorization);
        String language = request.language() == null || request.language().isBlank() ? "cpp" : request.language();
        var existing = codes.findByUser_IdAndProblemIdAndLanguage(user.getId(), request.problemId(), language);
        if (existing.isPresent()) {
            // 已存在则直接覆盖代码内容。
            var code = existing.get();
            code.setCode(request.code());
            codes.save(code);
        } else {
            // 达到题目数上限时淘汰最旧记录，保证新草稿可写入。
            if (codes.countDistinctProblems(user.getId()) >= MAX_PROBLEMS_PER_USER) {
                codes.findFirstByUser_IdOrderByUpdatedAtAscIdAsc(user.getId())
                        .ifPresent(oldest -> codes.deleteById(oldest.getId()));
            }
            codes.save(UserCode.of(user, request.problemId(), language, request.code()));
        }
        return Map.of("message", "保存成功");
    }

    /**
     * 列出当前用户的代码草稿（最多 {@link #MAX_PROBLEMS_PER_USER} 条）。
     *
     * @param authorization 认证头
     * @return 代码草稿列表
     */
    @GetMapping("/code")
    public Map<String, Object> list(@RequestHeader(value = "Authorization", required = false)
    String authorization) {
        var user = currentUser.require(authorization);
        List<Map<String, Object>> data = codes.findByUser_IdOrderByUpdatedAtDescIdDesc(user.getId()).stream()
                .limit(MAX_PROBLEMS_PER_USER)
                .map(item -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("problem_id", item.getProblemId());
                    body.put("language", item.getLanguage());
                    body.put("code", item.getCode());
                    body.put("updated_at", item.getUpdatedAt() == null ? null : item.getUpdatedAt().toString());
                    return body;
                }).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        return body;
    }

    /**
     * 读取某题某语言的代码草稿。
     *
     * <p>不存在时也返回 200，仅有 {@code code:null}，便于前端直接渲染编辑器。</p>
     *
     * @param authorization 认证头
     * @param problemId     题目 id
     * @param language      编程语言（默认 cpp）
     * @return 代码草稿或空
     */
    @GetMapping("/code/{problemId}")
    public ResponseEntity<?> get(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer problemId,
            @RequestParam(defaultValue = "cpp")
            String language) {
        var user = currentUser.require(authorization);
        return codes.findByUser_IdAndProblemIdAndLanguage(user.getId(), problemId, language)
                .<ResponseEntity<?>>map(item -> ResponseEntity.ok(Map.of("problem_id", item.getProblemId(), "language",
                        item.getLanguage(), "code", item.getCode())))
                .orElseGet(() -> ResponseEntity.ok(java.util.Collections.singletonMap("code", null)));
    }

    /** 代码草稿请求体，兼容 {@code problem_id} / {@code problemId} 字段名。 */
    public record CodeRequest(@JsonProperty("problem_id") @JsonAlias("problemId") @NotNull
    Integer problemId,
            @NotBlank
            String language, @NotBlank
            String code) {
    }
}
