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

@RestController
@RequestMapping("/user")
public class UserCodeController {
    private static final int MAX_PROBLEMS_PER_USER = 5;

    private final CurrentUser currentUser; private final UserCodeRepository codes;
    public UserCodeController(CurrentUser currentUser, UserCodeRepository codes) { this.currentUser = currentUser; this.codes = codes; }

    @PutMapping("/code")
    @Transactional
    public Map<String, Object> save(@RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody CodeRequest request) {
        var user = currentUser.require(authorization);
        String language = request.language() == null || request.language().isBlank() ? "cpp" : request.language();
        var existing = codes.findByUser_IdAndProblemIdAndLanguage(user.getId(), request.problemId(), language);
        if (existing.isPresent()) {
            var code = existing.get(); code.setCode(request.code()); codes.save(code);
        } else {
            if (codes.countDistinctProblems(user.getId()) >= MAX_PROBLEMS_PER_USER) {
                codes.findFirstByUser_IdOrderByUpdatedAtAscIdAsc(user.getId()).ifPresent(oldest -> codes.deleteById(oldest.getId()));
            }
            codes.save(UserCode.of(user, request.problemId(), language, request.code()));
        }
        return Map.of("message", "保存成功");
    }

    @GetMapping("/code")
    public Map<String, Object> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        var user = currentUser.require(authorization);
        List<Map<String, Object>> data = codes.findByUser_IdOrderByUpdatedAtDescIdDesc(user.getId()).stream().limit(MAX_PROBLEMS_PER_USER)
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

    @GetMapping("/code/{problemId}")
    public ResponseEntity<?> get(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer problemId,
                                 @RequestParam(defaultValue = "cpp") String language) {
        var user = currentUser.require(authorization);
        return codes.findByUser_IdAndProblemIdAndLanguage(user.getId(), problemId, language)
                .<ResponseEntity<?>>map(item -> ResponseEntity.ok(Map.of("problem_id", item.getProblemId(), "language", item.getLanguage(), "code", item.getCode())))
                .orElseGet(() -> ResponseEntity.ok(java.util.Collections.singletonMap("code", null)));
    }

    public record CodeRequest(@JsonProperty("problem_id") @JsonAlias("problemId") @NotNull Integer problemId,
                              @NotBlank String language, @NotBlank String code) {}
}
