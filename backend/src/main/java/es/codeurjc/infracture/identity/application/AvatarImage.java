package es.codeurjc.infracture.identity.application;

import es.codeurjc.infracture.shared.InvalidDataException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

// Checks an uploaded avatar and re-encodes it as PNG. Only the pixels survive, so metadata such as the
// GPS location in EXIF never reaches storage.
final class AvatarImage {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MIN_SIDE = 128;
    static final int MAX_SIDE = 1024;
    private static final Set<String> FORMATS = Set.of("png", "jpeg");

    private AvatarImage() {
    }

    static byte[] toPng(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidDataException("The avatar is empty");
        }
        if (content.length > MAX_BYTES) {
            throw new InvalidDataException("The avatar must not exceed 2 MB");
        }
        BufferedImage image = read(content);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(png)) {
            ImageIO.write(image, "png", output);
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode the avatar", e);
        }
        return png.toByteArray();
    }

    private static BufferedImage read(byte[] content) {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw notAnImage();
            }
            ImageReader reader = readers.next();
            try {
                if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw notAnImage();
                }
                reader.setInput(input, true, true);
                // The size comes from the header, before any pixel is decoded: a small file can declare a
                // huge image that would exhaust memory.
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width != height) {
                    throw new InvalidDataException("The avatar must be square");
                }
                if (width < MIN_SIDE || width > MAX_SIDE) {
                    throw new InvalidDataException(
                            "The avatar must be between %d and %d pixels wide".formatted(MIN_SIDE, MAX_SIDE));
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (InvalidDataException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw notAnImage();
        }
    }

    private static InvalidDataException notAnImage() {
        return new InvalidDataException("The avatar must be a PNG or JPEG image");
    }
}
