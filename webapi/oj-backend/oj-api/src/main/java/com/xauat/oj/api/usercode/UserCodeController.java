package com.xauat.oj.api.usercode;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.core.usercode.domain.UserCode;
import com.xauat.oj.core.usercode.repository.UserCodeRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserCodeController {
    private final CurrentUser currentUser; private final UserCodeRepository codes;
    public UserCodeController(CurrentUser currentUser, UserCodeRepository codes) { this.currentUser = currentUser; this.codes = codes; }

    @PutMapping("/code")
    @Transactional
    public Map<String, Object> save(@RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody CodeRequest request) {
        var user = currentUser.require(authorization);
        var code = codes.findByUserIdAndProblemIdAndLanguage(user.getId(), request.problemId(), request.language())
                .orElseGet(() -> UserCode.of(user, request.problemId(), request.language(), request.code()));
        code.setCode(request.code());
        return Map.of("problem_id", request.problemId(), "language", request.language(), "saved", true);
    }

    @GetMapping("/code/{problemId}")
    public ResponseEntity<?> get(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer problemId,
                                 @RequestParam(defaultValue = "cpp") String language) {
        var user = currentUser.require(authorization);
        return codes.findByUserIdAndProblemIdAndLanguage(user.getId(), problemId, language).<ResponseEntity<?>>map(item -> ResponseEntity.ok(Map.of("problem_id", item.getProblemId(), "language", item.getLanguage(), "code", item.getCode())))
                .orElseGet(() -> ResponseEntity.ok(java.util.Collections.singletonMap("code", null)));
    }

    public record CodeRequest(@NotNull Integer problemId, @NotBlank String language, @NotBlank String code) {}
}
