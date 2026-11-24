package com.reviewlens.service.admin;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;

/** Time buckets for platform analytics (UTC): 30 days, 12 ISO weeks or 12 months. */
public record Periods(String range, String mongoFormat, Instant since, List<String> keys) {

    public static Periods of(String range) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<String> keys = new ArrayList<>();
        if ("12w".equals(range)) {
            LocalDate start = today.with(DayOfWeek.MONDAY).minusWeeks(11);
            for (int i = 0; i < 12; i++) keys.add(week(start.plusWeeks(i)));
            return new Periods("12w", "%G-W%V", start.atStartOfDay(ZoneOffset.UTC).toInstant(), keys);
        }
        if ("12m".equals(range)) {
            YearMonth start = YearMonth.from(today).minusMonths(11);
            for (int i = 0; i < 12; i++) keys.add(start.plusMonths(i).toString());
            return new Periods("12m", "%Y-%m", start.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(), keys);
        }
        LocalDate start = today.minusDays(29);
        for (int i = 0; i < 30; i++) keys.add(start.plusDays(i).toString());
        return new Periods("30d", "%Y-%m-%d", start.atStartOfDay(ZoneOffset.UTC).toInstant(), keys);
    }

    static String week(LocalDate d) {
        return String.format("%d-W%02d", d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }
}
