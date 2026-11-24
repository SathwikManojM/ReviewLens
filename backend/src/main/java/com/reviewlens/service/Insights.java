package com.reviewlens.service;

import com.reviewlens.dto.Dtos.AspectStat;
import com.reviewlens.dto.Dtos.AspectWinner;
import com.reviewlens.dto.Dtos.ProductComparison;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Generates plain-language customer insight text from aspect statistics (deterministic, data-derived). */
public final class Insights {
    private Insights() {}

    private static final Map<String, String> PHRASE = Map.of(
            "Battery", "battery life", "Price", "pricing", "Delivery & Packaging", "delivery and packaging",
            "Heating", "heating", "Software", "software", "Customer Service", "customer service");

    public static String phrase(String aspect) {
        return PHRASE.getOrDefault(aspect, aspect.toLowerCase(Locale.ROOT));
    }

    public static String joinList(List<String> items) {
        if (items.size() <= 1) return items.isEmpty() ? "" : items.get(0);
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    public static String shortName(String name) {
        return name == null ? "" : name.length() > 40 ? name.substring(0, 40).trim() + "…" : name;
    }

    public static String insight(List<AspectStat> stats, long totalReviews) {
        List<AspectStat> mentioned = stats.stream().filter(s -> s.mentions() > 0).toList();
        if (mentioned.isEmpty()) return "No product aspects were detected in these reviews yet.";
        long min = Math.max(2, Math.round(totalReviews * 0.01));
        List<AspectStat> eligible = mentioned.stream().filter(s -> s.mentions() >= min).toList();
        if (eligible.isEmpty()) eligible = mentioned;
        List<String> praised = eligible.stream().filter(s -> s.netSentiment() >= 0.25)
                .sorted(Comparator.comparingDouble(AspectStat::netSentiment).reversed()).limit(3).map(s -> phrase(s.aspect())).toList();
        List<String> criticized = eligible.stream().filter(s -> s.netSentiment() <= -0.1)
                .sorted(Comparator.comparingDouble(AspectStat::netSentiment)).limit(3).map(s -> phrase(s.aspect())).toList();
        List<String> mixed = eligible.stream().filter(s -> s.netSentiment() > -0.1 && s.netSentiment() < 0.25)
                .limit(2).map(s -> phrase(s.aspect())).toList();

        StringBuilder sb = new StringBuilder();
        if (!praised.isEmpty() && !criticized.isEmpty()) {
            sb.append("Customers appreciate the ").append(joinList(praised))
              .append(" but express dissatisfaction with ").append(joinList(criticized)).append(".");
        } else if (!praised.isEmpty()) {
            sb.append("Customers are largely satisfied, especially with the ").append(joinList(praised)).append(".");
        } else if (!criticized.isEmpty()) {
            sb.append("Customers are mostly dissatisfied, particularly with ").append(joinList(criticized)).append(".");
        }
        if (!mixed.isEmpty()) sb.append(sb.isEmpty() ? "" : " ").append("Opinions on ").append(joinList(mixed)).append(" are mixed.");
        if (totalReviews <= 1) return sb.toString().trim();
        AspectStat top = mentioned.get(0);
        sb.append(" The most discussed aspect is ").append(phrase(top.aspect())).append(" (").append(top.mentions())
          .append(" mentions, ").append(Math.round(100.0 * top.positive() / top.mentions())).append("% positive).");
        return sb.toString().trim();
    }

    public static String comparisonSummary(List<ProductComparison> list, List<AspectWinner> winners) {
        if (winners.isEmpty()) return "Not enough overlapping aspect mentions to compare these products yet.";
        List<String> parts = new ArrayList<>();
        for (ProductComparison pc : list) {
            List<String> won = winners.stream().filter(w -> w.winnerProductId().equals(pc.productId()) && w.margin() >= 0.1)
                    .map(w -> phrase(w.aspect())).toList();
            if (!won.isEmpty()) parts.add(shortName(pc.name()) + " leads on " + joinList(won));
        }
        if (parts.isEmpty()) return "Customers perceive these products similarly across the compared aspects.";
        ProductComparison best = list.stream().max(Comparator.comparingDouble(ProductComparison::positivePct)).orElseThrow();
        return String.join("; ", parts) + ". Overall, " + shortName(best.name()) + " has the highest share of positive reviews ("
                + best.positivePct() + "%).";
    }
}
