package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import es.codeurjc.infracture.execution.domain.ExecutionPlan;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Container;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Counters;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.HttpProbe;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Limits;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Node;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Wiring;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CounterScraperTests {

    private static final UUID ORDERS = UUID.fromString("3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10");
    private static final UUID STOCK = UUID.fromString("5a1d2c3b-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    private static final UUID STALLED = UUID.fromString("8d4e2f1a-3b5c-4d6e-9f8a-1b2c3d4e5f6a");
    private static final UUID UNREACHABLE = UUID.fromString("9e5f3a2b-4c6d-4e7f-8a9b-2c3d4e5f6a7b");
    private static final UUID DATABASE = UUID.fromString("7c2e4a6b-8d0f-4b1c-9e3a-5f7b9d1c3e5a");
    private static final Limits LIMITS = new Limits(0.5, 384L << 20, 200);

    @Test
    void parsesPrometheusTextAndSumsDeclaredSeriesAcrossLabels() {
        String text = """
                # HELP infracture_requests_total Total requests
                # TYPE infracture_requests_total counter
                infracture_requests_total{method="GET",path="/"} 10.0
                infracture_requests_total{method="POST",note="a \\"} label"} 5.5 1700000000000
                infracture_request_errors_total 0.0
                infracture_unrelated_total 99.0
                infracture_database_queries_total NaN
                """;
        Map<String, String> series = Map.of(
                "requests", "infracture_requests_total",
                "errors", "infracture_request_errors_total",
                "database_queries", "infracture_database_queries_total");

        assertThat(CounterScraper.parse(text, series))
                .containsOnly(Map.entry("requests", 15.5), Map.entry("errors", 0.0));
    }

    @Test
    void scrapesReachableNodesInParallelAndLeavesOutNodesThatFailStallOrRefuse() throws IOException {
        CountDownLatch bothArrived = new CountDownLatch(2);
        CountDownLatch releaseStalled = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/ok-a", exchange -> {
            bothArrived.countDown();
            awaitQuietly(bothArrived, 2);
            reply(exchange, 200, "infracture_requests_total 42\ninfracture_request_errors_total 2\n");
        });
        server.createContext("/ok-b", exchange -> {
            bothArrived.countDown();
            awaitQuietly(bothArrived, 2);
            reply(exchange, 200, "infracture_requests_total 7\n");
        });
        server.createContext("/stalled-body", exchange -> {
            // Sends headers (200) and withholds the body, exercising the body-read timeout.
            exchange.sendResponseHeaders(200, 100);
            exchange.getResponseBody().flush();
            awaitQuietly(releaseStalled, 5);
            exchange.close();
        });
        server.createContext("/down", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        CounterScraper scraper = new CounterScraper(Duration.ofMillis(400));
        try {
            int port = server.getAddress().getPort();
            Map<String, String> series = Map.of(
                    "requests", "infracture_requests_total",
                    "errors", "infracture_request_errors_total");
            ExecutionPlan plan = new ExecutionPlan("p", Map.of(), List.of(
                    node(ORDERS, new Counters(URI.create("http://127.0.0.1:" + port + "/ok-a"), series)),
                    node(STOCK, new Counters(URI.create("http://127.0.0.1:" + port + "/ok-b"), series)),
                    node(STALLED, new Counters(URI.create("http://127.0.0.1:" + port + "/stalled-body"), series)),
                    node(UNREACHABLE, new Counters(URI.create("http://127.0.0.1:9/metrics"), series)),
                    node(DATABASE, null)), null);

            Map<UUID, Map<String, Double>> readings = scraper.scrape(plan);

            // Both /ok-a and /ok-b met at the latch, proving the scraper sent both requests in parallel.
            assertThat(bothArrived.getCount()).isZero();
            assertThat(readings).containsOnlyKeys(ORDERS, STOCK);
            assertThat(readings.get(ORDERS)).containsOnly(Map.entry("requests", 42.0), Map.entry("errors", 2.0));
            assertThat(readings.get(STOCK)).containsOnly(Map.entry("requests", 7.0));
        } finally {
            releaseStalled.countDown();
            scraper.close();
            server.stop(0);
        }
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void awaitQuietly(CountDownLatch latch, long seconds) {
        try {
            latch.await(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private static Node node(UUID key, Counters counters) {
        return new Node(key, "c-" + key, new Container("image", null, null, List.of()),
                new Wiring(Map.of(), Map.of(), List.of()), new HttpProbe(8080, "/health", 2, 2, 15, 60), null, counters,
                LIMITS);
    }
}
