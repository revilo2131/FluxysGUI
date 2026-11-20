import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regenerates the GUI sprites (anti-aliased rounded shapes) into src/main/resources.
 * Run from the project root: java tools/GenSprites.java
 */
class GenSprites {
	static final Path OUT = Path.of("src/main/resources/assets/cleanui/textures/gui/sprites");
	static final int RADIUS = 5;

	public static void main(String[] args) throws Exception {
		// Tintable white shapes: Panel and Button color them per draw.
		write("panel", 16, 16, (x, y) -> argb(0xFFFFFF, cover(x, y, 0, 0, 16, 16, RADIUS)), nineSlice(16, 16, 6, false));
		write("outline", 16, 16, (x, y) -> argb(0xFFFFFF, ring(x, y, 0, 0, 16, 16, RADIUS)), nineSlice(16, 16, 6, false));
		// Soft shadow: drawn 8px larger than the panel on every side.
		write("shadow", 32, 32, (x, y) -> argb(0x000000, 0.45f * soft(x, y, 8, 8, 24, 24, 6f)), nineSlice(32, 32, 12, false));
		// Tooltip style "cleanui:clean". Vanilla insets the box 8px into the sprite (see vanilla tooltip/background.png).
		write("tooltip/clean_background", 100, 100, (x, y) -> argb(0x14171D, 0.97f * cover(x, y, 8, 8, 92, 92, 4)), nineSlice(100, 100, 9, false));
		write("tooltip/clean_frame", 100, 100, (x, y) -> argb(0xFFFFFF, 0.12f * ring(x, y, 8, 8, 92, 92, 4)), nineSlice(100, 100, 10, true));

		// Icons: white 48×48 glyphs, drawn at 24×24 GUI px and tinted per draw.
		// shortcut: 2× source is crisp at GUI scale 2 and 4, slightly uneven at 3; switch to per-scale sizes if that shows.
		icon("icon/calendar", union(
				stroke(rrect(6, 9, 42, 42, 5), 3),
				rrect(6, 9, 42, 18, 5), rrect(6, 14, 42, 19, 0),        // header band
				rrect(14, 4, 18, 13, 2), rrect(30, 4, 34, 13, 2),       // binder rings
				circle(16, 27, 2.5f), circle(24, 27, 2.5f), circle(32, 27, 2.5f),
				circle(16, 35, 2.5f), circle(24, 35, 2.5f)));
		icon("icon/skills", union(
				rrect(7, 28, 15, 42, 2), rrect(20, 18, 28, 42, 2), rrect(33, 7, 41, 42, 2)));
		icon("icon/storage", union(
				stroke(rrect(5, 10, 43, 41, 5), 3),
				rrect(5, 20, 43, 23, 0),                               // lid seam
				rrect(20, 18, 28, 28, 2)));                            // latch
	}

	// --- icon shapes as signed distance functions (negative = inside) ---

	interface Sdf { float d(float x, float y); }

	static Sdf rrect(float x0, float y0, float x1, float y1, float r) {
		return (x, y) -> dist(x, y, x0, y0, x1, y1, Math.max(r, 0.001f));
	}

	static Sdf circle(float cx, float cy, float r) {
		return (x, y) -> (float) Math.hypot(x - cx, y - cy) - r;
	}

	static Sdf stroke(Sdf s, float width) {
		return (x, y) -> Math.abs(s.d(x, y) + width / 2) - width / 2; // stroke sits inside the shape's edge
	}

	static Sdf union(Sdf... parts) {
		return (x, y) -> {
			float d = Float.MAX_VALUE;
			for (Sdf p : parts) d = Math.min(d, p.d(x, y));
			return d;
		};
	}

	static void icon(String name, Sdf s) throws Exception {
		write(name, 48, 48, (x, y) -> {
			int hits = 0;
			for (int sy = 0; sy < 4; sy++) for (int sx = 0; sx < 4; sx++)
				if (s.d(x + (sx + 0.5f) / 4, y + (sy + 0.5f) / 4) <= 0) hits++;
			return argb(0xFFFFFF, hits / 16f);
		}, "{}\n");
	}

	interface Pixel { int at(int x, int y); }

	static void write(String name, int w, int h, Pixel p, String mcmeta) throws Exception {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, p.at(x, y));
		Path png = OUT.resolve(name + ".png");
		Files.createDirectories(png.getParent());
		ImageIO.write(img, "png", png.toFile());
		Files.writeString(OUT.resolve(name + ".png.mcmeta"), mcmeta);
		System.out.println("wrote " + png);
	}

	static String nineSlice(int w, int h, int border, boolean stretchInner) {
		return """
				{ "gui": { "scaling": { "type": "nine_slice", "width": %d, "height": %d, "border": %d%s } } }
				""".formatted(w, h, border, stretchInner ? ", \"stretch_inner\": true" : "");
	}

	static int argb(int rgb, float a) {
		return Math.round(Math.clamp(a, 0f, 1f) * 255) << 24 | rgb;
	}

	/** Fraction of pixel (x,y) inside the rounded rect [x0,x1)×[y0,y1), 4×4 supersampled. */
	static float cover(int x, int y, float x0, float y0, float x1, float y1, float r) {
		int hits = 0;
		for (int sy = 0; sy < 4; sy++) for (int sx = 0; sx < 4; sx++)
			if (dist(x + (sx + 0.5f) / 4, y + (sy + 0.5f) / 4, x0, y0, x1, y1, r) <= 0) hits++;
		return hits / 16f;
	}

	/** 1px border of the rounded rect. */
	static float ring(int x, int y, float x0, float y0, float x1, float y1, float r) {
		return cover(x, y, x0, y0, x1, y1, r) - cover(x, y, x0 + 1, y0 + 1, x1 - 1, y1 - 1, r - 1);
	}

	/** Shadow falloff outside the rounded rect. */
	static float soft(int x, int y, float x0, float y0, float x1, float y1, float spread) {
		float d = dist(x + 0.5f, y + 0.5f, x0, y0, x1, y1, RADIUS);
		return d <= 0 ? 1f : (float) Math.exp(-(d * d) / (2 * (spread / 2.5f) * (spread / 2.5f)));
	}

	/** Signed distance from a point to a rounded rect (negative inside). */
	static float dist(float px, float py, float x0, float y0, float x1, float y1, float r) {
		float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
		float qx = Math.abs(px - cx) - ((x1 - x0) / 2 - r), qy = Math.abs(py - cy) - ((y1 - y0) / 2 - r);
		return (float) Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r;
	}
}
