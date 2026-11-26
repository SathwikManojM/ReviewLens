package com.reviewlens.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvReviewParserTest {

    private final CsvReviewParser parser = new CsvReviewParser();

    @Test
    void parsesJsonlInStreamingBatchesAndMapsAmazonFields() throws Exception {
        Path file = Files.createTempFile("reviews-", ".jsonl");
        Files.writeString(file, String.join("\n",
                "{\"asin\":\"A1\",\"parent_asin\":\"P1\",\"text\":\"Great battery life\",\"rating\":5,\"timestamp\":1700000000,\"user_id\":\"u1\",\"helpful_vote\":\"7\",\"verified_purchase\":true}",
                "{\"asin\":\"A2\",\"text\":\"Screen is average\",\"rating\":\"3.0 out of 5 stars\",\"timestamp\":1700000000000}",
                "{\"asin\":\"A3\",\"text\":\"\"}"
        ));

        List<CsvReviewParser.ParsedRow> rows = new ArrayList<>();
        CsvReviewParser.ParseSummary summary = parser.parse(file, 10, 2, null, rows::addAll);

        assertEquals(2, summary.rows());
        assertEquals(1, summary.skipped());
        assertEquals(0, summary.malformedJsonLines());

        CsvReviewParser.ParsedRow first = rows.get(0);
        assertEquals("A1", first.productKey());
        assertEquals("P1", first.parentProductKey());
        assertEquals(5, first.rating());
        assertEquals(Integer.valueOf(7), first.helpfulVote());
        assertEquals(Boolean.TRUE, first.verifiedPurchase());
        assertEquals(LocalDate.of(2023, 11, 14), first.date());

        CsvReviewParser.ParsedRow second = rows.get(1);
        assertEquals(3, second.rating());
        assertNotNull(second.date());
    }

    @Test
    void parsesJsonlGzAndCountsMalformedLines() throws Exception {
        Path raw = Files.createTempFile("reviews-", ".jsonl");
        Files.writeString(raw, "{\"asin\":\"A1\",\"text\":\"Nice camera\"}\n{not_json}\n{\"asin\":\"A2\",\"text\":\"Solid build\"}\n");

        Path gz = Files.createTempFile("reviews-", ".jsonl.gz");
        try (var out = new java.util.zip.GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write(Files.readAllBytes(raw));
        }

        List<CsvReviewParser.ParsedRow> rows = new ArrayList<>();
        CsvReviewParser.ParseSummary summary = parser.parse(gz, 10, 1000, null, rows::addAll);

        assertEquals(2, summary.rows());
        assertEquals(1, summary.malformedJsonLines());
        assertEquals(1, summary.skipped());
    }

    @Test
    void previewDetectsJsonKeysAndTextMapping() throws Exception {
        Path file = Files.createTempFile("preview-", ".ndjson");
        Files.writeString(file, "{\"asin\":\"A1\",\"text\":\"Excellent\",\"rating\":4}\n");

        CsvReviewParser.Preview preview = parser.preview(file, 5);

        assertEquals("jsonl", preview.delimiter());
        assertTrue(preview.columns().contains("text"));
        assertEquals("text", preview.detected().get("text"));
        assertFalse(preview.rows().isEmpty());
    }
}
