package cleanui.feature;

import cleanui.CleanUi;
import cleanui.skyblock.SkyblockTime;
import cleanui.ui.Anim;
import cleanui.ui.Button;
import cleanui.ui.CleanScreen;
import cleanui.ui.Theme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

import java.util.function.Supplier;

/**
 * The Clean UI window every page lives in: header (brand + SkyBlock date), tab bar,
 * page body, footer (hint + version). Switching tabs swaps the screen but keeps the frame
 * still: only the page slides in and the tab underline glides to the new tab.
 */
public abstract class Window extends CleanScreen {
	public enum Tab {
		OVERVIEW("Overview", Items.NETHER_STAR, OverviewScreen::new, null),
		CALENDAR("Calendar", Items.CLOCK, CalendarScreen::new, null),
		// these two run a Hypixel command once; the menu it opens is then shown as the page
		SKILLS("Skills", Items.DIAMOND_SWORD, null, "skills"),
		STORAGE("Storage", Items.ENDER_CHEST, null, "storage");

		final String label;
		final ItemStack item;
		final Supplier<Screen> open;
		final String command;

		Tab(String label, net.minecraft.world.item.Item item, Supplier<Screen> open, String command) {
			this.label = label;
			this.item = new ItemStack(item);
			this.open = open;
			this.command = command;
		}
	}

	public static final int W = 440, H = 256;
	private static final int TABS_Y = 24, TAB_H = 18, BODY_Y = 50, FOOTER = 20;
	/** Page body size. */
	protected static final int BW = W - Theme.PAD * 2, BH = H - BODY_Y - FOOTER - 6;
	private static final ItemStack BRAND = new ItemStack(Items.NETHER_STAR); // SkyBlock's own menu item
	private static final String VERSION = "v" + FabricLoader.getInstance().getModContainer(CleanUi.ID)
			.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");

	// Carried between screens so a tab switch continues from where the last frame was.
	private static long lastShownNanos;
	private static Tab lastTab = Tab.OVERVIEW;
	private static float lastUnderX = -1, lastUnderW;

	private final Tab tab;
	private final Anim page = new Anim(1f, 16f), underX = new Anim(0f, 20f), underW = new Anim(0f, 20f);
	private int slideDir;
	private Button selectedTab;

	protected Window(Tab tab) {
		super(Component.literal(tab.label));
		this.tab = tab;
	}

	/** Add this page's widgets (positions relative to {@link #bx()}/{@link #by()}). */
	protected void initBody() {}

	/** Draw the page body at {@link #bx()}, {@link #by()} (size {@link #BW}×{@link #BH}). */
	protected abstract void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a);

	/** One-line hint shown in the footer. */
	protected String hint() {
		return "";
	}

	/** Called before leaving this page for another tab. */
	protected void leaving() {}

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
		slideDir = Integer.signum(tab.ordinal() - lastTab.ordinal());
		page.snap(switching && slideDir != 0 ? 0f : 1f);
		page.target(1f);
		lastTab = tab;
	}

	@Override
	protected final void init() {
		int x = left() + Theme.PAD, y = top() + TABS_Y;
		for (Tab t : Tab.values()) {
			int w = font.width(t.label) + 15 + 14;
			boolean available = t == tab || t.open != null || (t.command != null && CleanUi.onHypixel());
			Button b = new Button(x, y, w, TAB_H, Component.literal(t.label), () -> switchTo(t)).item(t.item).tab(t == tab);
			b.active = available;
			if (!available) b.tooltip(Component.literal("Only on Hypixel SkyBlock."));
			if (t == tab) selectedTab = b;
			addRenderableWidget(b);
			x += w + 2;
		}
		float target = selectedTab.getX() - left();
		underX.snap(lastUnderX >= 0 ? lastUnderX : target);
		underW.snap(lastUnderX >= 0 ? lastUnderW : selectedTab.getWidth());
		underX.target(target);
		underW.target(selectedTab.getWidth());
		initBody();
	}

	private void switchTo(Tab t) {
		if (t == tab) return;
		if (t.command != null) {
			// same as typing the command once; the menu hook then shows the page
			minecraft.getConnection().sendCommand(t.command);
			return;
		}
		leaving();
		minecraft.setScreenAndShow(t.open.get());
	}

	@Override
	public void onClose() {
		lastShownNanos = 0; // a reopen should animate in again
		lastUnderX = -1;
		super.onClose();
	}

	@Override
	protected final void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (!isClosing()) lastShownNanos = System.nanoTime();
		int x = left(), y = top();
		Theme.panel(g, x, y, W, H, a);

		// header: brand left, SkyBlock date + time right
		Theme.item(g, BRAND, x + Theme.PAD, y + 6, 12, a);
		g.text(font, "Clean UI", x + Theme.PAD + 16, y + 8, Theme.argb(Theme.ACCENT, a), true);
		SkyblockTime.Date now = SkyblockTime.at(SkyblockTime.now());
		String clock = now.clock(), date = now.monthName() + " " + SkyblockTime.ordinal(now.day()) + "  ";
		int cx = x + W - Theme.PAD - font.width(clock);
		g.text(font, clock, cx, y + 8, Theme.argb(Theme.TEXT, a), true);
		g.text(font, date, cx - font.width(date), y + 8, Theme.argb(Theme.TEXT_MUTED, a), true);

		// tab bar line + gliding underline
		int lineY = y + TABS_Y + TAB_H;
		g.fill(x + 1, lineY, x + W - 1, lineY + 1, Theme.argb(Theme.LINE, a));
		lastUnderX = underX.get();
		lastUnderW = underW.get();
		g.fill(x + Math.round(lastUnderX), lineY - 2, x + Math.round(lastUnderX + lastUnderW), lineY, Theme.argb(Theme.ACCENT, a));

		// footer
		int fy = y + H - FOOTER;
		g.fill(x + 1, fy, x + W - 1, fy + 1, Theme.argb(Theme.LINE, a));
		Theme.label(g, font, hint(), x + Theme.PAD, fy + 6, a);
		g.text(font, VERSION, x + W - Theme.PAD - font.width(VERSION), fy + 6, Theme.argb(Theme.TEXT_MUTED, a * 0.7f), true);

		// page body slides in from the side of the tab we came from
		float p = page.get();
		bodyAlpha = p;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate((1f - p) * 14f * slideDir, 0f);
		extractBody(g, mouseX, mouseY, a * p);
		pose.popMatrix();
	}
}
