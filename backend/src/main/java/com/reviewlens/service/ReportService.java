package com.reviewlens.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.mongodb.client.model.Sorts;
import com.reviewlens.dto.Dtos.AspectStat;
import com.reviewlens.dto.Dtos.Comparison;
import com.reviewlens.dto.Dtos.CreateReportRequest;
import com.reviewlens.dto.Dtos.Dashboard;
import com.reviewlens.dto.Dtos.KeywordDto;
import com.reviewlens.dto.Dtos.PageDto;
import com.reviewlens.dto.Dtos.ProductComparison;
import com.reviewlens.dto.Dtos.ProductDto;
import com.reviewlens.dto.Dtos.ReportDto;
import com.reviewlens.exception.ApiException;
import com.reviewlens.model.Dataset;
import com.reviewlens.model.Product;
import com.reviewlens.model.Report;
import com.reviewlens.repository.ProductRepository;
import com.reviewlens.repository.ReportRepository;
import com.reviewlens.security.AuthUser;
import com.reviewlens.support.MongoSupport;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/** Module 9 - report library: PDF analysis reports and aspect-level CSV exports, generated in the background. */
@Service
public class ReportService {
    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final Color INK = new Color(0x1d, 0x1d, 0x1f);
    private static final Color MUTED = new Color(0x6e, 0x6e, 0x73);
    private static final Color ACCENT = new Color(0x00, 0x66, 0xcc);
    private static final Color POS = new Color(0x2a, 0x78, 0xd6);
    private static final Color NEG = new Color(0xd0, 0x3b, 0x3b);
    private static final Color HEAD_BG = new Color(0xf2, 0xf2, 0xf4);
    private static final Color LINE = new Color(0xe5, 0xe5, 0xea);
    private static final Color ROW_ALT = new Color(0xfa, 0xfa, 0xfb);

    private final ReportRepository reports;
    private final AnalyticsService analytics;
    private final DatasetService datasetService;
    private final ProductRepository products;
    private final MongoTemplate mongo;
    private final SettingsService settings;
    private final DataCleanupService cleanup;
    private final TaskExecutor executor;
    private final Path reportsDir;

    public ReportService(ReportRepository reports, AnalyticsService analytics, DatasetService datasetService,
                         ProductRepository products, MongoTemplate mongo, SettingsService settings,
                         DataCleanupService cleanup, @Qualifier("reportExecutor") TaskExecutor executor,
                         @Value("${reviewlens.reports-dir}") String reportsDir) throws IOException {
        this.reports = reports;
        this.analytics = analytics;
        this.datasetService = datasetService;
        this.products = products;
        this.mongo = mongo;
        this.settings = settings;
        this.cleanup = cleanup;
        this.executor = executor;
        this.reportsDir = Files.createDirectories(Path.of(reportsDir).toAbsolutePath());
    }

