package com.reviewlens.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewlens.dto.Dtos.FieldInfo;
import com.reviewlens.exception.ApiException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * Streams review datasets from CSV/TSV/TXT and JSONL/NDJSON/JSONL.GZ files.
 */
@Component
public class CsvReviewParser {

    public enum Format {
        CSV, JSONL, NDJSON, JSONL_GZ
    }

    public record ParsedRow(String productKey, String parentProductKey, String productName, String brand,
                            String category, String reviewer, String sourceReviewId, Integer helpfulVote,
                            Boolean verifiedPurchase, Integer rating, String title, String text, LocalDate date,
                            Map<String, Object> sourceMetadata) {}

    public record ParseSummary(int rows, int skipped, int malformedJsonLines, Map<String, String> mapping) {}

    public record Preview(String delimiter, List<String> columns, Map<String, String> detected,
                          List<List<String>> rows, boolean truncated) {}

    private record Opened(CSVParser parser, char delimiter) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            parser.close();
        }
    }

    public static final List<FieldInfo> FIELDS = List.of(
            new FieldInfo("text", "Review text", true, "The customer's written review"),
            new FieldInfo("title", "Review title", false, "Short review headline"),
            new FieldInfo("rating", "Star rating", false, "1-5 stars"),
            new FieldInfo("productId", "Product ID", false, "ASIN used as product fallback key"),
            new FieldInfo("parentProductId", "Parent product ID", false, "parent_asin (preferred grouping key)"),
            new FieldInfo("productName", "Product name", false, "Use metadata/product catalog name when available"),
            new FieldInfo("brand", "Brand", false, "Optional product metadata"),
            new FieldInfo("category", "Category", false, "Optional product metadata"),
            new FieldInfo("date", "Review date", false, "Supports unix seconds/millis and date strings"),
            new FieldInfo("reviewer", "Reviewer", false, "username / user_id"),
            new FieldInfo("sourceReviewId", "Review ID", false, "Stable upstream review identifier"),
            new FieldInfo("helpfulVote", "Helpful vote", false, "helpful_vote as integer"),
            new FieldInfo("verifiedPurchase", "Verified purchase", false, "verified_purchase true/false")
    );

    private static final Set<String> FIELD_KEYS = new HashSet<>(FIELDS.stream().map(FieldInfo::key).toList());

    private static final Map<String, List<String>> ALIASES = new LinkedHashMap<>();
    static {
        ALIASES.put("text", List.of("reviewstext", "reviewtext", "text", "reviewbody", "review_content", "reviewcontent", "content", "review", "body", "comment", "comments", "reviews"));
        ALIASES.put("title", List.of("reviewtitle", "summary", "title", "reviewheadline", "headline"));
        ALIASES.put("rating", List.of("reviewsrating", "rating", "score", "stars", "starrating", "overall", "reviewrating"));
        ALIASES.put("productId", List.of("asin", "asins", "productid", "productasin", "itemid", "sku"));
        ALIASES.put("parentProductId", List.of("parentasin", "parent_asin", "parentproductid", "parentid"));
        ALIASES.put("productName", List.of("product_name", "productname", "producttitle", "itemname", "name", "item_title", "product"));
        ALIASES.put("brand", List.of("brand", "manufacturer"));
        ALIASES.put("category", List.of("primarycategories", "categories", "category", "maincategory"));
        ALIASES.put("date", List.of("reviewsdate", "reviewdate", "date", "timestamp", "unixreviewtime", "reviewtime", "submissiontime", "time"));
        ALIASES.put("reviewer", List.of("reviewsusername", "username", "reviewername", "profilename", "author", "user", "user_id", "userid"));
        ALIASES.put("sourceReviewId", List.of("review_id", "reviewid", "id", "rid"));
        ALIASES.put("helpfulVote", List.of("helpful_vote", "helpfulvote", "helpful", "helpfulness"));
        ALIASES.put("verifiedPurchase", List.of("verified_purchase", "verifiedpurchase", "verified"));
    }

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("M/d/yyyy"), DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/M/d"), DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH), DateTimeFormatter.ofPattern("MM d, yyyy"),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH));

    private final ObjectMapper mapper = new ObjectMapper();

    /** Streams the file and hands rows to the consumer in chunks. */
    public ParseSummary parse(Path file, int maxRows, int chunkSize, Map<String, String> override,
                              Consumer<List<ParsedRow>> consumer) {
        Format format = detect(file.getFileName().toString());
        return switch (format) {
            case CSV -> parseCsv(file, maxRows, chunkSize, override, consumer);
            case JSONL, NDJSON, JSONL_GZ -> parseJsonLines(file, format, maxRows, chunkSize, override, consumer);
        };
    }

    public Preview preview(Path file, int maxRows) {
        Format format = detect(file.getFileName().toString());
        return switch (format) {
            case CSV -> previewCsv(file, maxRows);
            case JSONL, NDJSON, JSONL_GZ -> previewJson(file, format, maxRows);
        };
    }

    public Map<String, String> resolveMapping(Path file, Map<String, String> override) {
        Format format = detect(file.getFileName().toString());
        return switch (format) {
            case CSV -> resolveMappingFromHeaders(csvHeaders(file), override);
            case JSONL, NDJSON, JSONL_GZ -> resolveMappingFromHeaders(jsonKeys(file, format, 300), override);
        };
    }

    public String formatKey(String fileName) {
        return switch (detect(fileName)) {
            case CSV -> fileName.toLowerCase(Locale.ROOT).endsWith(".tsv") ? "tsv"
                    : fileName.toLowerCase(Locale.ROOT).endsWith(".txt") ? "txt" : "csv";
            case JSONL -> "jsonl";
            case NDJSON -> "ndjson";
            case JSONL_GZ -> "jsonl.gz";
        };
    }

    public static Format detect(String fileName) {
        String lower = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jsonl.gz")) return Format.JSONL_GZ;
        if (lower.endsWith(".jsonl")) return Format.JSONL;
        if (lower.endsWith(".ndjson")) return Format.NDJSON;
        if (lower.endsWith(".csv") || lower.endsWith(".tsv") || lower.endsWith(".txt")) return Format.CSV;
        throw ApiException.badRequest("Unsupported file type. Allowed: .csv, .tsv, .txt, .jsonl, .jsonl.gz, .ndjson");
    }

    private ParseSummary parseCsv(Path file, int maxRows, int chunkSize, Map<String, String> override,
                                  Consumer<List<ParsedRow>> consumer) {
        try (Opened o = open(file)) {
            Map<String, String> mapping = resolveMappingFromHeaders(o.parser().getHeaderNames(), override);
            List<ParsedRow> chunk = new ArrayList<>(chunkSize);
            int rows = 0;
            int skipped = 0;
            for (CSVRecord rec : o.parser()) {
                ParsedRow row = fromCsvRecord(rec, mapping);
                if (row == null) {
                    skipped++;
                    continue;
                }
                chunk.add(row);
                rows++;
                if (chunk.size() >= chunkSize) {
                    consumer.accept(chunk);
                    chunk = new ArrayList<>(chunkSize);
                }
                if (rows >= maxRows) break;
            }
            if (!chunk.isEmpty()) consumer.accept(chunk);
            return new ParseSummary(rows, skipped, 0, mapping);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read CSV/TSV file: " + e.getMessage());
        }
    }

    private ParseSummary parseJsonLines(Path file, Format format, int maxRows, int chunkSize, Map<String, String> override,
                                        Consumer<List<ParsedRow>> consumer) {
        List<String> keys = jsonKeys(file, format, 300);
        Map<String, String> mapping = resolveMappingFromHeaders(keys, override);
        List<ParsedRow> chunk = new ArrayList<>(chunkSize);
        int rows = 0;
        int skipped = 0;
        int malformed = 0;
        try (BufferedReader br = openJsonReader(file, format)) {
            String line;
            while ((line = br.readLine()) != null && rows < maxRows) {
                if (line == null || line.isBlank()) continue;
                JsonNode node;
                try {
                    node = mapper.readTree(line);
                } catch (IOException ex) {
                    malformed++;
                    skipped++;
                    continue;
                }
                ParsedRow row = fromJsonNode(node, mapping);
                if (row == null) {
                    skipped++;
                    continue;
                }
                chunk.add(row);
                rows++;
                if (chunk.size() >= chunkSize) {
                    consumer.accept(chunk);
                    chunk = new ArrayList<>(chunkSize);
                }
            }
            if (!chunk.isEmpty()) consumer.accept(chunk);
            return new ParseSummary(rows, skipped, malformed, mapping);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read JSONL file: " + e.getMessage());
        }
    }

    private Preview previewCsv(Path file, int maxRows) {
        try (Opened o = open(file)) {
            List<String> columns = o.parser().getHeaderNames();
            Map<String, String> detected = mapColumns(columns);
            List<List<String>> rows = new ArrayList<>();
            int count = 0;
            for (CSVRecord r : o.parser()) {
                List<String> values = new ArrayList<>(columns.size());
                for (String h : columns) values.add(r.isMapped(h) && r.isSet(h) ? truncate(r.get(h), 120) : "");
                rows.add(values);
                count++;
                if (count >= maxRows) break;
            }
            return new Preview(String.valueOf(o.delimiter()), columns, detected, rows, count >= maxRows);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not preview file: " + e.getMessage());
        }
    }

    private Preview previewJson(Path file, Format format, int maxRows) {
        List<String> columns = new ArrayList<>();
        Map<String, String> detected;
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader br = openJsonReader(file, format)) {
            String line;
            int count = 0;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode node;
                try {
                    node = mapper.readTree(line);
                } catch (IOException ex) {
                    continue;
                }
                if (node == null || !node.isObject()) continue;
                node.fieldNames().forEachRemaining(field -> {
                    if (!columns.contains(field)) {
                        columns.add(field);
                        for (List<String> existing : rows) existing.add("");
                    }
                });
                List<String> values = new ArrayList<>(columns.size());
                for (String c : columns) {
                    JsonNode v = node.get(c);
                    values.add(v == null || v.isNull() ? "" : truncate(v.isValueNode() ? v.asText() : v.toString(), 120));
                }
                rows.add(values);
                count++;
                if (count >= maxRows) break;
            }
        } catch (IOException e) {
            throw ApiException.badRequest("Could not preview file: " + e.getMessage());
        }
        detected = mapColumns(columns);
        return new Preview("jsonl", columns, detected, rows, rows.size() >= maxRows);
    }

    private List<String> csvHeaders(Path file) {
        try (Opened o = open(file)) {
            return o.parser().getHeaderNames();
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read file headers: " + e.getMessage());
        }
    }

    private List<String> jsonKeys(Path file, Format format, int maxLines) {
        Set<String> keys = new LinkedHashSet<>();
        try (BufferedReader br = openJsonReader(file, format)) {
            String line;
            int lines = 0;
            while ((line = br.readLine()) != null && lines < maxLines) {
                if (line.isBlank()) continue;
                JsonNode node;
                try {
                    node = mapper.readTree(line);
                } catch (IOException ex) {
                    continue;
                }
                if (node != null && node.isObject()) node.fieldNames().forEachRemaining(keys::add);
                lines++;
            }
        } catch (IOException e) {
            throw ApiException.badRequest("Could not inspect JSON keys: " + e.getMessage());
        }
        return new ArrayList<>(keys);
    }

    private Map<String, String> resolveMappingFromHeaders(List<String> headers, Map<String, String> override) {
        Map<String, String> auto = mapColumns(headers);
        if (override == null || override.isEmpty()) {
            if (!auto.containsKey("text")) {
                throw ApiException.badRequest("Could not detect the review text column. Map it manually. Found columns: " + headers);
            }
            return auto;
        }
        Set<String> available = new HashSet<>(headers);
        Map<String, String> mapping = new LinkedHashMap<>(auto);
        for (Map.Entry<String, String> e : override.entrySet()) {
            String field = e.getKey();
            String col = e.getValue();
            if (!FIELD_KEYS.contains(field)) throw ApiException.badRequest("Unknown field in column mapping: " + field);
            if (col == null || col.isBlank()) continue;
            if (!available.contains(col)) throw ApiException.badRequest("Column/key '" + col + "' does not exist in the file");
            mapping.put(field, col);
        }
        if (!mapping.containsKey("text")) throw ApiException.badRequest("Map a source field to 'Review text' - it is required");
        return mapping;
    }

    Map<String, String> mapColumns(List<String> headers) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (String h : headers) {
            if (h == null) continue;
            normalized.putIfAbsent(normalizeHeader(h), h);
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (var e : ALIASES.entrySet()) {
            for (String alias : e.getValue()) {
                String header = normalized.get(normalizeHeader(alias));
                if (header != null && !used.contains(header)) {
                    mapping.put(e.getKey(), header);
                    used.add(header);
                    break;
                }
            }
        }
        return mapping;
    }

    private static String normalizeHeader(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private Opened open(Path file) throws IOException {
        BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8);
        br.mark(64 * 1024);
        String firstLine = br.readLine();
        if (firstLine == null) {
            br.close();
            throw ApiException.badRequest("The file is empty");
        }
        br.reset();
        char delimiter = count(firstLine, '\t') > count(firstLine, ',') ? '\t'
                : count(firstLine, ';') > count(firstLine, ',') ? ';' : ',';
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter).setHeader().setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true).setTrim(true).setAllowMissingColumnNames(true)
                .setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_ALL).setIgnoreSurroundingSpaces(true)
                .get();
        return new Opened(CSVParser.builder().setReader(br).setFormat(format).get(), delimiter);
    }

    private BufferedReader openJsonReader(Path file, Format format) throws IOException {
        InputStream in = Files.newInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(in);
        if (format == Format.JSONL_GZ) {
            return new BufferedReader(new InputStreamReader(new GZIPInputStream(bis), StandardCharsets.UTF_8));
        }
        return new BufferedReader(new InputStreamReader(bis, StandardCharsets.UTF_8));
    }

    private ParsedRow fromCsvRecord(CSVRecord rec, Map<String, String> mapping) {
        String text = truncate(get(rec, mapping, "text"), 3000);
        if (text == null || text.isBlank()) return null;

        String productId = truncate(get(rec, mapping, "productId"), 200);
        String parentProductId = truncate(get(rec, mapping, "parentProductId"), 200);
        if (productId == null && parentProductId == null) productId = "UNKNOWN";

        Map<String, Object> metadata = new LinkedHashMap<>();
        putMeta(metadata, "asin", productId);
        putMeta(metadata, "parent_asin", parentProductId);

        return new ParsedRow(
                productId == null ? "UNKNOWN" : productId,
                parentProductId,
                truncate(get(rec, mapping, "productName"), 220),
                truncate(get(rec, mapping, "brand"), 120),
                truncate(get(rec, mapping, "category"), 120),
                truncate(get(rec, mapping, "reviewer"), 120),
                truncate(get(rec, mapping, "sourceReviewId"), 200),
                parseInteger(get(rec, mapping, "helpfulVote")),
                parseBoolean(get(rec, mapping, "verifiedPurchase")),
                parseRating(get(rec, mapping, "rating")),
                truncate(get(rec, mapping, "title"), 280),
                text,
                parseDate(get(rec, mapping, "date")),
                metadata
        );
    }

    private ParsedRow fromJsonNode(JsonNode node, Map<String, String> mapping) {
        String text = truncate(get(node, mapping, "text"), 3000);
        if (text == null || text.isBlank()) return null;

        String asin = truncate(get(node, mapping, "productId"), 200);
        String parentAsin = truncate(get(node, mapping, "parentProductId"), 200);
        if (asin == null && parentAsin == null) asin = "UNKNOWN";

        Map<String, Object> metadata = new LinkedHashMap<>();
        putMeta(metadata, "asin", asin);
        putMeta(metadata, "parent_asin", parentAsin);
        putMeta(metadata, "user_id", truncate(get(node, mapping, "reviewer"), 120));
        putMeta(metadata, "helpful_vote", parseInteger(get(node, mapping, "helpfulVote")));
        putMeta(metadata, "verified_purchase", parseBoolean(get(node, mapping, "verifiedPurchase")));

        return new ParsedRow(
                asin == null ? "UNKNOWN" : asin,
                parentAsin,
                truncate(get(node, mapping, "productName"), 220),
                truncate(get(node, mapping, "brand"), 120),
                truncate(get(node, mapping, "category"), 120),
                truncate(get(node, mapping, "reviewer"), 120),
                truncate(get(node, mapping, "sourceReviewId"), 200),
                parseInteger(get(node, mapping, "helpfulVote")),
                parseBoolean(get(node, mapping, "verifiedPurchase")),
                parseRating(get(node, mapping, "rating")),
                truncate(get(node, mapping, "title"), 280),
                text,
                parseDate(get(node, mapping, "date")),
                metadata
        );
    }

    private static void putMeta(Map<String, Object> meta, String key, Object value) {
        if (value != null) meta.put(key, value);
    }

    private static String get(CSVRecord rec, Map<String, String> mapping, String field) {
        String header = mapping.get(field);
        if (header == null || !rec.isMapped(header) || !rec.isSet(header)) return null;
        String v = rec.get(header);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String get(JsonNode node, Map<String, String> mapping, String field) {
        String key = mapping.get(field);
        if (key == null || key.isBlank()) return null;
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) return null;
        String text = v.isValueNode() ? v.asText() : v.toString();
        return text == null || text.isBlank() ? null : text.trim();
    }

    static Integer parseRating(String v) {
        if (v == null) return null;
        try {
            String[] tokens = v.replaceAll("[^0-9.]", " ").trim().split("\\s+");
            if (tokens.length == 0 || tokens[0].isBlank()) return null;
            double d = Double.parseDouble(tokens[0]);
            if (d > 5 && d <= 10) d = d / 2;
            int r = (int) Math.round(d);
            return r >= 1 && r <= 5 ? r : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer parseInteger(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            String clean = v.replaceAll("[^0-9-]", "");
            if (clean.isBlank()) return null;
            return Integer.parseInt(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Boolean parseBoolean(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim().toLowerCase(Locale.ROOT);
        if (List.of("true", "1", "yes", "y").contains(s)) return true;
        if (List.of("false", "0", "no", "n").contains(s)) return false;
        return null;
    }

    static LocalDate parseDate(String v) {
        if (v == null) return null;
        String s = v.trim();
        try {
            if (s.matches("\\d{9,13}")) {
                long n = Long.parseLong(s);
                Instant i = s.length() >= 12 ? Instant.ofEpochMilli(n) : Instant.ofEpochSecond(n);
                return LocalDate.ofInstant(i, ZoneOffset.UTC);
            }
            if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
                return LocalDate.parse(s.substring(0, 10));
            }
        } catch (DateTimeException | NumberFormatException ignored) {
            return null;
        }
        String cleaned = s.replaceAll("^Reviewed in .* on ", "").replaceAll("\\s+", " ");
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(cleaned, f);
            } catch (DateTimeParseException ignored) { }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }
}



