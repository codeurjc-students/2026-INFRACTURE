package es.codeurjc.infracture.identity;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

// Real encoded images for avatar tests.
public final class TestImages {

    private TestImages() {
    }

    public static byte[] png(int width, int height) {
        return encode(width, height, "png", BufferedImage.TYPE_INT_ARGB);
    }

    public static byte[] jpeg(int width, int height) {
        return encode(width, height, "jpeg", BufferedImage.TYPE_INT_RGB);
    }

    public static byte[] gif(int width, int height) {
        return encode(width, height, "gif", BufferedImage.TYPE_INT_RGB);
    }

    public static BufferedImage decode(byte[] content) {
        try {
            return ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] encode(int width, int height, String format, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0x2f5d8a));
        graphics.fillRect(0, 0, width, height / 2);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, output);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return output.toByteArray();
    }
}
