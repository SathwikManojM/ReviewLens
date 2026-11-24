package com.reviewlens.service.admin;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.reviewlens.dto.AdminDtos.AdminAnalytics;
import com.reviewlens.dto.AdminDtos.CategoryStat;
import com.reviewlens.dto.AdminDtos.GrowthPoint;
import com.reviewlens.dto.AdminDtos.OutcomePoint;
import com.reviewlens.security.AuthUser;
import com.reviewlens.service.AuditService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Platform-wide administrative reports (CSV and PDF), built from the same aggregations as the analytics page. */
@Service
public class AdminReportService {
    private static final Color INK = new Color(0x1d, 0x1d, 0x1f);
    private static final Color MUTED = new Color(0x6e, 0x6e, 0x73);
    private static final Color ACCENT = new Color(0x58, 0x56, 0xd6);
    private static final Color HEAD_BG = new Color(0xf2, 0xf2, 0xf4);
    private static final Color LINE = new Color(0xe5, 0xe5, 0xea);

    private final AdminDashboardService dashboard;
    private final AuditService audit;

    public AdminReportService(AdminDashboardService dashboard, AuditService audit) {
        this.dashboard = dashboard;
        this.audit = audit;
    }

    public void exportCsv(AuthUser admin, String range, OutputStream os) throws IOException {
        AdminAnalytics a = dashboard.analytics(range);
        Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8);
        w.write('﻿');
        try (CSVPrinter p = new CSVPrinter(w, CSVFormat.DEFAULT.builder().setHeader("section", "period_or_name", "metric", "value").get())) {
            for (Map.Entry<String, Long> e : a.totals().entrySet()) p.printRecord("totals", a.range(), e.getKey(), e.getValue());
            if (a.successRate() != null) p.printRecord("totals", a.range(), "analysisSuccessRatePct", a.successRate());
            for (GrowthPoint g : a.userGrowth()) {
                p.printRecord("user_growth", g.period(), "newUsers", g.newUsers());
                p.printRecord("user_growth", g.period(), "totalUsers", g.totalUsers());
            }
            a.uploads().forEach(c -> print(p, "dataset_uploads", c.period(), "uploads", c.count()));
            a.reviewVolume().forEach(c -> print(p, "review_processing", c.period(), "reviewsProcessed", c.count()));
            for (OutcomePoint o : a.jobOutcomes()) {
                p.printRecord("analysis_outcomes", o.period(), "completed", o.completed());
                p.printRecord("analysis_outcomes", o.period(), "failed", o.failed());
            }
            for (CategoryStat c : a.topCategories()) {
                p.printRecord("top_categories", c.category(), "products", c.products());
                p.printRecord("top_categories", c.category(), "reviews", c.reviews());
            }
            a.usageHeatmap().forEach(h -> print(p, "usage_by_weekday_hour_utc", "day" + h.dayOfWeek() + "-" + h.hour() + "h", "events", h.count()));
        }
        audit.record(admin, "REPORT_EXPORTED", "PLATFORM_REPORT", null, "Platform report (" + a.range() + ", CSV)", Map.of("format", "CSV"));
    }

    public byte[] exportPdf(AuthUser admin, String range) {
        AdminAnalytics a = dashboard.analytics(range);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 44, 44, 44, 44);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, INK);
        Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, INK);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 10, INK);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, MUTED);
        doc.add(new Paragraph("REVIEWLENS ADMIN", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, ACCENT)));
        doc.add(new Paragraph("Platform report", h1));
        String label = switch (a.range()) { case "12w" -> "last 12 weeks"; case "12m" -> "last 12 months"; default -> "last 30 days"; };
        doc.add(new Paragraph("Period: " + label + " (UTC)  ·  Generated "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)), small));

        section(doc, "Summary", h2);
        PdfPTable t = table(new float[]{3, 2}, "Metric", "Value");
        a.totals().forEach((k, v) -> { cell(t, label(k), body); cell(t, String.valueOf(v), body); });
        cell(t, "Analysis success rate", body);
        cell(t, a.successRate() == null ? "-" : a.successRate() + "%", body);
        doc.add(t);

        section(doc, "Activity by period", h2);
        PdfPTable pt = table(new float[]{2, 1.4f, 1.4f, 1.6f, 1.3f, 1.3f}, "Period", "New users", "Uploads", "Reviews processed", "Jobs OK", "Jobs failed");
        for (int i = 0; i < a.userGrowth().size(); i++) {
            cell(pt, a.userGrowth().get(i).period(), body);
            cell(pt, String.valueOf(a.userGrowth().get(i).newUsers()), body);
            cell(pt, String.valueOf(a.uploads().get(i).count()), body);
            cell(pt, String.valueOf(a.reviewVolume().get(i).count()), body);
            cell(pt, String.valueOf(a.jobOutcomes().get(i).completed()), body);
            cell(pt, String.valueOf(a.jobOutcomes().get(i).failed()), body);
        }
        doc.add(pt);

        section(doc, "Most analysed product categories", h2);
        List<CategoryStat> cats = a.topCategories();
        if (cats.isEmpty()) {
            doc.add(new Paragraph("No products yet.", body));
        } else {
            PdfPTable ct = table(new float[]{4, 1.5f, 1.5f}, "Category", "Products", "Reviews");
            for (CategoryStat c : cats) {
                cell(ct, c.category(), body);
                cell(ct, String.valueOf(c.products()), body);
                cell(ct, String.valueOf(c.reviews()), body);
            }
            doc.add(ct);
        }
        doc.close();
        audit.record(admin, "REPORT_EXPORTED", "PLATFORM_REPORT", null, "Platform report (" + a.range() + ", PDF)", Map.of("format", "PDF"));
        return out.toByteArray();
    }

    private static void print(CSVPrinter p, Object... values) {
        try {
            p.printRecord(values);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static String label(String key) {
        return switch (key) {
            case "newUsers" -> "New users";
            case "uploads" -> "Dataset uploads";
            case "reviewsProcessed" -> "Reviews processed";
            case "jobsCompleted" -> "Analysis jobs completed";
            case "jobsFailed" -> "Analysis jobs failed";
            case "totalUsers" -> "Registered users (end of period)";
            default -> key;
        };
    }

    private static void section(Document doc, String title, Font f) {
        Paragraph p = new Paragraph(title, f);
        p.setSpacingBefore(16);
        p.setSpacingAfter(6);
        doc.add(p);
    }

    private static PdfPTable table(float[] widths, String... headers) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        Font hf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, INK);
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, hf));
            c.setBackgroundColor(HEAD_BG);
            c.setBorderColor(LINE);
            c.setPadding(5);
            t.addCell(c);
        }
        return t;
    }

    private static void cell(PdfPTable t, String v, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(v == null ? "" : v, f));
        c.setBorderColor(LINE);
        c.setPadding(5);
        t.addCell(c);
    }
}
