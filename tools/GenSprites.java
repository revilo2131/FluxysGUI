import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regenerates the tooltip style sprites (square, gold 1px frame) into src/main/resources.
 * Everything else is drawn in code. Run from the project root: java tools/GenSprites.java
 */
class GenSprites {
	static final Path OUT = Path.of("src/main/resources/assets/cleanui/textures/gui/sprites/tooltip");
	// Vanilla insets the tooltip box 8px into these 100×100 sprites (see vanilla tooltip/background.png).
	static final int IN = 8, SIZE = 100;

	public static void main(String[] args) throws Exception {
		write("clean_background", (x, y) -> inside(x, y, IN) ? argb(0x18191D, 0.96f) : 0, 9, false);
		write("clean_frame", (x, y) -> inside(x, y, IN) && !inside(x, y, IN + 1) ? argb(0xC8902E, 0.9f) : 0, 10, true);
	}

	interface Pixel { int at(int x, int y); }

	static boolean inside(int x, int y, int inset) {
		return x >= inset && y >= inset && x < SIZE - inset && y < SIZE - inset;
	}

	static void write(String name, Pixel p, int border, boolean stretchInner) throws Exception {
		BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) img.setRGB(x, y, p.at(x, y));
		Files.createDirectories(OUT);
		ImageIO.write(img, "png", OUT.resolve(name + ".png").toFile());
		Files.writeString(OUT.resolve(name + ".png.mcmeta"), """
				{ "gui": { "scaling": { "type": "nine_slice", "width": %d, "height": %d, "border": %d%s } } }
				""".formatted(SIZE, SIZE, border, stretchInner ? ", \"stretch_inner\": true" : ""));
		System.out.println("wrote " + name);
	}

	static int argb(int rgb, float a) {
		return Math.round(a * 255) << 24 | rgb;
	}
}
