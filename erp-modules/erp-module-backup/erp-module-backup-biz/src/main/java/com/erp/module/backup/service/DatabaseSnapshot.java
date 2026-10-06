package com.erp.module.backup.service;

import com.erp.common.exception.BizException;
import com.erp.framework.module.ErpModule;
import com.erp.module.backup.api.BackupErrorCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 数据库整库快照（按 JDBC 元数据通用处理，兼容 MySQL 8 与 H2）：列出参与备份的表、各模块数据库版本，
 * 逐表导出为 JSON Lines（首行为列名与类型），恢复时在同一事务中清空并批量写入。
 *
 * <p>不参与备份 / 恢复的表：各模块迁移历史 {@code flyway_history_*}（版本必须一致才能恢复）、本模块的 {@code bak_*}（备份记录与恢复日志）。
 * 这是唯一按元数据访问全部模块表的组件，只做整表复制，不含业务逻辑（需求 14 第 3 节）。
 */
@Component
public class DatabaseSnapshot {

    private static final int BATCH = 500;

    /** 一张表的导出信息 */
    public record TableData(String name, long rows) {
    }

    private final DataSource dataSource;
    private final List<ErpModule> modules;
    private final ObjectMapper objectMapper;

    public DatabaseSnapshot(DataSource dataSource, List<ErpModule> modules, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.modules = modules;
        this.objectMapper = objectMapper;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    static boolean excluded(String table) {
        String t = table.toLowerCase(Locale.ROOT);
        return t.startsWith("flyway_history_") || t.startsWith("bak_");
    }

    /** 参与备份的表（小写，按名称排序） */
    public List<String> tables(Connection c) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        List<String> out = new ArrayList<>();
        try (ResultSet rs = md.getTables(c.getCatalog(), c.getSchema(), "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                if (schema != null && schema.equalsIgnoreCase("INFORMATION_SCHEMA")) continue;
                String name = rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                if (!excluded(name)) out.add(name);
            }
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    /** 各模块当前数据库版本（迁移历史中最后一个成功的版本号）；模块编码 → 版本 */
    public Map<String, String> schemaVersions(Connection c) throws SQLException {
        Map<String, String> out = new TreeMap<>();
        Set<String> existing = Set.copyOf(allTables(c));
        for (ErpModule m : modules) {
            if (!existing.contains(m.historyTable())) {
                out.put(m.code(), "");
                continue;
            }
            String version = "";
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT version, success FROM " + m.historyTable() + " ORDER BY installed_rank")) {
                while (rs.next()) {
                    String v = rs.getString(1);
                    if (v != null && rs.getBoolean(2)) version = v;
                }
            }
            out.put(m.code(), version);
        }
        return out;
    }

    private List<String> allTables(Connection c) throws SQLException {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = c.getMetaData().getTables(c.getCatalog(), c.getSchema(), "%", new String[]{"TABLE"})) {
            while (rs.next()) out.add(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
        }
        return out;
    }

    public String product(Connection c) throws SQLException {
        return c.getMetaData().getDatabaseProductName();
    }

    private static String quote(Connection c, String name) throws SQLException {
        String q = c.getMetaData().getIdentifierQuoteString();
        q = q == null || q.isBlank() ? "" : q.trim();
        return q + name + q;
    }

    // ==================== 导出 ====================

    /** 导出一张表到 out（不关闭 out）：首行 {"columns":[…],"types":[…]}，之后每行一个 JSON 数组 */
    public long export(Connection c, String table, OutputStream out) throws SQLException, IOException {
        boolean mysql = product(c).toLowerCase(Locale.ROOT).contains("mysql");
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        long rows = 0;
        try (Statement st = c.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            st.setFetchSize(mysql ? Integer.MIN_VALUE : 1000);
            try (ResultSet rs = st.executeQuery("SELECT * FROM " + quote(c, table))) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                int[] types = new int[n];
                ObjectNode head = JsonNodeFactory.instance.objectNode();
                ArrayNode cols = head.putArray("columns");
                ArrayNode ts = head.putArray("types");
                for (int i = 1; i <= n; i++) {
                    cols.add(md.getColumnLabel(i).toLowerCase(Locale.ROOT));
                    types[i - 1] = md.getColumnType(i);
                    ts.add(types[i - 1]);
                }
                w.write(objectMapper.writeValueAsString(head));
                w.write('\n');
                while (rs.next()) {
                    ArrayNode row = JsonNodeFactory.instance.arrayNode(n);
                    for (int i = 1; i <= n; i++) write(rs, i, types[i - 1], row);
                    w.write(objectMapper.writeValueAsString(row));
                    w.write('\n');
                    rows++;
                }
            }
        }
        w.flush();
        return rows;
    }

