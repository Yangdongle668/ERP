package com.erp.module.system.service.workflow;

import com.erp.module.system.api.org.OrgApi;
import com.erp.module.system.api.workflow.ApprovalBizDefinition.FieldType;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分支条件（需求 08 第 2 节 wf_branch.conditions）：[{field, op, value}]，同一分支内为“并且”。
 * 缺少某条件所需的变量时该条件视为不满足并记录警告（R03）。
 */
@Slf4j
public final class WfConditions {

    private WfConditions() {
    }

    /** 各字段类型可用的运算符 */
    static final Map<FieldType, Set<String>> OPS = Map.of(
            FieldType.NUMBER, Set.of("GT", "GE", "LT", "LE", "EQ", "BETWEEN"),
            FieldType.STRING, Set.of("EQ", "NE", "IN", "NOT_IN"),
            FieldType.ENUM, Set.of("EQ", "NE", "IN", "NOT_IN"),
            FieldType.DICT, Set.of("EQ", "NE", "IN", "NOT_IN"),
            FieldType.DEPT, Set.of("IN_TREE"),
            FieldType.USER, Set.of("IN"),
            FieldType.BOOL, Set.of("EQ"));

    public record Cond(String field, String op, JsonNode value) {
    }

    /** 值是否完整（发布校验） */
    static boolean complete(FieldType type, String op, JsonNode v) {
        if (v == null || v.isNull()) return false;
        return switch (op) {
            case "BETWEEN" -> v.isArray() && v.size() == 2 && number(v.get(0)) != null && number(v.get(1)) != null;
            case "IN", "NOT_IN", "IN_TREE" -> v.isArray() && !v.isEmpty();
            default -> type == FieldType.NUMBER ? number(v) != null : type == FieldType.BOOL ? v.isBoolean() || "true".equals(v.asText()) || "false".equals(v.asText()) : !v.asText().isBlank();
        };
    }

    static boolean matches(List<Cond> conds, Map<String, FieldType> types, Map<String, Object> vars, OrgApi orgApi, String bizType) {
        for (Cond c : conds) {
            FieldType type = types.get(c.field());
            Object actual = vars == null ? null : vars.get(c.field());
            if (type == null || actual == null) {
                log.warn("[审批流] {} 缺少条件字段 {} 的值，条件视为不满足", bizType, c.field());
                return false;
            }
            if (!test(type, c.op(), c.value(), actual, orgApi)) return false;
        }
        return true;
    }

    private static boolean test(FieldType type, String op, JsonNode v, Object actual, OrgApi orgApi) {
        switch (type) {
            case NUMBER -> {
                BigDecimal a = toDecimal(actual);
                if (a == null) return false;
                if ("BETWEEN".equals(op)) {
                    BigDecimal lo = number(v.get(0));
                    BigDecimal hi = number(v.get(1));
                    return lo != null && hi != null && a.compareTo(lo) >= 0 && a.compareTo(hi) <= 0;
                }
                BigDecimal b = number(v);
                if (b == null) return false;
                int cmp = a.compareTo(b);
                return switch (op) {
                    case "GT" -> cmp > 0;
                    case "GE" -> cmp >= 0;
                    case "LT" -> cmp < 0;
                    case "LE" -> cmp <= 0;
                    case "EQ" -> cmp == 0;
                    default -> false;
                };
            }
            case STRING, ENUM, DICT -> {
                String a = String.valueOf(actual);
                return switch (op) {
                    case "EQ" -> a.equals(v.asText());
                    case "NE" -> !a.equals(v.asText());
                    case "IN" -> texts(v).contains(a);
                    case "NOT_IN" -> !texts(v).contains(a);
                    default -> false;
                };
            }
            case DEPT -> {
                Long dept = toLong(actual);
                if (dept == null) return false;
                for (String root : texts(v)) {
                    Long r = parseLong(root);
                    if (r != null && orgApi.getSelfAndChildrenIds(r).contains(dept)) return true;
                }
                return false;
            }
            case USER -> {
                Long user = toLong(actual);
                return user != null && texts(v).contains(String.valueOf(user));
            }
            case BOOL -> {
                boolean a = actual instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(actual));
                return a == (v.isBoolean() ? v.asBoolean() : Boolean.parseBoolean(v.asText()));
            }
            default -> {
                return false;
            }
        }
    }

    static BigDecimal number(JsonNode v) {
        if (v == null || v.isNull()) return null;
        try {
            return v.isNumber() ? v.decimalValue() : new BigDecimal(v.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal toDecimal(Object o) {
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> texts(JsonNode v) {
        List<String> r = new ArrayList<>();
        if (v != null && v.isArray()) v.forEach(x -> r.add(x.asText()));
        else if (v != null && !v.isNull()) r.add(v.asText());
        return r;
    }

    static Long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        return o == null ? null : parseLong(String.valueOf(o));
    }

    static Long parseLong(String s) {
        try {
            return s == null ? null : Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
