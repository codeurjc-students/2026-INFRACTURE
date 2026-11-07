package es.codeurjc.infracture.demo;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "APP_ROLE=http", "APP_MODE=STATELESS", "COMPONENT_NAME=orders-api", "RESPONSE_DELAY_MS=10" })
@AutoConfigureMockMvc
class DemoApplicationTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void answersWithItsComponentAndProfile() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("orders-api"))
                .andExpect(jsonPath("$.role").value("http"))
                .andExpect(jsonPath("$.mode").value("STATELESS"))
                .andExpect(jsonPath("$.responseDelayMs").value(10));
    }

    @Test
    void reportsItsHealthAtTheRootPath() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void refusesToStartWithAnUnsupportedProfile() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new DemoController("http", "CACHE_ASIDE", "orders-api", 0))
                .withMessageContaining("APP_MODE=CACHE_ASIDE");
        assertThatIllegalStateException()
                .isThrownBy(() -> new DemoController("worker", "STATELESS", "orders-api", 0));
        assertThatIllegalStateException()
                .isThrownBy(() -> new DemoController("http", "STATELESS", "orders-api", -1));
    }
}