    // ------------------------------------------------------------------ library
    public PageDto<ReportDto> list(AuthUser user, int page, int size) {
        Page<Report> p = reports.findByOwnerIdOrderByCreatedAtDesc(user.id(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return PageDto.of(p.getContent().stream().map(ReportService::toDto).toList(), p.getNumber(), p.getSize(), p.getTotalElements());
    }

    public ReportDto get(AuthUser user, String id) {
        return toDto(owned(user, id));
    }

    public ReportDto create(AuthUser user, CreateReportRequest req) {
        settings.requireFeature("reportGeneration", "Report generation");
        Dataset ds = datasetService.owned(user, req.datasetId());
        if (ds.getStatus() != Dataset.Status.COMPLETED) {
            throw ApiException.conflict("DATASET_NOT_READY", "The dataset must finish processing before a report can be generated");
        }
        Report.Format format = Report.Format.valueOf(req.format());
        Product product = null;
        if (notBlank(req.productId())) {
            product = products.findByIdAndDatasetId(req.productId(), ds.getId()).orElseThrow(() -> ApiException.notFound("Product"));
        }
        List<String> compare = req.compareProductIds() == null ? List.of()
                : req.compareProductIds().stream().filter(ReportService::notBlank).distinct().toList();
        if (!compare.isEmpty()) {
            if (format != Report.Format.PDF) throw ApiException.badRequest("Product comparison is only available in PDF reports");
            if (compare.size() < 2 || compare.size() > 4) throw ApiException.badRequest("Select 2 to 4 products to compare");
            for (String id : compare) {
                products.findByIdAndDatasetId(id, ds.getId()).orElseThrow(() -> ApiException.notFound("Product"));
            }
        }
        Report r = new Report();
        r.setOwnerId(user.id());
        r.setDatasetId(ds.getId());
        r.setDatasetName(ds.getName());
        r.setProductId(product == null ? null : product.getId());
        r.setProductName(product == null ? null : product.getName());
        r.setCompareProductIds(compare.isEmpty() ? null : compare);
        r.setFormat(format);
        r.setStatus(Report.Status.GENERATING);
        r.setTitle((format == Report.Format.PDF ? "Voice of customer report" : "Aspect-level data export")
                + " · " + (product == null ? ds.getName() : product.getName()));
        reports.save(r);
        String id = r.getId();
        executor.execute(() -> generate(id, user));
        return toDto(r);
    }

    private void generate(String reportId, AuthUser user) {
        Report r = reports.findById(reportId).orElse(null);
        if (r == null) return;
        try {
            Dataset ds = datasetService.owned(user, r.getDatasetId());
            String ext = r.getFormat() == Report.Format.PDF ? "pdf" : "csv";
            String fileName = "reviewlens-" + slug(r.getProductName() != null ? r.getProductName() : ds.getName())
                    + "-" + LocalDate.now() + "." + ext;
            Path file = reportsDir.resolve(reportId + "." + ext);
            if (r.getFormat() == Report.Format.PDF) {
                Files.write(file, buildPdf(user, ds, r.getProductId(), r.getCompareProductIds()));
            } else {
                try (OutputStream os = Files.newOutputStream(file)) {
                    writeCsv(ds, r.getProductId(), os);
                }
            }
            mongo.updateFirst(query(where("_id").is(reportId)), new Update().set("status", Report.Status.READY)
                    .set("fileName", fileName).set("storedPath", file.toString()).set("sizeBytes", Files.size(file))
                    .set("completedAt", Instant.now()), Report.class);
        } catch (Exception e) {
            log.error("Report {} failed", reportId, e);
            String msg = e instanceof ApiException ? e.getMessage() : "Report generation failed";
            mongo.updateFirst(query(where("_id").is(reportId)), new Update().set("status", Report.Status.FAILED)
                    .set("errorMessage", MongoSupport.trunc(msg, 500)).set("completedAt", Instant.now()), Report.class);
        }
    }

    public Report downloadable(AuthUser user, String id) {
        Report r = owned(user, id);
        if (r.getStatus() != Report.Status.READY || r.getStoredPath() == null || !Files.exists(Path.of(r.getStoredPath()))) {
            throw ApiException.conflict("REPORT_NOT_READY", "This report is not available for download");
        }
        return r;
    }

    public void delete(AuthUser user, String id) {
        Report r = owned(user, id);
        cleanup.deleteFile(r.getStoredPath());
        reports.deleteById(r.getId());
    }

    private Report owned(AuthUser user, String id) {
        if (!MongoSupport.isId(id)) throw ApiException.notFound("Report");
        return reports.findByIdAndOwnerId(id, user.id()).orElseThrow(() -> ApiException.notFound("Report"));
    }

    // ------------------------------------------------------------------ direct downloads (v1 API, kept for compatibility)
    public byte[] pdf(AuthUser user, String datasetId, String productId, List<String> compare) {
        settings.requireFeature("reportGeneration", "Report generation");
        return buildPdf(user, datasetService.owned(user, datasetId), productId, compare);
    }

    public void csv(AuthUser user, String datasetId, String productId, OutputStream os) throws IOException {
        settings.requireFeature("reportGeneration", "Report generation");
        Dataset ds = datasetService.owned(user, datasetId);
        if (notBlank(productId)) products.findByIdAndDatasetId(productId, ds.getId()).orElseThrow(() -> ApiException.notFound("Product"));
        writeCsv(ds, productId, os);
    }

    // ------------------------------------------------------------------ builders
    byte[] buildPdf(AuthUser user, Dataset ds, String productId, List<String> compareIds) {
        Dashboard d = analytics.dashboard(user, ds.getId(), productId, null, null);
        Comparison cmp = compareIds != null && compareIds.size() >= 2 ? analytics.compare(user, ds.getId(), compareIds, null, null) : null;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 44, 44, 44, 44);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, INK);
        Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, INK);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 10, INK);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, MUTED);

        Paragraph brand = new Paragraph("REVIEWLENS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, ACCENT));
        brand.setSpacingAfter(4);
        doc.add(brand);
        doc.add(new Paragraph("Voice of Customer Report", h1));
        doc.add(new Paragraph(ds.getName() + "  ·  " + (productId == null ? "All products" : d.scopeName()) + "  ·  Generated "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)), small));
        doc.add(spacer());

        long total = d.totalReviews();
        PdfPTable kpi = new PdfPTable(5);
        kpi.setWidthPercentage(100);
        addKpi(kpi, "Reviews", String.valueOf(total));
        addKpi(kpi, "Products", String.valueOf(d.productsAnalyzed()));
        addKpi(kpi, "Avg rating", d.avgRating() == null ? "-" : String.format(Locale.ENGLISH, "%.2f", d.avgRating()));
        addKpi(kpi, "Positive", pct(d.sentimentDistribution().get("POSITIVE"), total));
        addKpi(kpi, "Negative", pct(d.sentimentDistribution().get("NEGATIVE"), total));
        doc.add(kpi);

        section(doc, "Customer insight", h2);
        doc.add(new Paragraph(d.insight(), body));

        section(doc, "Aspect-level sentiment", h2);
        PdfPTable at = table(new float[]{3, 1.4f, 1.4f, 1.4f, 1.4f, 1.4f}, "Aspect", "Mentions", "Positive", "Negative", "Neutral", "Net score");
        int i = 0;
        for (AspectStat s : d.aspects()) {
            Color bg = i++ % 2 == 1 ? ROW_ALT : Color.WHITE;
            cell(at, s.aspect(), body, bg);
            cell(at, String.valueOf(s.mentions()), body, bg);
            cell(at, pct(s.positive(), s.mentions()), FontFactory.getFont(FontFactory.HELVETICA, 10, POS), bg);
            cell(at, pct(s.negative(), s.mentions()), FontFactory.getFont(FontFactory.HELVETICA, 10, NEG), bg);
            cell(at, pct(s.neutral(), s.mentions()), body, bg);
            cell(at, String.format(Locale.ENGLISH, "%+.2f", s.netSentiment()), body, bg);
        }
        doc.add(at);

        section(doc, "Keyword analysis", h2);
        doc.add(new Paragraph("Top keywords: " + terms(d.keywords(), 20), body));
        doc.add(new Paragraph("What customers praise: " + terms(d.praise(), 12), body));
        doc.add(new Paragraph("Customer concerns: " + terms(d.concerns(), 12), body));

        section(doc, "Representative opinions", h2);
        for (AspectStat s : d.aspects().stream().limit(6).toList()) {
            doc.add(new Paragraph(s.aspect(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, INK)));
            example(doc, ds.getId(), productId, s.aspect(), "POSITIVE", "+ ", POS);
            example(doc, ds.getId(), productId, s.aspect(), "NEGATIVE", "- ", NEG);
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
            for (int k = 0; k < cmp.products().size(); k++) heads[k + 1] = Insights.shortName(cmp.products().get(k).name());
            PdfPTable ct = table(widths, heads);
            for (String aspect : cmp.aspects()) {
                cell(ct, aspect, body, Color.WHITE);
                for (ProductComparison pc : cmp.products()) {
                    String v = pc.aspects().stream().filter(a -> a.aspect().equals(aspect)).findFirst()
                            .map(a -> String.format(Locale.ENGLISH, "%+.2f  (%d)", a.netSentiment(), a.mentions())).orElse("-");
                    cell(ct, v, body, Color.WHITE);
                }
            }
            doc.add(ct);
        }

        if (!d.topProducts().isEmpty()) {
            section(doc, "Products in dataset (top 10 by review volume)", h2);
            PdfPTable pt = table(new float[]{5, 1.3f, 1.3f, 1.5f}, "Product", "Reviews", "Avg rating", "Positive");
            for (ProductDto p : d.topProducts()) {
                cell(pt, Insights.shortName(p.name()), body, Color.WHITE);
                cell(pt, String.valueOf(p.reviewCount()), body, Color.WHITE);
                cell(pt, p.avgRating() == null ? "-" : String.format(Locale.ENGLISH, "%.2f", p.avgRating()), body, Color.WHITE);
                cell(pt, p.positivePct() + "%", body, Color.WHITE);
            }
            doc.add(pt);
        }

        doc.add(spacer());
        doc.add(new Paragraph("Method: reviews were cleaned, segmented into opinion clauses, matched against an aspect lexicon and scored "
                + "with the rule-based baseline engine (" + (ds.getNlpEngine() == null ? "rule-based-vader" : ds.getNlpEngine())
                + ": VADER extended with electronics-specific opinion rules).", small));
        doc.close();
        return out.toByteArray();
    }

    void writeCsv(Dataset ds, String productId, OutputStream os) throws IOException {
        Map<String, String> names = new HashMap<>();
        products.findByDatasetIdOrderByReviewCountDesc(ds.getId()).forEach(p -> names.put(p.getId(), p.getName()));
        org.bson.Document match = new org.bson.Document("datasetId", new ObjectId(ds.getId()));
        if (notBlank(productId)) match.append("productId", new ObjectId(productId));
        Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8);
        w.write('﻿');   // Excel-friendly BOM
        try (CSVPrinter p = new CSVPrinter(w, CSVFormat.DEFAULT.builder().setHeader("review_id", "product", "rating",
                "review_date", "aspect", "aspect_term", "aspect_sentiment", "aspect_score", "opinion_snippet").get())) {
            for (org.bson.Document a : mongo.getCollection("aspectResults").find(match).sort(Sorts.ascending("_id")).batchSize(2000)) {
                p.printRecord(MongoSupport.hex(a.get("reviewId")), names.get(MongoSupport.hex(a.get("productId"))), a.get("rating"),
                        MongoSupport.toLocalDate(a.getDate("reviewDate")), a.getString("aspect"), a.getString("term"),
                        a.getString("sentiment"), a.get("score"), a.getString("snippet"));
            }
        }
    }

    private void example(Document doc, String datasetId, String productId, String aspect, String sentiment, String prefix, Color color) {
        org.bson.Document m = new org.bson.Document("datasetId", new ObjectId(datasetId)).append("aspect", aspect).append("sentiment", sentiment);
        if (productId != null) m.append("productId", new ObjectId(productId));
        org.bson.Document a = mongo.getCollection("aspectResults").find(m)
                .sort("POSITIVE".equals(sentiment) ? Sorts.descending("score") : Sorts.ascending("score")).first();
        if (a == null) return;
        Paragraph para = new Paragraph(prefix + "\"" + a.getString("snippet") + "\"", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9.5f, color));
        para.setIndentationLeft(12);
        doc.add(para);
    }

    private static void section(Document doc, String title, Font f) {
        Paragraph p = new Paragraph(title, f);
        p.setSpacingBefore(16);
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
        c.setBorderColor(LINE);
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
            c.setBorderColor(LINE);
            t.addCell(c);
        }
        return t;
    }

    private static void cell(PdfPTable t, String v, Font f, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(v == null ? "" : v, f));
        c.setPadding(5);
        c.setBackgroundColor(bg);
        c.setBorderColor(LINE);
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

    private static String slug(String s) {
        String v = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return v.length() > 40 ? v.substring(0, 40) : v.isEmpty() ? "report" : v;
    }

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public static ReportDto toDto(Report r) {
        return new ReportDto(r.getId(), r.getTitle(), r.getFormat().name(), r.getStatus().name(), r.getDatasetId(),
                r.getDatasetName(), r.getProductId(), r.getProductName(), r.getCompareProductIds(), r.getSizeBytes(),
                r.getErrorMessage(), r.getCreatedAt(), r.getCompletedAt());
    }
}
