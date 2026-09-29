package com.erp.module.pmc.service;

import com.erp.common.exception.BizException;
import com.erp.module.pmc.api.PmcErrorCodes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ISO 周（如 2026-W40）工具 */
public final class Weeks {

    private static final Pattern WEEK = Pattern.compile("(\\d{4})-W(\\d{2})");

    private Weeks() {
    }

    public static String of(LocalDate d) {
        return String.format("%d-W%02d", d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** 该周周一 */
    public static LocalDate monday(String week) {
        Matcher m = week == null ? null : WEEK.matcher(week.trim());
        if (m == null || !m.matches()) throw BizException.of(PmcErrorCodes.WEEK_INVALID, week);
        int year = Integer.parseInt(m.group(1));
        int w = Integer.parseInt(m.group(2));
        LocalDate d = LocalDate.of(year, 1, 4).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, w).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (!of(d).equals(week.trim())) throw BizException.of(PmcErrorCodes.WEEK_INVALID, week);
        return d;
    }

    public static LocalDate sunday(String week) {
        return monday(week).plusDays(6);
    }

    /** 从 start 到 end 的全部周（含两端） */
    public static List<String> range(String start, String end) {
        List<String> out = new ArrayList<>();
        LocalDate d = monday(start);
        LocalDate last = monday(end);
        while (!d.isAfter(last)) {
            out.add(of(d));
            d = d.plusWeeks(1);
        }
        return out;
    }

    public static String current() {
        return of(LocalDate.now());
    }
}
