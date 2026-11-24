package es.codeurjc.infracture.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.codeurjc.infracture.identity.TestImages;
import es.codeurjc.infracture.shared.InvalidDataException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AvatarImageTests {

    private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' };

    @Test
    void convertsAJpegIntoAPngOfTheSameSize() {
        byte[] png = AvatarImage.toPng(TestImages.jpeg(300, 300));

        assertThat(Arrays.copyOf(png, 8)).isEqualTo(PNG_SIGNATURE);
        BufferedImage image = TestImages.decode(png);
        assertThat(image.getWidth()).isEqualTo(300);
        assertThat(image.getHeight()).isEqualTo(300);
    }

    @ParameterizedTest
    @ValueSource(ints = { AvatarImage.MIN_SIDE, 512, AvatarImage.MAX_SIDE })
    void acceptsSquarePngsWithinTheAllowedSides(int side) {
        assertThat(TestImages.decode(AvatarImage.toPng(TestImages.png(side, side))).getWidth()).isEqualTo(side);
    }

    @Test
    void dropsTheMetadataOfTheUploadedFile() {
        byte[] withLocation = withTextChunk(TestImages.png(200, 200), "GPSLatitude", "40.4168 N");

        byte[] stored = AvatarImage.toPng(withLocation);

        assertThat(new String(withLocation, StandardCharsets.ISO_8859_1)).contains("GPSLatitude");
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("GPSLatitude");
    }

    @Test
    void rejectsAnEmptyFile() {
        assertRejected(new byte[0], "The avatar is empty");
        assertRejected(null, "The avatar is empty");
    }

    @Test
    void rejectsAFileOverTwoMegabytes() {
        assertRejected(new byte[AvatarImage.MAX_BYTES + 1], "The avatar must not exceed 2 MB");
    }

    @Test
    void rejectsContentThatIsNotAPngOrJpeg() {
        assertRejected("<svg onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8),
                "The avatar must be a PNG or JPEG image");
        assertRejected(TestImages.gif(200, 200), "The avatar must be a PNG or JPEG image");
        assertRejected(Arrays.copyOf(TestImages.png(200, 200), 60), "The avatar must be a PNG or JPEG image");
    }

    @Test
    void rejectsAnImageThatIsNotSquare() {
        assertRejected(TestImages.png(300, 200), "The avatar must be square");
    }

    @ParameterizedTest
    @ValueSource(ints = { AvatarImage.MIN_SIDE - 1, AvatarImage.MAX_SIDE + 1 })
    void rejectsSidesOutsideTheAllowedRange(int side) {
        assertRejected(TestImages.png(side, side), "The avatar must be between 128 and 1024 pixels wide");
    }

    // A few bytes can declare a huge image; the header check rejects it before decoding any pixel.
    @Test
    void rejectsAHugeDeclaredSizeWithoutDecodingIt() {
        byte[] header = pngHeaderOnly(30_000, 30_000);

        assertThat(header.length).isLessThan(100);
        assertRejected(header, "The avatar must be between 128 and 1024 pixels wide");
    }

    private static void assertRejected(byte[] content, String message) {
        assertThatThrownBy(() -> AvatarImage.toPng(content))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage(message);
    }

    private static byte[] pngHeaderOnly(int width, int height) {
        ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.writeBytes(PNG_SIGNATURE);
        png.writeBytes(chunk("IHDR", ihdr.array()));
        return png.toByteArray();
    }

    // Inserts a tEXt chunk right after IHDR (signature 8 bytes + IHDR chunk 25 bytes).
    private static byte[] withTextChunk(byte[] png, String keyword, String text) {
        byte[] data = (keyword + "\0" + text).getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(png, 0, 33);
        result.writeBytes(chunk("tEXt", data));
        result.write(png, 33, png.length - 33);
        return result.toByteArray();
    }

    private static byte[] chunk(String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        return ByteBuffer.allocate(12 + data.length).putInt(data.length).put(typeBytes).put(data)
                .putInt((int) crc.getValue()).array();
    }
}
