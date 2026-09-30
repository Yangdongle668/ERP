package com.erp.architecture;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL 8 保留字检查：H2（MySQL 模式）的关键字与 MySQL 不同，保留字作列名 / 别名时 H2 测试能通过、MySQL 上会报语法错误。
 * 检查实体映射的列名与表名、迁移脚本 CREATE TABLE 的列名、Java 字符串中的 SQL 别名（AS xxx）。
 */
class SqlReservedWordTest {

    /** MySQL 8.0 保留字（官方列表中标记 R 的关键字） */
    static final Set<String> RESERVED = Set.of(("ACCESSIBLE ADD ALL ALTER ANALYZE AND AS ASC ASENSITIVE BEFORE BETWEEN BIGINT BINARY BLOB BOTH BY CALL "
            + "CASCADE CASE CHANGE CHAR CHARACTER CHECK COLLATE COLUMN CONDITION CONSTRAINT CONTINUE CONVERT CREATE CROSS CUBE CUME_DIST CURRENT_DATE "
            + "CURRENT_TIME CURRENT_TIMESTAMP CURRENT_USER CURSOR DATABASE DATABASES DAY_HOUR DAY_MICROSECOND DAY_MINUTE DAY_SECOND DEC DECIMAL DECLARE "
            + "DEFAULT DELAYED DELETE DENSE_RANK DESC DESCRIBE DETERMINISTIC DISTINCT DISTINCTROW DIV DOUBLE DROP DUAL EACH ELSE ELSEIF EMPTY ENCLOSED "
            + "ESCAPED EXCEPT EXISTS EXIT EXPLAIN FALSE FETCH FIRST_VALUE FLOAT FLOAT4 FLOAT8 FOR FORCE FOREIGN FROM FULLTEXT FUNCTION GENERATED GET GRANT "
            + "GROUP GROUPING GROUPS HAVING HIGH_PRIORITY HOUR_MICROSECOND HOUR_MINUTE HOUR_SECOND IF IGNORE IN INDEX INFILE INNER INOUT INSENSITIVE INSERT "
            + "INT INT1 INT2 INT3 INT4 INT8 INTEGER INTERSECT INTERVAL INTO IO_AFTER_GTIDS IO_BEFORE_GTIDS IS ITERATE JOIN JSON_TABLE KEY KEYS KILL LAG "
            + "LAST_VALUE LATERAL LEAD LEADING LEAVE LEFT LIKE LIMIT LINEAR LINES LOAD LOCALTIME LOCALTIMESTAMP LOCK LONG LONGBLOB LONGTEXT LOOP "
            + "LOW_PRIORITY MASTER_BIND MASTER_SSL_VERIFY_SERVER_CERT MATCH MAXVALUE MEDIUMBLOB MEDIUMINT MEDIUMTEXT MIDDLEINT MINUTE_MICROSECOND "
            + "MINUTE_SECOND MOD MODIFIES NATURAL NOT NO_WRITE_TO_BINLOG NTH_VALUE NTILE NULL NUMERIC OF ON OPTIMIZE OPTIMIZER_COSTS OPTION OPTIONALLY OR "
            + "ORDER OUT OUTER OUTFILE OVER PARTITION PERCENT_RANK PRECISION PRIMARY PROCEDURE PURGE RANGE RANK READ READS READ_WRITE REAL RECURSIVE "
            + "REFERENCES REGEXP RELEASE RENAME REPEAT REPLACE REQUIRE RESIGNAL RESTRICT RETURN REVOKE RIGHT RLIKE ROW ROWS ROW_NUMBER SCHEMA SCHEMAS "
            + "SECOND_MICROSECOND SELECT SENSITIVE SEPARATOR SET SHOW SIGNAL SMALLINT SPATIAL SPECIFIC SQL SQLEXCEPTION SQLSTATE SQLWARNING SQL_BIG_RESULT "
            + "SQL_CALC_FOUND_ROWS SQL_SMALL_RESULT SSL STARTING STORED STRAIGHT_JOIN SYSTEM TABLE TERMINATED THEN TINYBLOB TINYINT TINYTEXT TO TRAILING "
            + "TRIGGER TRUE UNDO UNION UNIQUE UNLOCK UNSIGNED UPDATE USAGE USE USING UTC_DATE UTC_TIME UTC_TIMESTAMP VALUES VARBINARY VARCHAR VARCHARACTER "
            + "VARYING VIRTUAL WHEN WHERE WHILE WINDOW WITH WRITE XOR YEAR_MONTH ZEROFILL").split(" "));

