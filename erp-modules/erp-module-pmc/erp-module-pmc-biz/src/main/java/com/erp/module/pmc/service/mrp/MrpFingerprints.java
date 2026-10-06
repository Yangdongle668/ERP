package com.erp.module.pmc.service.mrp;

import com.erp.module.pmc.service.mrp.MrpModel.Balance;
import com.erp.module.pmc.service.mrp.MrpModel.Output;
import com.erp.module.pmc.service.mrp.MrpModel.Peg;
import com.erp.module.pmc.service.mrp.MrpModel.Planned;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 物料计算结果指纹：建议（类型、数量、需求日期、净需求）、需求追溯、例外、供需平衡按规范化文本排序后取 SHA-256。
 * 不含下达日期与“已延迟”（随运算日期变化，复制旧建议时重新计算），计划订单序号不参与比较。
 */
final class MrpFingerprints {

    private MrpFingerprints() {
    }

    static Map<Long, String> of(Output out, Set<Long> materials) {
        Map<Long, List<String>> parts = new HashMap<>();
        for (Long id : materials) parts.put(id, new ArrayList<>());
        for (Planned p : out.planned()) {
            StringBuilder sb = new StringBuilder("P|").append(p.type).append('|').append(n(p.qty)).append('|').append(p.requiredDate).append('|').append(n(p.net));
            List<String> pegs = new ArrayList<>();
            for (Peg g : p.pegs) {
                pegs.add(g.demandType() + "," + g.sourceId() + "," + plain(g.sourceNo()) + "," + g.parentMaterialId() + "," + n(g.qty()) + "," + g.date()
                        + (g.parent() == null ? "" : "," + g.parent().requiredDate + "," + n(g.parent().qty)));
            }
            pegs.sort(String::compareTo);
            sb.append('|').append(String.join(";", pegs));
            parts.computeIfAbsent(p.materialId, k -> new ArrayList<>()).add(sb.toString());
        }
        for (MrpModel.Exception e : out.exceptions()) {
            parts.computeIfAbsent(e.materialId(), k -> new ArrayList<>()).add("E|" + e.type() + "|" + e.docType() + "|" + e.docId() + "|" + e.lineId() + "|"
                    + e.supplyDate() + "|" + e.suggestedDate() + "|" + n(e.qty()));
        }
        for (Balance b : out.balances()) {
            parts.computeIfAbsent(b.materialId(), k -> new ArrayList<>()).add("B|" + b.date() + "|" + b.type() + "|" + plain(b.docNo()) + "|" + b.parentMaterialId() + "|"
                    + n(b.demand()) + "|" + n(b.supply()));
        }
        Map<Long, String> out2 = new TreeMap<>();
        parts.forEach((id, list) -> {
            list.sort(String::compareTo);
            out2.put(id, sha256(String.join("\n", list)));
        });
        return out2;
    }

    /** 计划订单序号（“计划订单 #12”）每次运算不同，不参与比较 */
    private static String plain(String no) {
        return no == null ? "" : PLANNED_NO.matcher(no).replaceAll("计划订单");
    }

    private static final java.util.regex.Pattern PLANNED_NO = java.util.regex.Pattern.compile("计划订单 #\\d+");

    private static String n(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
