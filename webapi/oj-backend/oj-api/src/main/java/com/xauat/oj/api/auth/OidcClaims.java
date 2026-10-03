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
            Map.entry("干事", "staff"), Map.entry("部门主管", "manager"), Map.entry("普通用户", "member"), Map.entry("管理员", "manager"),
            Map.entry("role_admin", "manager"), Map.entry("role_manager", "manager"), Map.entry("role_staff", "staff"),
            Map.entry("role_member", "member"), Map.entry("role_user", "member"));
    private static final Map<String, Integer> PRIORITY = Map.of("manager", 3, "staff", 2, "member", 1);
    private static final Set<String> ROLE_FIELDS = Set.of(
            "authorities", "department", "group", "groups", "identities", "identity", "level", "memberof",
            "position", "realmaccess", "role", "roles", "userrole", "usertype");
    private static final Set<String> IDENTITY_CONTAINERS = Set.of("account", "claims", "principal", "user", "userinfo");
    private static final Set<String> TRUE_VALUES = Set.of("active", "enabled", "normal", "valid", "1", "true");
    private static final Set<String> FALSE_VALUES = Set.of("inactive", "disabled", "locked", "suspended", "invalid", "0", "false");

    private OidcClaims() {}

    public static String highestRole(JsonNode source) {
        List<String> roles = new ArrayList<>();
        visit(source, false, roles);
        String best = "member"; int bestPriority = 1;
        for (String raw : roles) {
            String normalized = normalizeRole(raw);
            int priority = PRIORITY.getOrDefault(normalized, 0);
            if (priority > bestPriority) { bestPriority = priority; best = normalized; }
        }
        return best;
    }

    public static String normalizeRole(String raw) {
        if (raw == null) return "member";
        return ROLE_MAP.getOrDefault(raw.trim().toLowerCase(Locale.ROOT), "member");
    }

    public static Boolean accountStatus(JsonNode source) {
        for (String field : List.of("is_active", "active", "enabled", "status", "account_status")) {
            JsonNode value = source.get(field);
            if (value == null || value.isNull()) continue;
            if (value.isBoolean()) return value.booleanValue();
            String normalized = value.asText("").trim().toLowerCase(Locale.ROOT);
            if (TRUE_VALUES.contains(normalized)) return true;
            if (FALSE_VALUES.contains(normalized)) return false;
        }
        return null;
    }

    public static void mergeIdentityClaims(Map<String, Object> target, JsonNode claims) {
        for (String key : List.of("preferred_username", "nickname", "name", "email", "picture", "avatar", "avatar_url",
                "authorities", "department", "group", "groups", "identities", "identity", "level", "memberOf",
                "position", "realm_access", "role", "roles", "type", "userRole", "userType", "user_type")) {
            if (claims.has(key)) target.put(key, claims.get(key));
        }
    }

    private static void visit(JsonNode node, boolean ambiguous, List<String> roles) {
        if (node == null || !node.isObject()) return;
        node.fields().forEachRemaining(entry -> {
            String normalized = normalizeField(entry.getKey());
            if (ROLE_FIELDS.contains(normalized) || (ambiguous && normalized.equals("type"))) {
                iterRoleValues(entry.getValue(), roles);
            }
            if (IDENTITY_CONTAINERS.contains(normalized)) {
                JsonNode value = entry.getValue();
                if (value.isObject()) visit(value, true, roles);
                else if (value.isArray()) value.forEach(item -> { if (item.isObject()) visit(item, true, roles); });
            }
        });
    }

    private static void iterRoleValues(JsonNode value, List<String> roles) {
        if (value == null) return;
        if (value.isTextual()) { roles.add(value.asText()); return; }
        if (value.isObject()) {
            value.fields().forEachRemaining(entry -> {
                String normalized = normalizeField(entry.getKey());
                if (ROLE_FIELDS.contains(normalized) || normalized.equals("name")) iterRoleValues(entry.getValue(), roles);
            });
            return;
        }
        if (value.isArray()) value.forEach(item -> iterRoleValues(item, roles));
    }

    private static String normalizeField(String field) {
        StringBuilder result = new StringBuilder();
        for (char character : field.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(character)) result.append(character);
        }
        return result.toString();
    }
}
