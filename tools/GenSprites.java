import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regenerates the tooltip style sprites into src/main/resources: navy background, cyan
 * frame with a dim inner line and pixel ornaments in the corners (same look as Theme.panel).
 * Everything else is drawn in code. Run from the project root: java tools/GenSprites.java
 */
class GenSprites {
	static final Path OUT = Path.of("src/main/resources/assets/cleanui/textures/gui/sprites/tooltip");
	// Vanilla insets the tooltip box 8px into these 100×100 sprites (see vanilla tooltip/background.png).
	static final int IN = 8, SIZE = 100;
	static final int PANEL = 0x0C1524, FRAME = 0x5ED6EA, FRAME_DIM = 0x2C7488;
	static final String[] ORNAMENT = {"XXXX.", "X....", "X.XX.", "X.X..", "....."};

	public static void main(String[] args) throws Exception {
		write("clean_background", (x, y) -> inside(x, y, IN) ? argb(PANEL, 0.97f) : 0, 9, false);
		// corners must hold the ornament (inset 11..15), so the frame's nine-slice border is 16
		write("clean_frame", GenSprites::frame, 16, true);
	}

	static int frame(int x, int y) {
		if (inside(x, y, IN) && !inside(x, y, IN + 1)) return argb(FRAME, 1f);
		if (inside(x, y, IN + 2) && !inside(x, y, IN + 3)) return argb(FRAME_DIM, 0.55f);
		int ox = Math.min(x, SIZE - 1 - x) - (IN + 3), oy = Math.min(y, SIZE - 1 - y) - (IN + 3); // mirrored per corner
		if (ox >= 0 && oy >= 0 && oy < ORNAMENT.length && ox < ORNAMENT[oy].length() && ORNAMENT[oy].charAt(ox) == 'X')
			return argb(FRAME, 1f);
		return 0;
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