    private static final Path MODULES = Path.of("..", "erp-modules");

    @Test
    void entityColumnsAreNotReserved() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));
        List<String> bad = new ArrayList<>();
        int count = 0;
        for (BeanDefinition bd : scanner.findCandidateComponents("com.erp")) {
            Class<?> c = Class.forName(bd.getBeanClassName());
            count++;
            TableName tn = c.getAnnotation(TableName.class);
            if (reserved(tn.value())) bad.add(c.getSimpleName() + " 表名 " + tn.value());
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    String col = column(f);
                    if (col != null && reserved(col)) bad.add(c.getSimpleName() + "." + f.getName() + " 列名 " + col);
                }
            }
        }
        assertThat(count).as("扫描到的实体数").isGreaterThan(100);
        assertThat(bad).as("以下列名是 MySQL 8 保留字，请改名（或用 @TableField(\"`xxx`\") 加引号）").isEmpty();
    }

    @Test
    void migrationColumnsAreNotReserved() throws IOException {
        Pattern col = Pattern.compile("^\\s*`?([A-Za-z_][A-Za-z0-9_]*)`?\\s+(BIGINT|INT|INTEGER|TINYINT|SMALLINT|DECIMAL|VARCHAR|CHAR|TEXT|LONGTEXT|MEDIUMTEXT|"
                + "DATE|DATETIME|TIMESTAMP|TIME|BOOLEAN|BIT|DOUBLE|BLOB|LONGBLOB|JSON)\\b", Pattern.CASE_INSENSITIVE);
        List<String> bad = new ArrayList<>();
        int files = 0;
        try (Stream<Path> s = Files.walk(MODULES)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".sql") && x.toString().contains("src" + java.io.File.separator + "main")).toList()) {
                files++;
                List<String> lines = Files.readAllLines(p);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher m = col.matcher(lines.get(i));
                    if (m.find() && !lines.get(i).trim().startsWith("`") && reserved(m.group(1))) {
                        bad.add(p.getFileName() + ":" + (i + 1) + " 列名 " + m.group(1));
                    }
                }
            }
        }
        assertThat(files).as("扫描到的迁移脚本数").isGreaterThan(10);
        assertThat(bad).as("以下迁移脚本列名是 MySQL 8 保留字").isEmpty();
    }

    @Test
    void sqlAliasesAreNotReserved() throws IOException {
        Pattern literal = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
        Pattern alias = Pattern.compile("\\b[Aa][Ss]\\s+`?([A-Za-z_][A-Za-z0-9_]*)");
        Pattern sqlHint = Pattern.compile("(?i)\\b(SELECT|FROM|SUM\\(|COUNT\\(|MAX\\(|MIN\\(|COALESCE\\()");
        List<String> bad = new ArrayList<>();
        try (Stream<Path> s = Files.walk(MODULES)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".java") && x.toString().contains("src" + java.io.File.separator + "main")).toList()) {
                List<String> lines = Files.readAllLines(p);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher lm = literal.matcher(lines.get(i));
                    while (lm.find()) {
                        String text = lm.group(1);
                        if (!sqlHint.matcher(text).find() && !text.matches("(?i).*\\)\\s+as\\s+.*")) continue;
                        Matcher am = alias.matcher(text);
                        while (am.find()) {
                            if (reserved(am.group(1)) && !text.substring(am.start()).startsWith("AS `") && !text.substring(am.start()).startsWith("as `")) {
                                bad.add(p.getFileName() + ":" + (i + 1) + " 别名 " + am.group(1));
                            }
                        }
                    }
                }
            }
        }
        assertThat(bad).as("以下 SQL 别名是 MySQL 8 保留字").isEmpty();
    }

    /** 实体字段映射的列名；不映射或已加反引号的返回 null */
    static String column(Field f) {
        TableField tf = f.getAnnotation(TableField.class);
        if (tf != null && !tf.exist()) return null;
        TableId id = f.getAnnotation(TableId.class);
        String name = tf != null && !tf.value().isEmpty() ? tf.value() : id != null && !id.value().isEmpty() ? id.value() : snake(f.getName());
        return name.startsWith("`") ? null : name;
    }

    static String snake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    static boolean reserved(String word) {
        return word != null && RESERVED.contains(word.toUpperCase(Locale.ROOT));
    }
}
