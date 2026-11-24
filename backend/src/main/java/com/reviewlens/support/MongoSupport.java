package com.reviewlens.support;

import com.reviewlens.exception.ApiException;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/** Small helpers for native MongoDB documents (ObjectId handling, numbers, dates). All dates are UTC. */
public final class MongoSupport {
    private MongoSupport() {}

    public static boolean isId(String id) {
        return id != null && ObjectId.isValid(id);
    }

    /** Parses an id from a request; an invalid id is reported as "not found" (never as a server error). */
    public static ObjectId oid(String id) {
        if (!isId(id)) throw ApiException.notFound("Resource");
        return new ObjectId(id);
    }

    public static List<ObjectId> oids(Collection<String> ids) {
        return ids.stream().filter(MongoSupport::isId).map(ObjectId::new).toList();
    }

    public static String hex(Object o) {
        if (o instanceof ObjectId id) return id.toHexString();
        return o == null ? null : o.toString();
    }

    public static long lng(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    public static Double dbl(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    public static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }

    public static Double roundOrNull(Double v, int places) {
        return v == null ? null : round(v, places);
    }

    /** Escapes user input for use inside a MongoDB $regex. */
    public static String escapeRegex(String s) {
        return s.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0");
    }

    public static Instant startOfDay(LocalDate d) {
        return d == null ? null : d.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public static Date dateOf(LocalDate d) {
        return d == null ? null : Date.from(startOfDay(d));
    }

    public static LocalDate toLocalDate(Instant i) {
        return i == null ? null : LocalDate.ofInstant(i, ZoneOffset.UTC);
    }

    public static LocalDate toLocalDate(Date d) {
        return d == null ? null : LocalDate.ofInstant(d.toInstant(), ZoneOffset.UTC);
    }

    public static String month(LocalDate d) {
        return d == null ? null : YearMonth.from(d).toString();
    }

    /** {$sum: {$cond: [{$eq: [field, value]}, 1, 0]}} */
    public static Document sumIf(String field, Object value) {
        return new Document("$sum", new Document("$cond", List.of(new Document("$eq", List.of(field, value)), 1, 0)));
    }

    public static Document first(Iterable<Document> it) {
        for (Document d : it) return d;
        return null;
    }

    public static String trunc(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
