package cleanui.feature;

import cleanui.data.Config;
import cleanui.data.Networth;
import cleanui.ui.Anim;
import cleanui.ui.Button;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanguard corpse settings, opened from the Mining skill page: which animation plays (snake,
 * lockpick or none), blur, sounds, confetti and drop values, test buttons for every drop, and what
 * each rare drop is worth right now. Saved on every change; Back returns to the Mining page.
 */
public final class VanguardSettingsScreen extends Window {
	private static final int COL_W = 200, RIGHT_X = 214, ROW_H = SettingsScreen.ROW_H, ROW_STEP = SettingsScreen.ROW_STEP;
	private static final String[] TEST_NAMES = {"No drop", "Frostbitten Dye", "Shattered Locket", "Caged Wisp", "Skeleton Key"};
	private static final String[] PINS = {"6/6", "5/6", "4/6", "4/6"};

	private static final List<SettingsScreen.Toggle> TOGGLES = List.of(
			new SettingsScreen.Toggle("Blur + hide HUD", "While it plays: blur the world and hide the rest of the HUD. You can still walk. Off: it draws over the HUD.",
					c -> c.vanguardFocus, c -> c.vanguardFocus = !c.vanguardFocus),
			new SettingsScreen.Toggle("Sounds", "Wither spawn, the katana cut, the lock's clicks, and each rare drop's own sound.",
					c -> c.vanguardSounds, c -> c.vanguardSounds = !c.vanguardSounds),
			new SettingsScreen.Toggle("Confetti", "Confetti when a rare drop bursts out.",
					c -> c.vanguardConfetti, c -> c.vanguardConfetti = !c.vanguardConfetti),
			new SettingsScreen.Toggle("Drop values", "The drop's coin value under its name (from the live price list).",
					c -> c.vanguardValues, c -> c.vanguardValues = !c.vanguardValues));

	private final Screen parent;
	private final List<Anim> knobs = new ArrayList<>();
	private int testDrop = -1; // -1 = no drop, else index into VanguardSnake.DROPS
	private Button pick;

	public VanguardSettingsScreen(Screen parent) {
		super("Vanguard", icon(), 3);
		this.parent = parent;
		for (SettingsScreen.Toggle t : TOGGLES) knobs.add(new Anim(t.get().test(Config.get()) ? 1f : 0f, 16f));
		for (VanguardSnake.Drop d : VanguardSnake.DROPS) Networth.price(d.id()); // start loading the values
	}

	/** The Frostbitten Dye: the rarest Vanguard drop. */
	static ItemStack icon() {
		return VanguardSnake.icon(VanguardSnake.DROPS.get(0));
	}

	private static String animation(Config c) {
		return !c.vanguardSnake ? "Off" : c.vanguardLockpick ? "Lockpick" : "Snake";
	}

	/** Snake → Lockpick → Off → Snake. */
	private static void nextAnimation(Config c) {
		if (!c.vanguardSnake) {
			c.vanguardSnake = true;
			c.vanguardLockpick = false;
		} else if (!c.vanguardLockpick) c.vanguardLockpick = true;
		else c.vanguardSnake = false;
	}

	@Override
	protected String crumb() {
		return parent.getTitle().getString();
	}

	@Override
	protected String hint() {
		return "Saved right away. Test buttons close the menu and play it.";
	}

	@Override
	protected void goBack() {
		minecraft.setScreenAndShow(parent);
	}

	@Override
	protected void closed() {
		// the Mining page we came from still has its Hypixel menu open underneath
		if (parent instanceof MenuPage) minecraft.player.closeContainer();
		else super.closed();
	}

