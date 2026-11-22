package cleanui.feature;

import cleanui.data.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The sidebar scoreboard in the Scatha Stats frame: the objective's title on the raised plate, each
 * line in the server's own colours, blank spacer lines as gem dividers. Lines are read the way vanilla
 * does (team sidebar first, then the sidebar; hidden entries dropped; highest score first; at most 15).
 * Place and size come from the HUD editor; Hypixel's date line and footer can be hidden.
 */
public final class ScoreboardHud {
	private static final int TOP = 7, LINE = 10, GAP = 7, PAD_X = 9, MIN_W = 120;
	private static final Pattern SERVER_LINE = Pattern.compile("^\\d{1,2}/\\d{1,2}/\\d{2}\\b"), FOOTER = Pattern.compile("(?i)hypixel\\.net\\s*$");
	private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparingInt(PlayerScoreEntry::value).reversed()
			.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

	/** Dev preview: show the sample even without a real scoreboard. */
	public static boolean preview;

	private record Board(Component title, List<Component> lines) {}

	private ScoreboardHud() {}

	/** Draw ours instead of vanilla's? Only when there's a sidebar to draw (or a preview). */
	public static boolean replacesVanilla() {
		return Config.get().scoreboardFancy;
	}

	public static void render(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() instanceof HudEditor) return; // the editor draws its own
		Board b = board(mc, preview);
		if (b == null) return;
		int[] at = place(b, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
		drawAt(g, b, at[0], at[1], scale());
	}

	// ---- for the HUD editor ----

	public static float scale() {
		return Config.get().scoreboardScale / 100f;
	}

	public static int boxW() {
		return boxW(board(Minecraft.getInstance(), true));
	}

	public static int boxH() {
		return boxH(board(Minecraft.getInstance(), true));
	}

	/** Top-left corner on a screen of this size: the saved place (or vanilla's right-centre), kept on screen. */
	public static int[] place(int screenW, int screenH) {
		return place(board(Minecraft.getInstance(), true), screenW, screenH);
	}

	/** The panel with its box's top-left at (x, y): the real scoreboard, or the sample when there's none. */
	public static void drawAt(GuiGraphicsExtractor g, int x, int y, float scale) {
		drawAt(g, board(Minecraft.getInstance(), true), x, y, scale);
	}

	// ---- reading ----

	private static Board board(Minecraft mc, boolean sampleIfNone) {
		Board real = real(mc);
		if (real != null) return real;
		return sampleIfNone ? sample() : null;
	}

	private static Board real(Minecraft mc) {
		if (mc.level == null || mc.player == null) return null;
		Scoreboard sb = mc.level.getScoreboard();
		Objective obj = null;
		PlayerTeam team = sb.getPlayersTeam(mc.player.getScoreboardName());
		if (team != null && team.getColor().isPresent()) obj = sb.getDisplayObjective(team.getColor().get().displaySlot());
		if (obj == null) obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (obj == null) return null;
		List<Component> lines = sb.listPlayerScores(obj).stream().filter(e -> !e.isHidden()).sorted(ORDER).limit(15)
				.map(e -> (Component) PlayerTeam.formatNameForTeam(sb.getPlayersTeam(e.owner()), e.ownerName())).toList();
		return new Board(obj.getDisplayName(), filter(lines));
	}

	/** Like the screenshot of a Hypixel scoreboard: for the editor and previews. */
	private static Board sample() {
		List<Component> lines = new ArrayList<>();
		for (String s : new String[]{"§711/22/26 §8m38AD", "", "§fLate Autumn 4th", "§79:20am §b☽", "§7⏣ §cMagma Fields", "",
				"§fHeat: §c28♨", "", "§fPurse: §6150,768,591", "§fBits: §b3,756", "§fNearby Players: §a3", "", "§ewww.hypixel.net"})
			lines.add(Component.literal(s));
		return new Board(Component.literal("SKYBLOCK").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), filter(lines));
	}

	/** Drops the hidden lines, and blank lines left at the ends or doubled up by that. */
	private static List<Component> filter(List<Component> in) {
		Config c = Config.get();
		List<Component> out = new ArrayList<>();
		for (Component line : in) {
			String plain = plain(line);
			if (c.scoreboardHideServer && SERVER_LINE.matcher(plain).find()) continue;
			if (c.scoreboardHideFooter && FOOTER.matcher(plain).find()) continue;
			if (plain.isEmpty() && (out.isEmpty() || plain(out.getLast()).isEmpty())) continue;
			out.add(line);
		}
		while (!out.isEmpty() && plain(out.getLast()).isEmpty()) out.removeLast();
		return out;
	}

	private static String plain(Component line) {
		return ChatFormatting.stripFormatting(line.getString()).strip();
	}

	// ---- layout and drawing ----

	private static int boxW(Board b) {
		Font font = Minecraft.getInstance().font;
		int w = ScathaStats.titleWidth(font, b.title(), false) + 30;
		for (Component line : b.lines()) w = Math.max(w, font.width(line) + PAD_X * 2);
		return Math.max(MIN_W, w) + 2;
	}

	private static int bodyH(Board b) {
		int h = 14 + 6; // under the title plate, and the bottom margin
		for (Component line : b.lines()) h += plain(line).isEmpty() ? GAP : LINE;
		return h;
	}

	private static int boxH(Board b) {
		return TOP + bodyH(b) + 3;
	}

	private static int[] place(Board b, int screenW, int screenH) {
		Config c = Config.get();
		float s = scale();
		int w = Math.round(boxW(b) * s), h = Math.round(boxH(b) * s);
		int x = c.scoreboardX < 0 ? screenW - w - 1 : c.scoreboardX;
		int y = c.scoreboardY < 0 ? (screenH - h) / 2 : c.scoreboardY;
		return new int[]{Math.max(0, Math.min(x, screenW - w)), Math.max(0, Math.min(y, screenH - h))};
	}

	private static void drawAt(GuiGraphicsExtractor g, Board b, int x, int y, float scale) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		Config c = Config.get();
		long now = System.currentTimeMillis();
		int w = boxW(b) - 2, h = bodyH(b);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		ScathaStats.frame(g, font, 0, TOP, w, h, now, b.title(), c.scoreboardShimmer, false);
		int ly = TOP + 14;
		for (Component line : b.lines()) {
			if (plain(line).isEmpty()) {
				if (c.scoreboardGems) ScathaStats.divider(g, 0, ly + GAP / 2 - 1, w, now);
				ly += GAP;
			} else {
				g.text(font, line, PAD_X, ly, 0xFFFFFFFF, true);
				ly += LINE;
			}
		}
		pose.popMatrix();
	}
}
