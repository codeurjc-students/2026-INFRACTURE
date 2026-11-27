package es.codeurjc.infracture.demo;

import es.codeurjc.infracture.demo.ItemStore.Item;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// The controlled API of DATABASE_CRUD: read, create and update items
@RestController
@RequestMapping("/items")
@ConditionalOnProperty(name = "APP_MODE", havingValue = "DATABASE_CRUD")
public class ItemController {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_QUANTITY = 1_000_000;

    private final ItemStore store;

    public ItemController(ItemStore store) {
        this.store = store;
    }

    // Before the server accepts requests: a database the service cannot prepare stops it, so the node never
    // gets healthy.
    @PostConstruct
    void prepareDatabase() {
        store.initialize();
    }

    @GetMapping("/{id}")
    public Item read(@PathVariable long id) {
        return store.find(id).orElseThrow(() -> notFound(id));
    }

    @PostMapping
    public ResponseEntity<Item> create(@RequestBody ItemRequest request) {
        validate(request);
        Item item = store.create(request.name(), request.quantity());
        return ResponseEntity.created(URI.create("/items/" + item.id())).body(item);
    }

    @PutMapping("/{id}")
    public Item update(@PathVariable long id, @RequestBody ItemRequest request) {
        validate(request);
        return store.update(id, request.name(), request.quantity()).orElseThrow(() -> notFound(id));
    }

    private static void validate(ItemRequest request) {
        if (request.name() == null || request.name().isBlank() || request.name().length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "name must have between 1 and %d characters".formatted(MAX_NAME_LENGTH));
        }
        if (request.quantity() == null || request.quantity() < 0 || request.quantity() > MAX_QUANTITY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "quantity must be between 0 and %d".formatted(MAX_QUANTITY));
        }
    }

    private static ResponseStatusException notFound(long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Item %d does not exist".formatted(id));
    }

    public record ItemRequest(String name, Integer quantity) {
    }
}