	@Override
	protected void initBody() {
		int x = bx() + RIGHT_X, w = BW - RIGHT_X, y = by() + 14;
		pick = addRenderableWidget(new Button(x, y, w, 18, Component.literal(""), () -> testDrop = testDrop >= VanguardSnake.DROPS.size() - 1 ? -1 : testDrop + 1)
				.tooltip(Component.literal("Which drop the tests below play. Click to change.")).body());
		int half = (w - 4) / 2;
		addRenderableWidget(new Button(x, y + 20, half, 18, Component.literal("Test snake"), () -> test(false))
				.color(0x55FF55).tooltip(Component.literal("The ouroboros with the drop above (/testvanguard).")).body());
		addRenderableWidget(new Button(x + w - half, y + 20, half, 18, Component.literal("Test lock"), () -> test(true))
				.color(0xFFAA00).tooltip(Component.literal("The lockpick with the drop above (/testlock).")).body());
	}

	private void test(boolean lock) {
		onClose();
		if (lock) VanguardSnake.testLock(testDrop);
		else VanguardSnake.test(testDrop);
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = bx(), y = by();
		Config c = Config.get();
		Theme.divider(g, font, "VANGUARD CORPSES", x, y, COL_W, a);
		int gy = y + 14;
		boolean overAnim = mouseX >= x && mouseX < x + COL_W && mouseY >= gy && mouseY < gy + ROW_H;
		Theme.box(g, x, gy, COL_W, ROW_H, overAnim ? Theme.ROW_HOVER : Theme.ROW, a, overAnim ? Theme.FRAME : Theme.LINE, a);
		Theme.textPair(g, font, "Animation", Theme.TEXT_MUTED, animation(c), Theme.TITLE, x + 5, gy + 4, COL_W - 10, a);
		String tip = overAnim ? "Snake: a 3D ouroboros spins; a rare drop gets it cut in half by a katana. Lockpick: each pin picked is one more rarity (6/6 dye, 5/6 locket, 4/6 wisp or key). Off: nothing. Click to switch." : null;
		for (int i = 0; i < TOGGLES.size(); i++) {
			SettingsScreen.Toggle t = TOGGLES.get(i);
			int ry = y + 14 + (i + 1) * ROW_STEP;
			boolean over = mouseX >= x && mouseX < x + COL_W && mouseY >= ry && mouseY < ry + ROW_H;
			if (over) tip = t.tip();
			boolean on = t.get().test(c);
			knobs.get(i).target(on ? 1f : 0f);
			SettingsScreen.toggleRow(g, font, t.label(), on, knobs.get(i).get(), x, ry, COL_W, over, a);
		}
		// right: tests, then the rare drops with their lock pins and value
		int rx = x + RIGHT_X, rw = BW - RIGHT_X;
		Theme.divider(g, font, "TRY IT", rx, y, rw, a);
		pick.setMessage(Component.literal("Drop: " + TEST_NAMES[testDrop + 1]));
		pick.item(testDrop < 0 ? new ItemStack(net.minecraft.world.item.Items.BARRIER) : VanguardSnake.icon(VanguardSnake.DROPS.get(testDrop)));
		Theme.divider(g, font, "RARE DROPS", rx, y + 64, rw, a);
		for (int i = 0; i < VanguardSnake.DROPS.size(); i++) {
			VanguardSnake.Drop d = VanguardSnake.DROPS.get(i);
			int dy = y + 78 + i * 16;
			Theme.item(g, VanguardSnake.icon(d), rx, dy - 2, 12, a);
			Double p = Networth.price(d.id());
			String value = p == null || p <= 0 ? "--" : cleanui.skyblock.SkillParser.compact(String.valueOf(Math.round(p)));
			Theme.textLeader(g, font, d.name() + " §8" + PINS[i], Theme.TEXT, value, 0xFFE38A, rx + 16, dy, rw - 16, a);
		}
		if (tip != null) g.setTooltipForNextFrame(font, Component.literal(tip), mouseX, mouseY, Theme.tooltipStyle());
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		int x = bx(), y = by();
		for (int i = 0; i <= TOGGLES.size(); i++) {
			int ry = y + 14 + i * ROW_STEP;
			if (event.x() >= x && event.x() < x + COL_W && event.y() >= ry && event.y() < ry + ROW_H) {
				if (i == 0) nextAnimation(Config.get());
				else TOGGLES.get(i - 1).flip().accept(Config.get());
				Config.get().save();
				return true;
			}
		}
		return false;
	}
}
