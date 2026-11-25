package es.codeurjc.infracture.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.codeurjc.infracture.shared.storage.ObjectStorage.StoredObject;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;

@Testcontainers
class S3ObjectStorageIntegrationTests {

    private static final String ACCESS_KEY = "storage-test";
    private static final String SECRET_KEY = "storage-test-secret";

    @Container
    static final GenericContainer<?> rustfs = new GenericContainer<>("rustfs/rustfs:1.0.6")
            .withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
            .withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/health").forPort(9000));

    private static S3Client client;

    @BeforeAll
    static void connect() {
        URI endpoint = URI.create("http://%s:%d".formatted(rustfs.getHost(), rustfs.getMappedPort(9000)));
        client = StorageConfiguration.client(endpoint, ACCESS_KEY, SECRET_KEY);
    }

    @AfterAll
    static void close() {
        client.close();
    }

    @Test
    void readsBackWhatItStoresAfterCreatingTheBucket() {
        ObjectStorage storage = newStorage();
        byte[] image = { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 };

        storage.put("avatars/1/first.png", image, "image/png");

        assertThat(storage.get("avatars/1/first.png")).hasValueSatisfying(object -> {
            assertThat(object.content()).isEqualTo(image);
            assertThat(object.contentType()).isEqualTo("image/png");
        });
    }

    @Test
    void findsNothingForAMissingKeyOrBucket() {
        ObjectStorage storage = newStorage();

        assertThat(storage.get("avatars/1/missing.png")).isEmpty();
        storage.put("avatars/1/other.png", new byte[] { 1 }, "image/png");
        assertThat(storage.get("avatars/1/missing.png")).isEmpty();
    }

    @Test
    void deletesAnObjectAndAcceptsDeletingItAgain() {
        ObjectStorage storage = newStorage();
        storage.put("avatars/1/old.png", new byte[] { 1 }, "image/png");

        storage.delete("avatars/1/old.png");

        assertThat(storage.get("avatars/1/old.png")).isEmpty();
        assertThatCode(() -> storage.delete("avatars/1/old.png")).doesNotThrowAnyException();
    }

    @Test
    void reusesABucketCreatedByAnEarlierRun() {
        String bucket = "images-" + UUID.randomUUID();
        new S3ObjectStorage(client, bucket).put("avatars/1/a.png", new byte[] { 1 }, "image/png");

        ObjectStorage restarted = new S3ObjectStorage(client, bucket);
        restarted.put("avatars/1/b.png", new byte[] { 2 }, "image/png");

        assertThat(restarted.get("avatars/1/a.png")).map(StoredObject::content).hasValue(new byte[] { 1 });
    }

    @Test
    void listsTheKeysUnderAPrefix() {
        ObjectStorage storage = newStorage();
        assertThat(storage.keys("avatars/")).isEmpty();
        storage.put("avatars/1/a.png", new byte[] { 1 }, "image/png");
        storage.put("avatars/2/b.png", new byte[] { 2 }, "image/png");
        storage.put("covers/1/c.png", new byte[] { 3 }, "image/png");

        assertThat(storage.keys("avatars/")).containsExactlyInAnyOrder("avatars/1/a.png", "avatars/2/b.png");
    }

    @Test
    void reportsAnUnreachableServerAsUnavailable() {
        try (S3Client unreachable = StorageConfiguration.client(URI.create("http://127.0.0.1:9"), ACCESS_KEY,
                SECRET_KEY)) {
            ObjectStorage storage = new S3ObjectStorage(unreachable, "images");

            assertThatThrownBy(() -> storage.put("avatars/1/a.png", new byte[] { 1 }, "image/png"))
                    .isInstanceOf(StorageUnavailableException.class);
            assertThatThrownBy(() -> storage.get("avatars/1/a.png")).isInstanceOf(StorageUnavailableException.class);
        }
    }

    // Each test gets its own bucket, so none sees the objects of another.
    private static ObjectStorage newStorage() {
        return new S3ObjectStorage(client, "images-" + UUID.randomUUID());
    }
}
