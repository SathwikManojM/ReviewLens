package com.reviewlens.nlp;

import com.reviewlens.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** HTTP client for the Python NLP engine (nlp-engine/app.py). */
@Component
public class NlpClient {

    public record ReviewIn(Long id, String text, Double rating) {}
    public record AspectOut(String aspect, String term, String sentiment, double score, String snippet, int mentions) {}
    public record ReviewOut(Long id, String cleanText, String overallSentiment, double overallScore, List<AspectOut> aspects) {}
    public record AnalyzeResponse(List<ReviewOut> results) {}
    public record Keyword(String term, int frequency, double score) {}
    public record KeywordResponse(List<Keyword> keywords, List<Keyword> praise, List<Keyword> concerns) {}

    private final RestClient client;
    private final String baseUrl;

    public NlpClient(@Value("${reviewlens.nlp.url}") String baseUrl) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(5));
        rf.setReadTimeout(Duration.ofMinutes(3));
        this.baseUrl = baseUrl;
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(rf).build();
    }

    public List<ReviewOut> analyze(List<ReviewIn> reviews) {
        try {
            AnalyzeResponse r = client.post().uri("/analyze").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("reviews", reviews)).retrieve().body(AnalyzeResponse.class);
            return r == null ? List.of() : r.results();
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    public KeywordResponse keywords(List<String> texts, int topN) {
        try {
            return client.post().uri("/keywords").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("texts", texts, "topN", topN)).retrieve().body(KeywordResponse.class);
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    public boolean isUp() {
        try {
            client.get().uri("/health").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    private ApiException unavailable(RestClientException e) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "NLP engine is not reachable at " + baseUrl + " - start it with: uvicorn app:app --port 8000 (" + e.getMessage() + ")");
    }
}
