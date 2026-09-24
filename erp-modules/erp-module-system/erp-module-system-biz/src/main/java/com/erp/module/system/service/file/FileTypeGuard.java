package com.erp.module.system.service.file;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文件头（魔数）校验（需求 SYS-FIL-R02）：已知类型的内容必须与扩展名一致；任何扩展名都不允许是可执行文件；
 * 文本类扩展名不允许包含二进制内容。未登记签名的扩展名只做可执行文件检查。
 */
public final class FileTypeGuard {

    private FileTypeGuard() {
    }

    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] ZIP_EMPTY = {0x50, 0x4B, 0x05, 0x06};
    private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private static final Map<String, List<byte[]>> SIGNATURES = Map.ofEntries(
            Map.entry("jpg", List.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})),
            Map.entry("jpeg", List.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})),
            Map.entry("png", List.of(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})),
            Map.entry("gif", List.of("GIF87a".getBytes(StandardCharsets.US_ASCII), "GIF89a".getBytes(StandardCharsets.US_ASCII))),
            Map.entry("bmp", List.of("BM".getBytes(StandardCharsets.US_ASCII))),
            Map.entry("webp", List.of("RIFF".getBytes(StandardCharsets.US_ASCII))),
            Map.entry("pdf", List.of("%PDF".getBytes(StandardCharsets.US_ASCII))),
            Map.entry("zip", List.of(ZIP, ZIP_EMPTY)),
            Map.entry("docx", List.of(ZIP)),
            Map.entry("xlsx", List.of(ZIP)),
            Map.entry("pptx", List.of(ZIP)),
            Map.entry("doc", List.of(OLE)),
            Map.entry("xls", List.of(OLE)),
            Map.entry("ppt", List.of(OLE)),
            Map.entry("rar", List.of("Rar!".getBytes(StandardCharsets.US_ASCII))),
            Map.entry("7z", List.of(new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C})),
            // DWG 以版本号开头：AC1012、AC1015、AC1032 …
            Map.entry("dwg", List.of("AC1".getBytes(StandardCharsets.US_ASCII))));

    /** 文本类：不能包含 NUL 字节 */
    private static final Set<String> TEXT = Set.of("txt", "csv", "dxf", "step", "stp", "igs", "iges", "json", "xml", "md", "log");

    private static final List<byte[]> EXECUTABLE = List.of(
            new byte[]{0x4D, 0x5A},                   // MZ（Windows exe/dll）
            new byte[]{0x7F, 0x45, 0x4C, 0x46},       // ELF
            new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}, // Mach-O fat / Java class
            new byte[]{(byte) 0xCF, (byte) 0xFA, (byte) 0xED, (byte) 0xFE}, // Mach-O 64
            "#!".getBytes(StandardCharsets.US_ASCII)); // 脚本

    /** @param head 文件开头（至少读取 8KB，文件更小时为全部内容） */
    public static boolean matches(String ext, byte[] head) {
        for (byte[] exe : EXECUTABLE) {
            if (startsWith(head, exe)) return false;
        }
        List<byte[]> sigs = SIGNATURES.get(ext);
        if (sigs != null) return sigs.stream().anyMatch(s -> startsWith(head, s));
        if (TEXT.contains(ext)) {
            for (byte b : head) {
                if (b == 0) return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }
}
