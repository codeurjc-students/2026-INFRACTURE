package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.execution.domain.ExecutionPlan;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Counters;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Node;
import jakarta.annotation.PreDestroy;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// Reads the Prometheus counters declared by each node of an execution plan. All nodes are asked at once so one
// slow or unreachable service does not delay the rest; a node that does not answer is simply left out.
@Component
public class CounterScraper {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    private final Duration timeout;
    private final HttpClient client;

    @Autowired
    public CounterScraper() {
        this(DEFAULT_TIMEOUT);
    }

    CounterScraper(Duration timeout) {
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @PreDestroy
    void close() {
        client.close();
    }

    public Map<UUID, Map<String, Double>> scrape(ExecutionPlan plan) {
        Map<UUID, CompletableFuture<Map<String, Double>>> requests = new LinkedHashMap<>();
        for (Node node : plan.nodes()) {
            Counters counters = node.counters();
            if (counters == null || counters.series().isEmpty()) {
                continue;
            }
            HttpRequest request = HttpRequest.newBuilder(counters.endpoint()).timeout(timeout).GET().build();
            requests.put(node.key(), client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> response.statusCode() / 100 == 2
                            ? parse(response.body(), counters.series())
                            : null)
                    .completeOnTimeout(null, timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .exceptionally(_ -> null));
        }
        if (requests.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<String, Double>> readings = new LinkedHashMap<>();
        requests.forEach((key, future) -> {
            Map<String, Double> values = future.join();
            if (values != null) {
                readings.put(key, values);
            }
        });
        return readings;
    }

    // Sums each declared Prometheus series across all its label sets. A series absent from the response stays
    // absent rather than reading as zero.
    static Map<String, Double> parse(String body, Map<String, String> series) {
        Map<String, Double> totals = new HashMap<>();
        var wanted = series.values();
        body.lines().forEach(raw -> {
            String line = raw.stripLeading();
            if (line.isEmpty() || line.charAt(0) == '#') {
                return;
            }
            int nameEnd = findMetricNameEnd(line);
            if (nameEnd <= 0) {
                return;
            }
            String metric = line.substring(0, nameEnd);
            if (!wanted.contains(metric)) {
                return;
            }
            int valueStart = skipLabelsAndSpace(line, nameEnd);
            if (valueStart < 0) {
                return;
            }
            Double value = parseValue(line, valueStart);
            if (value != null) {
                totals.merge(metric, value, Double::sum);
            }
        });
        Map<String, Double> result = new LinkedHashMap<>();
        series.forEach((name, metric) -> {
            Double total = totals.get(metric);
            if (total != null) {
                result.put(name, total);
            }
        });
        return result;
    }

    private static int findMetricNameEnd(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '{' || c == ' ' || c == '\t') {
                return i;
            }
        }
        return -1;
    }

    private static int skipLabelsAndSpace(String line, int index) {
        int i = index;
        if (i < line.length() && line.charAt(i) == '{') {
            i = findClosingBrace(line, i + 1);
            if (i < 0) {
                return -1;
            }
        }
        int spaceStart = i;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return i > spaceStart && i < line.length() ? i : -1;
    }

    private static int findClosingBrace(String line, int start) {
        boolean inQuotes = false;
        boolean escaped = false;
        for (int i = start; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inQuotes = false;
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == '}') {
                return i + 1;
            }
        }
        return -1;
    }

    private static Double parseValue(String line, int start) {
        int end = start;
        while (end < line.length() && line.charAt(end) != ' ' && line.charAt(end) != '\t') {
            end++;
        }
        try {
            double value = Double.parseDouble(line.substring(start, end));
            return Double.isFinite(value) && value >= 0 ? value : null;
        } catch (NumberFormatException _) {
            return null;
        }
    }
}
