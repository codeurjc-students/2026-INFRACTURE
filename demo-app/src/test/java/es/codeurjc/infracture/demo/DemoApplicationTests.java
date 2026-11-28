package es.codeurjc.infracture.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "APP_ROLE=http", "APP_MODE=STATELESS", "COMPONENT_NAME=orders-api", "RESPONSE_DELAY_MS=10" })
@AutoConfigureMockMvc
@AutoConfigureMetrics
class DemoApplicationTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void answersWithItsComponentAndProfile() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("orders-api"))
                .andExpect(jsonPath("$.role").value("http"))
                .andExpect(jsonPath("$.mode").value("STATELESS"))
                .andExpect(jsonPath("$.responseDelayMs").value(10))
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(jsonPath("$.downstream").doesNotExist());
    }

    @Test
    void hasNoItemsWithoutADatabase() throws Exception {
        mvc.perform(get("/items/1")).andExpect(status().isNotFound());
    }

    @Test
    void reportsItsHealthAtTheRootPath() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void exposesRequestAndErrorCountersAtMetricsWithoutCountingHealth() throws Exception {
        String before = scrape();
        double requestsBefore = PrometheusText.counter(before, "infracture_requests_total").orElseThrow();
        double errorsBefore = PrometheusText.counter(before, "infracture_request_errors_total").orElseThrow();

        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/items/1")).andExpect(status().isNotFound());
        mvc.perform(get("/health")).andExpect(status().isOk());

        String after = scrape();
        assertThat(PrometheusText.counter(after, "infracture_requests_total")).hasValue(requestsBefore + 2.0);
        assertThat(PrometheusText.counter(after, "infracture_request_errors_total")).hasValue(errorsBefore + 1.0);
        assertThat(PrometheusText.counter(after, "infracture_database_queries_total")).isEmpty();
    }

    private String scrape() throws Exception {
        return mvc.perform(get("/metrics")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void refusesToStartWithAnUnsupportedProfile() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new DemoController("http", "CACHE_ASIDE", "orders-api", 0, Optional.empty(),
                        Optional.empty()))
                .withMessageContaining("APP_MODE=CACHE_ASIDE");
        assertThatIllegalStateException()
                .isThrownBy(() -> new DemoController("worker", "STATELESS", "orders-api", 0, Optional.empty(),
                        Optional.empty()));
        assertThatIllegalStateException()
                .isThrownBy(() -> new DemoController("http", "STATELESS", "orders-api", -1, Optional.empty(),
                        Optional.empty()));
    }
}
