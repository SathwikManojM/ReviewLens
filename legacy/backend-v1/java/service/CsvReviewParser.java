package com.reviewlens.service;

import com.reviewlens.exception.ApiException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Module 2 - Dataset management: reads review CSV/TSV files and maps their columns automatically.
 * Supported out of the box (header names are matched case/punctuation-insensitively):
 *  - Datafiniti "Consumer Reviews of Amazon Products": name, asins, brand, reviews.text, reviews.rating ...
 *  - Amazon Fine Food / SNAP style: ProductId, Score, Summary, Text, Time, ProfileName
 *  - Kaggle "amazon.csv": product_id, product_name, rating, review_title, review_content
 *  - Amazon Reviews 2023 (converted to CSV): asin / parent_asin, rating, title, text, timestamp
 */
@Component
public class CsvReviewParser {

    public record ParsedRow(String productKey, String productName, String brand, String category,
                            String reviewer, Integer rating, String title, String text, LocalDate date) {}

    public record ParseSummary(int rows, int skipped, Map<String, String> mapping) {}

    private static final Map<String, List<String>> ALIASES = new LinkedHashMap<>();
    static {
        ALIASES.put("text", List.of("reviewstext", "reviewtext", "text", "reviewbody", "reviewcontent", "content",
                "review", "body", "comment", "comments", "reviews"));
        ALIASES.put("title", List.of("reviewstitle", "reviewtitle", "summary", "title", "reviewheadline", "headline"));
        ALIASES.put("rating", List.of("reviewsrating", "rating", "score", "stars", "starrating", "overall",
                "reviewrating", "ratings"));
        ALIASES.put("productId", List.of("asins", "asin", "parentasin", "productid", "productasin", "itemid", "sku"));
        ALIASES.put("productName", List.of("name", "productname", "producttitle", "itemname", "product", "model"));
        ALIASES.put("brand", List.of("brand", "manufacturer"));
        ALIASES.put("category", List.of("primarycategories", "categories", "category", "maincategory"));
        ALIASES.put("date", List.of("reviewsdate", "reviewdate", "date", "time", "timestamp", "unixreviewtime",
                "reviewtime", "submissiontime", "reviewsdateadded"));
        ALIASES.put("reviewer", List.of("reviewsusername", "username", "reviewername", "profilename", "author",
                "username", "userid", "user"));
    }

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("M/d/yyyy"), DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/M/d"), DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH), DateTimeFormatter.ofPattern("MM d, yyyy"),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH));

    /**
     * Streams the file and hands rows to {@code consumer} in chunks of {@code chunkSize}.
     */
    public ParseSummary parse(Path file, int maxRows, int chunkSize, Consumer<List<ParsedRow>> consumer) {
        try (CSVParser parser = open(file)) {
            {
                Map<String, String> mapping = requireMapping(parser);
                int rows = 0, skipped = 0;
                List<ParsedRow> chunk = new ArrayList<>(chunkSize);
                for (CSVRecord rec : parser) {
                    if (rows >= maxRows) break;
                    String text = get(rec, mapping, "text");
                    if (text == null || text.isBlank() || text.length() < 3) { skipped++; continue; }
                    String pid = truncate(get(rec, mapping, "productId"), 255);
                    String pname = truncate(get(rec, mapping, "productName"), 500);
                    String key = pid != null ? pid : pname != null ? pname : "UNKNOWN";
                    String name = pname != null ? pname : pid != null ? pid : "Unknown product";
                    chunk.add(new ParsedRow(key, name,
                            truncate(get(rec, mapping, "brand"), 150),
                            truncate(get(rec, mapping, "category"), 500),
                            truncate(get(rec, mapping, "reviewer"), 150),
                            parseRating(get(rec, mapping, "rating")),
                            truncate(get(rec, mapping, "title"), 500),
                            text.length() > 20000 ? text.substring(0, 20000) : text,
                            parseDate(get(rec, mapping, "date"))));
                    rows++;
                    if (chunk.size() >= chunkSize) {
                        consumer.accept(chunk);
                        chunk = new ArrayList<>(chunkSize);
                    }
                }
                if (!chunk.isEmpty()) consumer.accept(chunk);
                if (rows == 0) throw ApiException.badRequest("No usable reviews found in the file");
                return new ParseSummary(rows, skipped, mapping);
            }
        } catch (IOException | UncheckedIOException e) {
            throw ApiException.badRequest("Could not read the CSV file: " + e.getMessage());
        }
    }

    /** Reads only the header and returns the detected column mapping (fails fast on unusable files). */
    public Map<String, String> inspect(Path file) {
        try (CSVParser parser = open(file)) {
            return requireMapping(parser);
        } catch (IOException | UncheckedIOException e) {
            throw ApiException.badRequest("Could not read the CSV file: " + e.getMessage());
        }
    }

    private CSVParser open(Path file) throws IOException {
        BufferedReader br = (BufferedReader) openReader(file);
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
        return CSVParser.builder().setReader(br).setFormat(format).get();
    }

    private Map<String, String> requireMapping(CSVParser parser) {
        Map<String, String> mapping = mapColumns(parser.getHeaderNames());
        if (!mapping.containsKey("text")) {
            throw ApiException.badRequest("Could not find a review text column (e.g. reviews.text, reviewText, "
                    + "text, review_content, Text). Found columns: " + parser.getHeaderNames());
        }
        return mapping;
    }

    /** field -> actual header name */
    Map<String, String> mapColumns(List<String> headers) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (String h : headers) {
            if (h == null) continue;
            normalized.putIfAbsent(h.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""), h);
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (var e : ALIASES.entrySet()) {
            for (String alias : e.getValue()) {
                String header = normalized.get(alias);
                if (header != null && !used.contains(header)) {
                    mapping.put(e.getKey(), header);
                    used.add(header);
                    break;
                }
            }
        }
        return mapping;
    }

    private static Reader openReader(Path file) throws IOException {
        BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8);
        br.mark(1);
        if (br.read() != '﻿') br.reset();   // strip UTF-8 BOM
        return br;
    }

    private static String get(CSVRecord rec, Map<String, String> mapping, String field) {
        String header = mapping.get(field);
        if (header == null || !rec.isMapped(header) || !rec.isSet(header)) return null;
        String v = rec.get(header);
        return v == null || v.isBlank() ? null : v.trim();
    }

    static Integer parseRating(String v) {
        if (v == null) return null;
        try {
            String num = v.replaceAll("[^0-9.]", " ").trim().split("\\s+")[0];   // "4.0 out of 5 stars"
            double d = Double.parseDouble(num);
            if (d > 5 && d <= 10) d = d / 2;
            int r = (int) Math.round(d);
            return r >= 1 && r <= 5 ? r : null;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    static LocalDate parseDate(String v) {
        if (v == null) return null;
        String s = v.trim();
        try {
            if (s.matches("\\d{9,13}")) {           // unix seconds or millis
                long n = Long.parseLong(s);
                Instant i = s.length() >= 12 ? Instant.ofEpochMilli(n) : Instant.ofEpochSecond(n);
                return LocalDate.ofInstant(i, ZoneOffset.UTC);
            }
            if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
                return LocalDate.parse(s.substring(0, 10));   // ISO date / datetime
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
