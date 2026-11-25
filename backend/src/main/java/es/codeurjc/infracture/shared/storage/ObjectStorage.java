package es.codeurjc.infracture.shared.storage;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Keeps files such as images under a key chosen by the backend. The rest of the code depends on this
// interface, never on the S3 server behind it (ADR 0001 and ADR 0033). Every operation throws
// StorageUnavailableException when the server cannot complete it.
public interface ObjectStorage {

    void put(String key, byte[] content, String contentType);

    Optional<StoredObject> get(String key);

    // Deleting a key that does not exist is not an error.
    void delete(String key);

    List<String> keys(String prefix);

    // A record compares and prints an array by reference; these use the bytes, and never print them.
    record StoredObject(byte[] content, String contentType) {

        @Override
        public boolean equals(Object other) {
            return other instanceof StoredObject that && Arrays.equals(content, that.content)
                    && Objects.equals(contentType, that.contentType);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(content) + Objects.hashCode(contentType);
        }

        @Override
        public String toString() {
            return "StoredObject[%d bytes, %s]".formatted(content.length, contentType);
        }
    }
}
