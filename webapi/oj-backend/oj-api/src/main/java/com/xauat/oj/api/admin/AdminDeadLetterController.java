package com.xauat.oj.api.admin;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.common.constant.JudgeQueues;
import com.xauat.oj.core.contest.repository.JudgeDeadLetterRepository;
import com.xauat.oj.infrastructure.queue.JudgeQueue;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/judge/dead-letters")
public class AdminDeadLetterController {
    private final CurrentUser currentUser; private final JudgeDeadLetterRepository deadLetters; private final JudgeQueue queue;
    public AdminDeadLetterController(CurrentUser currentUser, JudgeDeadLetterRepository deadLetters, JudgeQueue queue) { this.currentUser = currentUser; this.deadLetters = deadLetters; this.queue = queue; }

    @GetMapping public ResponseEntity<?> list(@RequestHeader(value="Authorization", required=false) String auth) { manager(auth); return ResponseEntity.ok(deadLetters.findTop100ByOrderByIdDesc().stream().map(x -> Map.of("id", x.getId(), "queue", x.getQueue(), "job_id", x.getJobId(), "reason", x.getReason(), "state", x.getState())).toList()); }

    @PostMapping("/{id}/retry") @Transactional
    public ResponseEntity<?> retry(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) { manager(auth); var item = deadLetters.findById(id).orElse(null); if (item == null) return ResponseEntity.notFound().build(); if (!"OPEN".equals(item.getState())) return ResponseEntity.status(409).body(Map.of("error", "死信已处理")); queue.publish(item.getQueue(), item.getJobId()); item.markRetried(); deadLetters.save(item); return ResponseEntity.accepted().body(Map.of("id", id, "state", "RETRIED")); }

    /** 将 Redis 死信列表归档（保留最近 1000 条），避免死信堆积。 */
    @PostMapping("/archive") @Transactional
    public ResponseEntity<?> archive(@RequestHeader(value="Authorization", required=false) String auth) {
        manager(auth);
        long total = 0;
        for (String name : List.of(JudgeQueues.REGULAR, JudgeQueues.CONTEST, JudgeQueues.PRACTICE, JudgeQueues.REJUDGE, JudgeQueues.TESTCASE_GEN)) {
            total += queue.archiveDead(name, 1000);
        }
        return ResponseEntity.ok(Map.of("archived", total));
    }

    private void manager(String auth) { var user = currentUser.require(auth); if (!"manager".equals(user.getRole()) && !"staff".equals(user.getRole())) throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "权限不足"); }
}
