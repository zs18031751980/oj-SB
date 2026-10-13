package com.xauat.oj.api.auth;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** OIDC/OAuth 身份声明解析：从供应商 claims 提取最高角色、账号状态，并合并身份字段。 */
public final class OidcClaims {
    private static final Map<String, String> ROLE_MAP = Map.ofEntries(
            Map.entry("member", "member"), Map.entry("staff", "staff"), Map.entry("manager", "manager"),
            Map.entry("admin", "manager"), Map.entry("administrator", "manager"), Map.entry("superuser", "manager"),
            Map.entry("department", "staff"), Map.entry("minister", "manager"), Map.entry("president", "manager"),
            Map.entry("founder", "manager"), Map.entry("user", "member"),
            Map.entry("部长", "manager"), Map.entry("部员", "staff"), Map.entry("社员", "member"),
            Map.entry("社长", "manager"), Map.entry("副社长", "manager"), Map.entry("副部长", "manager"),
            Map.entry("干事", "staff"), Map.entry("部门主管", "manager"), Map.entry("普通用户", "member"),
            Map.entry("管理员", "manager"),
            Map.entry("role_admin", "manager"), Map.entry("role_manager", "manager"), Map.entry("role_staff", "staff"),
            Map.entry("role_member", "member"), Map.entry("role_user", "member"));
    private static final Map<String, Integer> PRIORITY = Map.of("manager", 3, "staff", 2, "member", 1);
    private static final Set<String> ROLE_FIELDS = Set.of(
            "authorities", "department", "group", "groups", "identities", "identity", "level", "memberof",
            "position", "realmaccess", "role", "roles", "userrole", "usertype");
    private static final Set<String> IDENTITY_CONTAINERS = Set.of("account", "claims", "principal", "user", "userinfo");
    private static final Set<String> TRUE_VALUES = Set.of("active", "enabled", "normal", "valid", "1", "true");
    private static final Set<String> FALSE_VALUES = Set.of("inactive", "disabled", "locked", "suspended", "invalid",
            "0", "false");

    /** 工具类禁止实例化。 */
    private OidcClaims() {
    }

    /**
     * 递归扫描 claims，提取并归一化后返回优先级最高的角色。
     *
     * @param source 供应商身份声明
     * @return 归一化角色（manager/staff/member），无有效角色时默认 member
     */
    public static String highestRole(JsonNode source) {
        List<String> roles = new ArrayList<>();
        visit(source, false, roles);
        String best = "member";
        int bestPriority = 1;
        // 允许多个角色字段并存，按优先级取最高者，避免因字段顺序不同产生不一致结果
        for (String raw : roles) {
            String normalized = normalizeRole(raw);
            int priority = PRIORITY.getOrDefault(normalized, 0);
            if (priority > bestPriority) {
                bestPriority = priority;
                best = normalized;
            }
        }
        return best;
    }

    /**
     * 将供应商角色名归一化为本地角色。
     *
     * @param raw 原始角色名，可为 null
     * @return 归一化角色，未知值回退 member
     */
    public static String normalizeRole(String raw) {
        if (raw == null)
            return "member";
        return ROLE_MAP.getOrDefault(raw.trim().toLowerCase(Locale.ROOT), "member");
    }

    /**
     * 从 claims 推导账号启用状态。
     *
     * @param source 供应商身份声明
     * @return true/false；无法判断返回 {@code null}（表示不改变本地状态）
     */
    public static Boolean accountStatus(JsonNode source) {
        // 兼容不同供应商的字段命名
        for (String field : List.of("is_active", "active", "enabled", "status", "account_status")) {
            JsonNode value = source.get(field);
            if (value == null || value.isNull())
                continue;
            if (value.isBoolean())
                return value.booleanValue();
            String normalized = value.asText("").trim().toLowerCase(Locale.ROOT);
            if (TRUE_VALUES.contains(normalized))
                return true;
            if (FALSE_VALUES.contains(normalized))
                return false;
        }
        return null;
    }

    /**
     * 将 claims 中的身份字段合并进目标映射。
     *
     * @param target 目标映射
     * @param claims 供应商声明
     */
    public static void mergeIdentityClaims(Map<String, Object> target, JsonNode claims) {
        for (String key : List.of("preferred_username", "nickname", "name", "email", "picture", "avatar", "avatar_url",
                "authorities", "department", "group", "groups", "identities", "identity", "level", "memberOf",
                "position", "realm_access", "role", "roles", "type", "userRole", "userType", "user_type")) {
            if (claims.has(key))
                target.put(key, claims.get(key));
        }
    }

    /**
     * 深度优先遍历对象，收集角色字段。
     *
     * @param ambiguous 当前是否处于身份容器内；只有此时才把含义模糊的 {@code type} 也当作角色
     */
    private static void visit(JsonNode node, boolean ambiguous, List<String> roles) {
        if (node == null || !node.isObject())
            return;
        node.fields().forEachRemaining(entry -> {
            String normalized = normalizeField(entry.getKey());
            if (ROLE_FIELDS.contains(normalized) || (ambiguous && normalized.equals("type"))) {
                iterRoleValues(entry.getValue(), roles);
            }
            if (IDENTITY_CONTAINERS.contains(normalized)) {
                // 进入 user/claims 等容器后，才把 type 视为角色来源，降低误判
                JsonNode value = entry.getValue();
                if (value.isObject())
                    visit(value, true, roles);
                else if (value.isArray())
                    value.forEach(item -> {
                        if (item.isObject())
                            visit(item, true, roles);
                    });
            }
        });
    }

    /** 从字符串、对象或数组中递归抽取角色名。 */
    private static void iterRoleValues(JsonNode value, List<String> roles) {
        if (value == null)
            return;
        if (value.isTextual()) {
            roles.add(value.asText());
            return;
        }
        if (value.isObject()) {
            value.fields().forEachRemaining(entry -> {
                String normalized = normalizeField(entry.getKey());
                if (ROLE_FIELDS.contains(normalized) || normalized.equals("name"))
                    iterRoleValues(entry.getValue(), roles);
            });
            return;
        }
        if (value.isArray())
            value.forEach(item -> iterRoleValues(item, roles));
    }

    /** 去掉字段名中的分隔符并小写，使 camelCase/snake_case/kebab-case 能统一匹配。 */
    private static String normalizeField(String field) {
        StringBuilder result = new StringBuilder();
        for (char character : field.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(character))
                result.append(character);
        }
        return result.toString();
    }
}
