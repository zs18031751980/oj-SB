package com.xauat.oj.api.admin;

import com.xauat.oj.api.auth.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/audit")
public class AdminAuditController {
    private final CurrentUser currentUser;
    private final AuditExporter exporter;

    public AdminAuditController(CurrentUser currentUser, AuditExporter exporter) { this.currentUser = currentUser; this.exporter = exporter; }

    @PostMapping("/export")
    public ResponseEntity<?> export(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @RequestBody(required = false) ExportRequest request) {
        var user = currentUser.require(auth);
        if (!"manager".equals(user.getRole()) && !"staff".equals(user.getRole())) {
            return ResponseEntity.status(403).body(java.util.Map.of("error", "权限不足"));
        }
        int after = request == null || request.after() == null ? 0 : request.after();
        int limit = request == null || request.limit() == null ? 500 : request.limit();
        return ResponseEntity.ok(exporter.export(after, limit));
    }

    public record ExportRequest(Integer after, Integer limit) {}
}
