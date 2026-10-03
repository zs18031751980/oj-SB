package com.xauat.oj.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.core.contest.domain.ContestAudit;
import com.xauat.oj.core.contest.repository.ContestAuditRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计副本导出：把审计行以不可变文件写入受控目录（只补不覆盖），供离线归档。
 * 与旧后端一致，审计记录本身不删除。
 */
@Service
public class AuditExporter {
    private final ContestAuditRepository audits;
    private final ObjectMapper mapper;
    private final String directory;

    public AuditExporter(ContestAuditRepository audits, ObjectMapper mapper,
                         @Value("${oj.audit.export-dir:}") String directory) {
        this.audits = audits; this.mapper = mapper; this.directory = directory;
    }

    public Map<String, Object> export(int after, int limit) {
        if (directory == null || directory.isBlank()) {
            throw new com.xauat.oj.common.exception.OjException("SERVICE_UNAVAILABLE", "未配置审计导出目录");
        }
        if (after < 0) throw new IllegalArgumentException("after 必须为非负整数");
        int size = Math.max(1, Math.min(limit, 1000));
        List<ContestAudit> rows = audits.findTop1000ByIdGreaterThanOrderByIdAsc(after).stream().limit(size).toList();
        Path root = Path.of(directory).toAbsolutePath().normalize();
        int exported = 0; int cursor = after;
        try {
            Files.createDirectories(root);
            try { Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------")); } catch (UnsupportedOperationException ignored) { }
            for (ContestAudit row : rows) {
                writeOnce(root, "audit-" + row.getId() + ".json", row);
                exported++; cursor = row.getId();
            }
        } catch (Exception exception) {
            throw new com.xauat.oj.common.exception.OjException("SERVICE_UNAVAILABLE", "审计导出失败");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exported", exported);
        result.put("next_cursor", cursor);
        return result;
    }

    private void writeOnce(Path root, String name, ContestAudit row) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.getId());
        data.put("contest_id", row.getContestId());
        data.put("action", row.getAction());
        data.put("reason", row.getReason());
        data.put("payload", row.getPayload());
        data.put("created_at", row.getCreatedAt() == null ? null : row.getCreatedAt().toString());
        byte[] content = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        Path target = root.resolve(name);
        if (Files.exists(target)) {
            if (!java.util.Arrays.equals(Files.readAllBytes(target), content)) throw new IllegalStateException("审计归档冲突: " + name);
            return;
        }
        Path temp = root.resolve(".pending-" + java.util.UUID.randomUUID().toString().replace("-", ""));
        Files.write(temp, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (FileAlreadyExistsException exception) {
            if (!java.util.Arrays.equals(Files.readAllBytes(target), content)) throw new IllegalStateException("审计归档冲突: " + name);
        } finally { Files.deleteIfExists(temp); }
    }
}
