package com.xauat.oj.api.admin;

import com.xauat.oj.api.auth.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 审计导出管理端接口（{@code /admin/audit}）。
 *
 * <p>仅 manager/staff 可调用，用于把审计行导出为受控目录中的不可变文件，
 * 供离线归档；审计记录本身不删除。</p>
 */
@RestController
@RequestMapping("/admin/audit")
public class AdminAuditController {
    private final CurrentUser currentUser;
    private final AuditExporter exporter;

    public AdminAuditController(CurrentUser currentUser, AuditExporter exporter) { this.currentUser = currentUser; this.exporter = exporter; }

    /**
     * 按游标增量导出审计记录。
     *
     * <p>权限：manager 或 staff。请求体可选，{@code after} 为起始审计 id（默认 0），
     * {@code limit} 为单次导出上限（默认 500，服务端另有上限约束）。</p>
     *
     * @param auth    认证头
     * @param request 导出参数（可空）
     * @return 导出结果（已导出条数与下一游标）；权限不足返回 403
     */
    @PostMapping("/export")
    public ResponseEntity<?> export(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @RequestBody(required = false) ExportRequest request) {
        var user = currentUser.require(auth);
        if (!"manager".equals(user.getRole()) && !"staff".equals(user.getRole())) {
            return ResponseEntity.status(403).body(java.util.Map.of("error", "权限不足"));
        }
        // 未提供参数时使用安全默认值，避免全量扫描。
        int after = request == null || request.after() == null ? 0 : request.after();
        int limit = request == null || request.limit() == null ? 500 : request.limit();
        return ResponseEntity.ok(exporter.export(after, limit));
    }

    /** 审计导出请求体：游标位置与单页条数。 */
    public record ExportRequest(Integer after, Integer limit) {}
}
