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

/**
 * 判题死信管理端接口（{@code /admin/judge/dead-letters}）。
 *
 * <p>死信指投递或消费多次仍失败的任务，这里提供查看、重投与归档能力，
 * 仅 manager/staff 可访问。</p>
 */
@RestController
@RequestMapping("/admin/judge/dead-letters")
public class AdminDeadLetterController {
    private final CurrentUser currentUser;
    private final JudgeDeadLetterRepository deadLetters;
    private final JudgeQueue queue;
    public AdminDeadLetterController(CurrentUser currentUser, JudgeDeadLetterRepository deadLetters, JudgeQueue queue) {
        this.currentUser = currentUser;
        this.deadLetters = deadLetters;
        this.queue = queue;
    }

    /**
     * 列出最近 100 条死信。
     *
     * <p>权限：manager/staff。</p>
     *
     * @param auth 认证头
     * @return 死信摘要列表（队列、任务 id、原因、状态）
     */
    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(value = "Authorization", required = false)
    String auth) {
        manager(auth);
        return ResponseEntity.ok(
                deadLetters.findTop100ByOrderByIdDesc().stream().map(x -> Map.of("id", x.getId(), "queue", x.getQueue(),
                        "job_id", x.getJobId(), "reason", x.getReason(), "state", x.getState())).toList());
    }

    /**
     * 重新投递一条未处理（OPEN）死信。
     *
     * <p>权限：manager/staff。仅 OPEN 状态可重投，成功后置为 RETRIED。</p>
     *
     * @param auth 认证头
     * @param id   死信 id
     * @return 202 与 RETRIED 状态；不存在返回 404；已处理返回 409
     */
    @PostMapping("/{id}/retry")
    @Transactional
    public ResponseEntity<?> retry(@RequestHeader(value = "Authorization", required = false)
    String auth,
            @PathVariable
            Integer id) {
        manager(auth);
        var item = deadLetters.findById(id).orElse(null);
        if (item == null)
            return ResponseEntity.notFound().build();
        if (!"OPEN".equals(item.getState()))
            return ResponseEntity.status(409).body(Map.of("error", "死信已处理"));
        queue.publish(item.getQueue(), item.getJobId());
        item.markRetried();
        deadLetters.save(item);
        return ResponseEntity.accepted().body(Map.of("id", id, "state", "RETRIED"));
    }

    /** 将 Redis 死信列表归档（保留最近 1000 条），避免死信堆积。 */
    @PostMapping("/archive")
    @Transactional
    public ResponseEntity<?> archive(@RequestHeader(value = "Authorization", required = false)
    String auth) {
        manager(auth);
        long total = 0;
        // 逐队列归档，仅保留每个队列最近 1000 条死信。
        for (String name : List.of(JudgeQueues.REGULAR, JudgeQueues.CONTEST, JudgeQueues.PRACTICE, JudgeQueues.REJUDGE,
                JudgeQueues.TESTCASE_GEN)) {
            total += queue.archiveDead(name, 1000);
        }
        return ResponseEntity.ok(Map.of("archived", total));
    }

    /** 要求当前用户为 manager/staff，否则抛出 FORBIDDEN。 */
    private void manager(String auth) {
        var user = currentUser.require(auth);
        if (!"manager".equals(user.getRole()) && !"staff".equals(user.getRole()))
            throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "权限不足");
    }
}
