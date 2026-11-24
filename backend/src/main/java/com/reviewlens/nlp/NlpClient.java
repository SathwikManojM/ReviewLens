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

/** HTTP client for the Python FastAPI NLP service (nlp-engine/app.py). */
@Component
public class NlpClient {

    public record ReviewIn(String id, String text, Double rating) {}
    public record AspectOut(String aspect, String term, String sentiment, double score, String snippet, int mentions) {}
    public record ReviewOut(String id, String cleanText, String overallSentiment, double overallScore, List<AspectOut> aspects) {}
    public record AnalyzeResponse(List<ReviewOut> results, Map<String, Object> engine) {}
    public record Keyword(String term, int frequency, double score) {}
    public record KeywordResponse(List<Keyword> keywords, List<Keyword> praise, List<Keyword> concerns) {}
    public record Health(String status, List<String> aspects, Map<String, Object> engine) {}

    private final RestClient client;
    private final RestClient healthClient;
    private volatile String engineLabel = "rule-based-vader";

    public NlpClient(@Value("${reviewlens.nlp.url}") String baseUrl) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(5));
        rf.setReadTimeout(Duration.ofMinutes(3));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(rf).build();
        SimpleClientHttpRequestFactory hf = new SimpleClientHttpRequestFactory();
        hf.setConnectTimeout(Duration.ofSeconds(2));
        hf.setReadTimeout(Duration.ofSeconds(4));
        this.healthClient = RestClient.builder().baseUrl(baseUrl).requestFactory(hf).build();
    }

    public List<ReviewOut> analyze(List<ReviewIn> reviews) {
        try {
            AnalyzeResponse r = client.post().uri("/analyze").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("reviews", reviews)).retrieve().body(AnalyzeResponse.class);
            if (r == null) return List.of();
            rememberEngine(r.engine());
            return r.results();
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    public KeywordResponse keywords(List<String> texts, int topN) {
        try {
            return client.post().uri("/keywords").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("texts", texts, "topN", topN)).retrieve().body(KeywordResponse.class);
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    /** Throws if the service is unreachable. */
    public Health health() {
        Health h = healthClient.get().uri("/health").retrieve().body(Health.class);
        if (h != null) rememberEngine(h.engine());
        return h;
    }

    public boolean isUp() {
        try {
            health();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** e.g. "rule-based-vader 1.1.0" - stored on jobs and datasets to tell baseline results from future models. */
    public String engineLabel() {
        return engineLabel;
    }

    private void rememberEngine(Map<String, Object> engine) {
        if (engine != null && engine.get("name") != null) {
            engineLabel = engine.get("name") + (engine.get("version") != null ? " " + engine.get("version") : "");
        }
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "NLP_UNAVAILABLE",
                "The NLP engine is not reachable. Start it with: uvicorn app:app --port 8000 (in nlp-engine)");
    }
}
