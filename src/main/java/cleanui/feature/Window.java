package cleanui.feature;

import cleanui.CleanUi;
import cleanui.data.Config;
import cleanui.skyblock.SkyblockTime;
import cleanui.ui.Anim;
import cleanui.ui.Button;
import cleanui.ui.CleanScreen;
import cleanui.ui.Theme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

/**
 * The Clean UI window every page lives in: header (back button + breadcrumb, SkyBlock date),
 * page body, footer (hint + version). Moving between pages swaps the screen but keeps the
 * frame still: only the body slides, right when going into a page and left when coming back.
 */
public abstract class Window extends CleanScreen {
	public static final int W = 440, H = 256;
	private static final int HEADER = 24, BODY_Y = 32, FOOTER = 20;
	/** Page body size. */
	protected static final int BW = W - Theme.PAD * 2, BH = H - BODY_Y - FOOTER - 6;
	private static final String HOME = "SkyBlock Menu";
	/** Header/footer inset that clears the frame's corner ornaments. */
	private static final int EDGE = 11;
	private static final String VERSION = "v" + FabricLoader.getInstance().getModContainer(CleanUi.ID)
			.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");

	// Carried between screens so moving between pages continues from the last frame.
	private static long lastShownNanos;
	private static int lastDepth;

	private final ItemStack icon;
	private final int depth; // 0 = SkyBlock Menu overview, 1 = a page opened from it
	private final Anim page = new Anim(1f, 16f);
	private int slideDir;

	protected Window(String title, ItemStack icon, int depth) {
		super(Component.literal(title));
		this.icon = icon;
		this.depth = depth;
	}

	/** Add this page's widgets (positions relative to {@link #bx()}/{@link #by()}). */
	protected void initBody() {}

	/** Draw the page body at {@link #bx()}, {@link #by()} (size {@link #BW}×{@link #BH}). */
	protected abstract void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a);

	/** One-line hint shown in the footer. */
	protected String hint() {
		return "";
	}

	/** The header's back button (pages below the overview only). */
	protected void goBack() {}

	/** Breadcrumb before this page's title: the page Back returns to. */
	protected String crumb() {
		return HOME;
	}

	protected int left() {
		return (width - W) / 2;
	}

	protected int top() {
		return (height - H) / 2;
	}

	protected int bx() {
		return left() + Theme.PAD;
	}

	protected int by() {
		return top() + BODY_Y;
	}

	@Override
	public void added() {
		super.added();
		boolean switching = System.nanoTime() - lastShownNanos < 1_500_000_000L; // frame was just on screen
		if (switching) skipOpenAnimation();
		slideDir = Integer.signum(depth - lastDepth);
		page.snap(switching && slideDir != 0 ? 0f : 1f);
		page.target(1f);
		lastDepth = depth;
	}

	@Override
	protected final void init() {
		if (depth > 0)
			addRenderableWidget(new Button(left() + EDGE, top() + 5, 16, 14, Component.literal("‹"), this::goBack)
					.tooltip(Component.literal("Back to " + crumb())));
		if (showSettingsButton())
			addRenderableWidget(new Button(left() + W - EDGE - 16, top() + 5, 16, 14, Component.empty(),
					() -> minecraft.setScreenAndShow(new SettingsScreen(this)))
					.item(new ItemStack(Items.COMPARATOR)).tooltip(Component.literal("Clean UI settings")));
		initBody();
	}

	protected boolean showSettingsButton() {
		return true;
	}

	/** User UI scale, shrunk so the window always fits on screen. */
	@Override
	protected float baseScale() {
		return Math.min(Config.get().uiScale / 100f, Math.min((height - 8f) / H, (width - 8f) / W));
	}

	@Override
	public void onClose() {
		lastShownNanos = 0; // a reopen should animate in again
		super.onClose();
	}

	@Override
	protected final void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (!isClosing()) lastShownNanos = System.nanoTime();
		int x = left(), y = top();
		Theme.panel(g, x, y, W, H, a);

		// header: [back] icon  SkyBlock Menu › Page            date  time
		int hx = x + EDGE + (depth > 0 ? 20 : 0);
		Theme.item(g, icon, hx, y + 6, 12, a);
		hx += 16;
		if (depth > 0) {
			String crumb = crumb() + "  ›  ";
			g.text(font, crumb, hx, y + 8, Theme.argb(Theme.TEXT_MUTED, a), true);
			hx += font.width(crumb);
		}
		g.text(font, title, hx, y + 8, Theme.argb(Theme.TITLE, a), true);
		SkyblockTime.Date now = SkyblockTime.at(SkyblockTime.now());
		String clock = now.clock(), date = now.monthName() + " " + SkyblockTime.ordinal(now.day()) + "  ";
		int cx = x + W - EDGE - (showSettingsButton() ? 22 : 0) - font.width(clock);
		g.text(font, clock, cx, y + 8, Theme.argb(Theme.TEXT, a), true);
		g.text(font, date, cx - font.width(date), y + 8, Theme.argb(Theme.TEXT_MUTED, a), true);
		Theme.divider(g, x + 3, y + HEADER, W - 6, a);

		// footer
		int fy = y + H - FOOTER;
		g.fill(x + 3, fy, x + W - 3, fy + 1, Theme.argb(Theme.LINE, a));
		Theme.label(g, font, hint(), x + EDGE, fy + 6, a);
		g.text(font, VERSION, x + W - EDGE - font.width(VERSION), fy + 6, Theme.argb(Theme.TEXT_MUTED, a * 0.7f), true);

		// body slides in: from the right going into a page, from the left coming back
		float p = page.get();
		bodyAlpha = p;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate((1f - p) * 16f * slideDir, 0f);
		extractBody(g, mouseX, mouseY, a * p);
		pose.popMatrix();
	}
}
