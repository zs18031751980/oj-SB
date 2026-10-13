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

    /**
     * 增量导出审计记录到受控目录。
     *
     * <p>按 id 升序读取 {@code (after, after+size]} 区间的审计行，逐行写入
     * {@code audit-<id>.json}；写入不可变（只补不覆盖）。返回已导出条数与下一游标。</p>
     *
     * @param after 起始审计 id（不含），必须非负
     * @param limit 单次导出上限（服务端再夹取到 1000）
     * @return {@code {exported, next_cursor}}
     */
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
            // 目录权限收紧为仅属主可读写执行（非 POSIX 文件系统忽略）。
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

    /**
     * 只写不覆盖地持久化单条审计：先写临时文件再原子移动；
     * 若目标已存在，内容一致则跳过，不一致则视为冲突。
     */
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
        // 先写临时文件，再原子重命名，避免导出中途产生半成品文件。
        Path temp = root.resolve(".pending-" + java.util.UUID.randomUUID().toString().replace("-", ""));
        Files.write(temp, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (FileAlreadyExistsException exception) {
            if (!java.util.Arrays.equals(Files.readAllBytes(target), content)) throw new IllegalStateException("审计归档冲突: " + name);
        } finally { Files.deleteIfExists(temp); }
    }
}
