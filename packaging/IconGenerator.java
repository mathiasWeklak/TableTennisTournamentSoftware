import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the application icon (table tennis bat and ball on a blue tile) as vector graphics and
 * writes all formats needed for packaging and for the window icon.
 *
 * <p>Run from the project root: {@code java packaging/IconGenerator.java}</p>
 *
 * <ul>
 *   <li>{@code packaging/icons/app.png} (512 px, Linux)</li>
 *   <li>{@code packaging/icons/app.ico} (16-256 px, Windows)</li>
 *   <li>{@code packaging/icons/app.icns} (128-1024 px, macOS)</li>
 *   <li>{@code src/main/resources/icons/app-NN.png} (16-256 px, window icon)</li>
 * </ul>
 */
public class IconGenerator {

    public static void main(String[] args) throws IOException {
        File out = new File("packaging/icons");
        File res = new File("src/main/resources/icons");
        out.mkdirs();
        res.mkdirs();

        ImageIO.write(draw(512), "png", new File(out, "app.png"));

        int[] icoSizes = {16, 24, 32, 48, 64, 128, 256};
        writeIco(new File(out, "app.ico"), icoSizes);
        writeIcns(new File(out, "app.icns"));

        for (int size : new int[]{16, 32, 48, 64, 128, 256}) {
            ImageIO.write(draw(size), "png", new File(res, "app-" + size + ".png"));
        }
        System.out.println("Icons written to " + out.getPath() + " and " + res.getPath());
    }

    /** Draws the icon at the given size (vector graphics, so every size is sharp). */
    static BufferedImage draw(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(size / 100.0, size / 100.0); // design on a 100 x 100 grid

        // blue tile with rounded corners and a soft gradient
        RoundRectangle2D tile = new RoundRectangle2D.Double(2, 2, 96, 96, 22, 22);
        g.setPaint(new GradientPaint(0, 0, new Color(33, 150, 243), 100, 100, new Color(13, 71, 161)));
        g.fill(tile);

        // table line (a hint of the table edge)
        g.setColor(new Color(255, 255, 255, 70));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(14, 78, 86, 78));

        // bat: handle first, then the red blade on top
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(-35), 44, 54);
        g.setColor(new Color(121, 85, 72));
        g.fill(new RoundRectangle2D.Double(39, 64, 10, 26, 5, 5));
        g.setColor(new Color(215, 168, 110));
        g.fill(new RoundRectangle2D.Double(40.5, 64, 7, 24, 4, 4));
        Ellipse2D blade = new Ellipse2D.Double(21, 22, 46, 46);
        g.setColor(new Color(120, 20, 20));
        g.fill(new Ellipse2D.Double(20, 21, 48, 48));
        g.setPaint(new GradientPaint(20, 20, new Color(244, 67, 54), 66, 66, new Color(183, 28, 28)));
        g.fill(blade);
        g.setTransform(old);

        // ball with a light shading
        Ellipse2D ball = new Ellipse2D.Double(64, 14, 22, 22);
        g.setColor(new Color(0, 0, 0, 50));
        g.fill(new Ellipse2D.Double(65, 16, 22, 22));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(71, 20), 16,
                new float[]{0f, 1f}, new Color[]{Color.WHITE, new Color(225, 230, 238)}));
        g.fill(ball);

        g.dispose();
        return img;
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bytes);
        return bytes.toByteArray();
    }

    /** ICO with PNG-compressed images (supported since Windows Vista). */
    private static void writeIco(File file, int[] sizes) throws IOException {
        List<byte[]> images = new ArrayList<>();
        for (int s : sizes) images.add(png(draw(s)));
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            writeShortLE(out, 0);              // reserved
            writeShortLE(out, 1);              // type: icon
            writeShortLE(out, sizes.length);   // number of images
            int offset = 6 + 16 * sizes.length;
            for (int i = 0; i < sizes.length; i++) {
                int s = sizes[i];
                out.writeByte(s >= 256 ? 0 : s); // width (0 = 256)
                out.writeByte(s >= 256 ? 0 : s); // height
                out.writeByte(0);                // palette
                out.writeByte(0);                // reserved
                writeShortLE(out, 1);            // color planes
                writeShortLE(out, 32);           // bits per pixel
                writeIntLE(out, images.get(i).length);
                writeIntLE(out, offset);
                offset += images.get(i).length;
            }
            for (byte[] image : images) out.write(image);
        }
    }

    /** ICNS with PNG images (ic07 = 128, ic08 = 256, ic09 = 512, ic10 = 1024). */
    private static void writeIcns(File file) throws IOException {
        String[] types = {"ic07", "ic08", "ic09", "ic10"};
        int[] sizes = {128, 256, 512, 1024};
        List<byte[]> images = new ArrayList<>();
        int total = 8;
        for (int s : sizes) {
            byte[] p = png(draw(s));
            images.add(p);
            total += 8 + p.length;
        }
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.writeBytes("icns");
            out.writeInt(total); // big endian
            for (int i = 0; i < sizes.length; i++) {
                out.writeBytes(types[i]);
                out.writeInt(8 + images.get(i).length);
                out.write(images.get(i));
            }
        }
    }

    private static void writeShortLE(DataOutputStream out, int v) throws IOException {
        out.writeByte(v & 0xff);
        out.writeByte((v >> 8) & 0xff);
    }

    private static void writeIntLE(DataOutputStream out, int v) throws IOException {
        writeShortLE(out, v & 0xffff);
        writeShortLE(out, (v >> 16) & 0xffff);
    }
}
