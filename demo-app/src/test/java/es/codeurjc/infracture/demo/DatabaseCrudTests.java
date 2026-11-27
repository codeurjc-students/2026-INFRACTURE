package es.codeurjc.infracture.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// DATABASE_CRUD against a real PostgreSQL, configured the way the execution plan configures it.
@SpringBootTest(properties = {
        "APP_ROLE=http", "APP_MODE=DATABASE_CRUD", "COMPONENT_NAME=orders-api", "RESPONSE_DELAY_MS=0" })
@AutoConfigureMockMvc
@Testcontainers
class DatabaseCrudTests {

    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:18.7-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("DATABASE_URL", () -> DATABASE.getJdbcUrl().substring("jdbc:".length()));
        registry.add("DATABASE_USER", DATABASE::getUsername);
        registry.add("DATABASE_PASSWORD", DATABASE::getPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ItemStore store;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void startsWithTheFixedItemsAndReadsThem() throws Exception {
        mvc.perform(get("/items/7")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("item-007"))
                .andExpect(jsonPath("$.quantity").value(70))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.updatedAt").isString());
        mvc.perform(get("/items/100")).andExpect(jsonPath("$.name").value("item-100"));
    }

    @Test
    void createsAnItemWithAnIdAfterTheFixedOnesAndReadsItBack() throws Exception {
        String location = mvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"new crate\",\"quantity\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("new crate"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).matches("/items/[0-9]+");
        assertThat(Long.parseLong(location.substring("/items/".length()))).isGreaterThan(ItemStore.INITIAL_ITEMS);
        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("new crate"));
    }

    @Test
    void updatesAnItemAndRaisesItsVersion() throws Exception {
        mvc.perform(put("/items/3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"item-003\",\"quantity\":1000000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(1000000))
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(get("/items/3")).andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void answersAMissingItemWithAProblem() throws Exception {
        mvc.perform(get("/items/999999")).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Item 999999 does not exist"));
        mvc.perform(put("/items/999999").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"quantity\":1}")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidItemsWithAProblemAndStoresNothing() throws Exception {
        long before = store.count();
        for (String body : new String[] { "{\"name\":\"\",\"quantity\":1}", "{\"name\":\" \",\"quantity\":1}",
                "{\"name\":\"" + "x".repeat(101) + "\",\"quantity\":1}", "{\"quantity\":1}",
                "{\"name\":\"crate\",\"quantity\":-1}", "{\"name\":\"crate\",\"quantity\":1000001}",
                "{\"name\":\"crate\"}" }) {
            mvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + "x".repeat(100) + "\",\"quantity\":1}")).andExpect(status().isCreated());

        assertThat(store.count()).isEqualTo(before + 1);
    }

    // The evidence the backend stores: the answer of GET / counts the items, so it proves a real read.
    @Test
    void answersWithItsProfileAndTheNumberOfItems() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DATABASE_CRUD"))
                .andExpect(jsonPath("$.items").value(store.count()));
    }

    @Test
    void includesTheDatabaseInItsHealth() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    void preparesASharedDatabaseOnceWithoutUndoingChanges() throws Exception {
        store.update(5, "renamed", 1);
        try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
            Callable<Void> prepare = () -> {
                store.initialize();
                return null;
            };
            for (Future<Void> done : threads.invokeAll(List.of(prepare, prepare))) {
                done.get();
            }
        }

        assertThat(jdbc.sql("SELECT count(*) FROM items WHERE id <= 100").query(Long.class).single())
                .isEqualTo(ItemStore.INITIAL_ITEMS);
        assertThat(store.find(5)).hasValueSatisfying(item -> assertThat(item.name()).isEqualTo("renamed"));
    }
}