    private static void write(ResultSet rs, int i, int type, ArrayNode row) throws SQLException {
        switch (type) {
            case Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT -> {
                long v = rs.getLong(i);
                if (rs.wasNull()) row.addNull(); else row.add(v);
            }
            case Types.DECIMAL, Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL -> {
                BigDecimal v = rs.getBigDecimal(i);
                if (v == null) row.addNull(); else row.add(v.toPlainString());
            }
            case Types.BOOLEAN, Types.BIT -> {
                boolean v = rs.getBoolean(i);
                if (rs.wasNull()) row.addNull(); else row.add(v);
            }
            case Types.DATE -> {
                Date v = rs.getDate(i);
                if (v == null) row.addNull(); else row.add(v.toLocalDate().toString());
            }
            case Types.TIME -> {
                Time v = rs.getTime(i);
                if (v == null) row.addNull(); else row.add(v.toString());
            }
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
                Timestamp v = rs.getTimestamp(i);
                if (v == null) row.addNull(); else row.add(v.toLocalDateTime().toString());
            }
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
                byte[] v = rs.getBytes(i);
                if (v == null) row.addNull(); else row.add(Base64.getEncoder().encodeToString(v));
            }
            default -> {
                String v = rs.getString(i);
                if (v == null) row.addNull(); else row.add(v);
            }
        }
    }

    // ==================== 恢复 ====================

    /** 读取一张表数据的输入（每次调用返回新的流） */
    public interface Source {
        InputStream open(String table) throws IOException;
    }

    /** 校验备份的表结构与当前库一致：表集合相同、每张表的列集合相同；返回不一致说明（空表示一致） */
    public List<String> verify(Connection c, Map<String, List<String>> backupColumns) throws SQLException {
        List<String> problems = new ArrayList<>();
        List<String> current = tables(c);
        for (String t : current) if (!backupColumns.containsKey(t)) problems.add("备份缺少表 " + t);
        for (String t : backupColumns.keySet()) if (!current.contains(t)) problems.add("当前系统没有表 " + t);
        for (String t : current) {
            List<String> cols = backupColumns.get(t);
            if (cols == null) continue;
            Set<String> target = columns(c, t).keySet();
            if (!target.equals(Set.copyOf(cols))) problems.add("表 " + t + " 的列不一致");
        }
        return problems;
    }

    /** 当前表的列（小写）→ JDBC 类型，按表中顺序 */
    private Map<String, Integer> columns(Connection c, String table) throws SQLException {
        Map<String, Integer> out = new LinkedHashMap<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM " + quote(c, table) + " WHERE 1 = 0")) {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) out.put(md.getColumnLabel(i).toLowerCase(Locale.ROOT), md.getColumnType(i));
        }
        return out;
    }

    /**
     * 在一个事务中清空全部表并写入备份数据；任何一步失败全部回滚（数据保持恢复前的状态）。
     *
     * @return 写入的行数
     */
    public long restore(List<String> tables, Source source) throws SQLException, IOException {
        try (Connection c = dataSource.getConnection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (Statement st = c.createStatement()) {
                    for (String t : tables) st.executeUpdate("DELETE FROM " + quote(c, t));
                }
                long rows = 0;
                for (String t : tables) {
                    try (InputStream in = source.open(t)) {
                        rows += load(c, t, in);
                    }
                }
                c.commit();
                return rows;
            } catch (SQLException | IOException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        }
    }

    private long load(Connection c, String table, InputStream in) throws SQLException, IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String first = r.readLine();
        if (first == null) throw BizException.of(BackupErrorCodes.INVALID_FILE, "表 " + table + " 数据为空");
        JsonNode head = objectMapper.readTree(first);
        List<String> cols = new ArrayList<>();
        head.get("columns").forEach(x -> cols.add(x.asText()));
        Map<String, Integer> target = columns(c, table);
        int[] types = cols.stream().mapToInt(x -> target.getOrDefault(x, Types.VARCHAR)).toArray();
        String q = c.getMetaData().getIdentifierQuoteString().trim();
        String sql = "INSERT INTO " + quote(c, table) + " (" + cols.stream().map(x -> q + x + q).collect(Collectors.joining(", ")) + ") VALUES ("
                + cols.stream().map(x -> "?").collect(Collectors.joining(", ")) + ")";
        long rows = 0;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int pending = 0;
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode row = objectMapper.readTree(line);
                for (int i = 0; i < cols.size(); i++) bind(ps, i + 1, types[i], row.get(i));
                ps.addBatch();
                rows++;
                if (++pending >= BATCH) {
                    ps.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) ps.executeBatch();
        }
        return rows;
    }

    private static void bind(PreparedStatement ps, int i, int type, JsonNode v) throws SQLException {
        if (v == null || v.isNull()) {
            ps.setNull(i, type);
            return;
        }
        switch (type) {
            case Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT -> ps.setLong(i, v.isBoolean() ? (v.asBoolean() ? 1 : 0) : v.asLong());
            case Types.DECIMAL, Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL -> ps.setBigDecimal(i, new BigDecimal(v.asText()));
            case Types.BOOLEAN, Types.BIT -> ps.setBoolean(i, v.isBoolean() ? v.asBoolean() : v.asLong() != 0);
            case Types.DATE -> ps.setDate(i, Date.valueOf(LocalDate.parse(v.asText().substring(0, 10))));
            case Types.TIME -> ps.setTime(i, Time.valueOf(v.asText()));
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> ps.setTimestamp(i, Timestamp.valueOf(LocalDateTime.parse(v.asText())));
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> ps.setBytes(i, Base64.getDecoder().decode(v.asText()));
            default -> ps.setString(i, v.isTextual() ? v.asText() : v.toString());
        }
    }
}
