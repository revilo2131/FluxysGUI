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
	static final String[] ORNAMENT = {"XXXX.", "X....", "X.XX.", "X.X..", "....."};
	/**
	 * id, tooltip background, background alpha, frame, frame dim, frame style — keep in sync with
	 * Theme.PRESETS. Tooltips stay dark in every theme (Parchment's is dark brown): Hypixel's lore
	 * colors are made for a dark background.
	 */
	static final Object[][] PRESETS = {
			{"cyan", 0x0C1524, 0.97f, 0x5ED6EA, 0x2C7488, "ORNATE"}, {"gold", 0x0C1524, 0.97f, 0xD9A441, 0x7A5A22, "ORNATE"},
			{"amethyst", 0x0C1524, 0.97f, 0xB98CF5, 0x5E3E8C, "ORNATE"}, {"emerald", 0x0C1524, 0.97f, 0x5ED68A, 0x2C7A4E, "ORNATE"},
			{"crimson", 0x0C1524, 0.97f, 0xF0606A, 0x7E2A33, "ORNATE"},
			{"midnight", 0x0E0F13, 0.97f, 0xC9CEDB, 0x4A505E, "CLEAN"}, {"neon", 0x07060D, 0.97f, 0xFF3FD8, 0x7A1E6A, "BOLD"},
			{"glass", 0x10202E, 0.85f, 0xBFE9FF, 0x4E7A96, "CLEAN"}, {"parchment", 0x2B2116, 0.97f, 0xB08E5E, 0x6E5B40, "ORNATE"}};

	public static void main(String[] args) throws Exception {
		for (Object[] p : PRESETS) {
			String id = (String) p[0], style = (String) p[5];
			int panel = (int) p[1], frame = (int) p[3], dim = (int) p[4];
			float alpha = (float) p[2];
			write("clean_" + id + "_background", (x, y) -> inside(x, y, IN) ? argb(panel, alpha) : 0, 9, false);
			// corners must hold the ornament (inset 11..15), so the frame's nine-slice border is 16
			write("clean_" + id + "_frame", (x, y) -> frame(x, y, frame, dim, style), 16, true);
		}
	}

	static int frame(int x, int y, int frame, int dim, String style) {
		if (inside(x, y, IN) && !inside(x, y, IN + 1)) return argb(frame, 1f);
		if (style.equals("BOLD") && inside(x, y, IN + 1) && !inside(x, y, IN + 2)) return argb(frame, 1f);
		if (!style.equals("ORNATE")) return 0;
		if (inside(x, y, IN + 2) && !inside(x, y, IN + 3)) return argb(dim, 0.55f);
		int ox = Math.min(x, SIZE - 1 - x) - (IN + 3), oy = Math.min(y, SIZE - 1 - y) - (IN + 3); // mirrored per corner
		if (ox >= 0 && oy >= 0 && oy < ORNAMENT.length && ox < ORNAMENT[oy].length() && ORNAMENT[oy].charAt(ox) == 'X')
			return argb(frame, 1f);
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
