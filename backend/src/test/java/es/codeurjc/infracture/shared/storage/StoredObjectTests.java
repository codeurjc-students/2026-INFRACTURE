package es.codeurjc.infracture.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import es.codeurjc.infracture.shared.storage.ObjectStorage.StoredObject;
import org.junit.jupiter.api.Test;

class StoredObjectTests {

    @Test
    void twoObjectsWithTheSameBytesAndTypeAreEqual() {
        StoredObject first = new StoredObject(new byte[] { 1, 2, 3 }, "image/png");
        StoredObject second = new StoredObject(new byte[] { 1, 2, 3 }, "image/png");

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    void differentBytesOrTypesAreNotEqual() {
        StoredObject image = new StoredObject(new byte[] { 1, 2, 3 }, "image/png");

        assertThat(image)
                .isNotEqualTo(new StoredObject(new byte[] { 1, 2, 4 }, "image/png"))
                .isNotEqualTo(new StoredObject(new byte[] { 1, 2, 3 }, "image/jpeg"))
                .isNotEqualTo(new StoredObject(new byte[] { 1, 2, 3 }, null))
                .isNotEqualTo("not an object");
    }

    @Test
    void printsTheSizeInsteadOfTheBytes() {
        assertThat(new StoredObject(new byte[2048], "image/png"))
                .hasToString("StoredObject[2048 bytes, image/png]");
    }
}
