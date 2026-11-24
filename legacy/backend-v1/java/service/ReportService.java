package com.reviewlens.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.reviewlens.dto.Dtos.*;
import com.reviewlens.model.AspectSentiment;
import com.reviewlens.model.Review;
import com.reviewlens.repository.AspectSentimentRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/** Module 9 - report generation (PDF summary report and CSV export of aspect-level results). */
@Service
public class ReportService {
    private static final Color INK = new Color(0x1f, 0x29, 0x37);
    private static final Color MUTED = new Color(0x6b, 0x72, 0x80);
    private static final Color ACCENT = new Color(0x4f, 0x46, 0xe5);
    private static final Color POS = new Color(0x05, 0x96, 0x69);
    private static final Color NEG = new Color(0xdc, 0x26, 0x26);
    private static final Color HEAD_BG = new Color(0xee, 0xf2, 0xff);
    private static final Color ROW_ALT = new Color(0xf9, 0xfa, 0xfb);

    private final AnalyticsService analytics;
    private final DatasetService datasets;
    private final AspectSentimentRepository aspects;

    public ReportService(AnalyticsService analytics, DatasetService datasets, AspectSentimentRepository aspects) {
        this.analytics = analytics;
        this.datasets = datasets;
        this.aspects = aspects;
    }

    public byte[] pdf(Long userId, Long datasetId, Long productId, List<Long> compareIds) {
        var ds = datasets.owned(userId, datasetId);
        Dashboard d = analytics.dashboard(userId, datasetId, productId);
        Comparison cmp = compareIds != null && compareIds.size() >= 2 ? analytics.compare(userId, datasetId, compareIds) : null;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, INK);
        Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, ACCENT);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 10, INK);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, MUTED);

        doc.add(new Paragraph("ReviewLens - Voice of Customer Report", h1));
        doc.add(new Paragraph("Dataset: " + ds.getName() + "   |   Scope: " + (productId == null ? "All products" : d.scopeName())
                + "   |   Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), small));
        doc.add(spacer());

        // KPIs
        long total = d.totalReviews();
        PdfPTable kpi = new PdfPTable(5);
        kpi.setWidthPercentage(100);
        addKpi(kpi, "Reviews", String.valueOf(total));
        addKpi(kpi, "Avg rating", d.avgRating() == null ? "-" : String.format("%.2f / 5", d.avgRating()));
        addKpi(kpi, "Positive", pct(d.sentimentDistribution().get("POSITIVE"), total));
        addKpi(kpi, "Negative", pct(d.sentimentDistribution().get("NEGATIVE"), total));
        addKpi(kpi, "Aspects found", String.valueOf(d.aspects().size()));
        doc.add(kpi);

        section(doc, "Customer insight", h2);
        doc.add(new Paragraph(d.insight(), body));

        section(doc, "Aspect-level sentiment", h2);
        PdfPTable at = table(new float[]{3, 1.4f, 1.4f, 1.4f, 1.4f, 1.4f},
                "Aspect", "Mentions", "Positive", "Negative", "Neutral", "Net score");
        int i = 0;
        for (AspectStat s : d.aspects()) {
            Color bg = i++ % 2 == 1 ? ROW_ALT : Color.WHITE;
            cell(at, s.aspect(), body, bg);
            cell(at, String.valueOf(s.mentions()), body, bg);
            cell(at, pct(s.positive(), s.mentions()), FontFactory.getFont(FontFactory.HELVETICA, 10, POS), bg);
            cell(at, pct(s.negative(), s.mentions()), FontFactory.getFont(FontFactory.HELVETICA, 10, NEG), bg);
            cell(at, pct(s.neutral(), s.mentions()), body, bg);
            cell(at, String.format("%+.2f", s.netSentiment()), body, bg);
        }
        doc.add(at);

        section(doc, "Keyword analysis", h2);
        doc.add(new Paragraph("Top keywords: " + terms(d.keywords(), 20), body));
        doc.add(new Paragraph("What customers praise: " + terms(d.praise(), 12), body));
        doc.add(new Paragraph("Customer concerns: " + terms(d.concerns(), 12), body));

        section(doc, "Representative opinions", h2);
        for (AspectStat s : d.aspects().stream().limit(6).toList()) {
            doc.add(new Paragraph(s.aspect(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, INK)));
            example(doc, datasetId, productId, s.aspect(), Review.Sentiment.POSITIVE, Sort.Direction.DESC, "+ ", POS);
            example(doc, datasetId, productId, s.aspect(), Review.Sentiment.NEGATIVE, Sort.Direction.ASC, "- ", NEG);
        }

        if (cmp != null) {
            section(doc, "Product comparison", h2);
            doc.add(new Paragraph(cmp.summary(), body));
            doc.add(spacer());
            float[] widths = new float[cmp.products().size() + 1];
            widths[0] = 2.2f;
            for (int k = 1; k < widths.length; k++) widths[k] = 2;
            String[] heads = new String[widths.length];
            heads[0] = "Aspect (net score)";
            for (int k = 0; k < cmp.products().size(); k++) heads[k + 1] = AnalyticsService.shortName(cmp.products().get(k).name());
            PdfPTable ct = table(widths, heads);
            for (String aspect : cmp.aspects()) {
                cell(ct, aspect, body, Color.WHITE);
                for (ProductComparison pc : cmp.products()) {
                    String v = pc.aspects().stream().filter(a -> a.aspect().equals(aspect)).findFirst()
                            .map(a -> String.format("%+.2f  (%d)", a.netSentiment(), a.mentions())).orElse("-");
                    cell(ct, v, body, Color.WHITE);
                }
            }
            doc.add(ct);
        }

        if (!d.topProducts().isEmpty()) {
            section(doc, "Products in dataset (top 10 by review volume)", h2);
            PdfPTable pt = table(new float[]{5, 1.3f, 1.3f, 1.5f}, "Product", "Reviews", "Avg rating", "Positive %");
            for (ProductDto p : d.topProducts()) {
                cell(pt, AnalyticsService.shortName(p.name()), body, Color.WHITE);
                cell(pt, String.valueOf(p.reviewCount()), body, Color.WHITE);
                cell(pt, p.avgRating() == null ? "-" : String.format("%.2f", p.avgRating()), body, Color.WHITE);
                cell(pt, p.positivePct() + "%", body, Color.WHITE);
            }
            doc.add(pt);
        }

        doc.add(spacer());
        doc.add(new Paragraph("Method: reviews were cleaned, segmented into opinion clauses, matched against an aspect lexicon "
                + "and scored with a lexicon-based sentiment model (VADER) extended with electronics-specific opinion rules.", small));
        doc.close();
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public void csv(Long userId, Long datasetId, Long productId, OutputStream os) throws IOException {
        datasets.owned(userId, datasetId);
        Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8);
        w.write('﻿');   // Excel-friendly BOM
        try (CSVPrinter p = new CSVPrinter(w, CSVFormat.DEFAULT.builder().setHeader("review_id", "product", "rating",
                "review_date", "overall_sentiment", "aspect", "aspect_term", "aspect_sentiment", "aspect_score", "opinion_snippet").get())) {
            int page = 0;
            Page<AspectSentiment> chunk;
            do {
                chunk = aspects.exportPage(datasetId, productId, PageRequest.of(page++, 1000, Sort.by("id")));
                for (AspectSentiment a : chunk) {
                    Review r = a.getReview();
                    p.printRecord(r.getId(), a.getProduct().getName(), r.getRating(), r.getReviewDate(),
                            r.getOverallSentiment(), a.getAspect(), a.getTerm(), a.getSentiment(), a.getScore(), a.getSnippet());
                }
            } while (chunk.hasNext());
        }
    }

    private void example(Document doc, Long d, Long p, String aspect, Review.Sentiment s, Sort.Direction dir, String prefix, Color color) {
        aspects.examples(d, p, aspect, s, PageRequest.of(0, 1, Sort.by(dir, "score"))).stream().findFirst().ifPresent(a -> {
            Paragraph para = new Paragraph(prefix + "\"" + a.getSnippet() + "\"", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9.5f, color));
            para.setIndentationLeft(12);
            doc.add(para);
        });
    }

    private static void section(Document doc, String title, Font f) {
        Paragraph p = new Paragraph(title, f);
        p.setSpacingBefore(14);
        p.setSpacingAfter(6);
        doc.add(p);
    }

    private static Paragraph spacer() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(2);
        return p;
    }

    private static void addKpi(PdfPTable t, String label, String value) {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(new Color(0xe5, 0xe7, 0xeb));
        c.setPadding(8);
        c.addElement(new Paragraph(label, FontFactory.getFont(FontFactory.HELVETICA, 8.5f, MUTED)));
        c.addElement(new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, INK)));
        t.addCell(c);
    }

    private static PdfPTable table(float[] widths, String... headers) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        Font hf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, INK);
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, hf));
            c.setBackgroundColor(HEAD_BG);
            c.setPadding(5);
            c.setBorderColor(new Color(0xe5, 0xe7, 0xeb));
            t.addCell(c);
        }
        return t;
    }

    private static void cell(PdfPTable t, String v, Font f, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(v == null ? "" : v, f));
        c.setPadding(5);
        c.setBackgroundColor(bg);
        c.setBorderColor(new Color(0xe5, 0xe7, 0xeb));
        t.addCell(c);
    }

    private static String pct(Long n, long total) {
        return pct(n == null ? 0 : n.longValue(), total);
    }

    private static String pct(long n, long total) {
        return total == 0 ? "0%" : Math.round(100.0 * n / total) + "%";
    }

    private static String terms(List<KeywordDto> list, int max) {
        if (list == null || list.isEmpty()) return "-";
        return list.stream().limit(max).map(KeywordDto::term).collect(Collectors.joining(", "));
    }
}
