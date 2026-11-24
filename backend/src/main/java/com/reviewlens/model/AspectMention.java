package com.reviewlens.model;

/**
 * Compact aspect result embedded in each review (bounded: at most one entry per aspect category),
 * used by the review explorer. The full analytical record lives in the "aspectResults" collection.
 */
public record AspectMention(String aspect, String term, Sentiment sentiment, double score, String snippet) {}
