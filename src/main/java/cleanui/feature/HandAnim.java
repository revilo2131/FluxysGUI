package cleanui.feature;

import cleanui.data.Config;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Hand animations for the item in your main hand: swing style, speed and strength, whether it moves
 * while hitting, where it sits, and how it comes up when you switch items. ItemInHandRendererMixin
 * hands vanilla a swing of 0 (and an equip of 0 for our own pull-outs), then {@link #apply} adds ours
 * right before the item is drawn. The empty hand, the offhand, maps and items being used stay vanilla.
 */
public final class HandAnim {
	public static final String[] SWINGS = {"VANILLA", "SMOOTH", "CHOP", "SPIN", "PUNCH", "SLIDE"};
	public static final String[] MOTIONS = {"FULL", "ROTATE", "NONE"};
	public static final String[] EQUIPS = {"VANILLA", "RISE", "NONE", "SLIDE", "SPIN", "FLIP", "ZOOM", "DROP"};
	private static final float PI = (float) Math.PI;
	/** Vanilla's pull-out takes about 6 ticks. */
	private static final long EQUIP_MS = 300;

	private static int lastSlot = -1;
	private static ItemStack lastItem = ItemStack.EMPTY;
	private static String lastName = "";
	private static long equipStart = -1;
	/** "Test pull-out" with the Vanilla style plays our Rise (vanilla's can't be restarted). */
	private static boolean testRise;

	// the current frame's call, set by the mixin around the item draw
	private static boolean active;
	private static float swing;
	private static HumanoidArm arm;

	private HandAnim() {}

	/** Ours to animate: main hand, holding something that isn't a map, not being eaten / drawn / blocked with. */
	public static boolean applies(AbstractClientPlayer player, InteractionHand hand, ItemStack stack) {
		if (!Config.get().handAnim || hand != InteractionHand.MAIN_HAND || stack.isEmpty() || stack.is(Items.FILLED_MAP)) return false;
		return !(player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && player.getUsedItemHand() == hand);
	}

	/** Vanilla keeps its own pull-out (and the cooldown dip) only with the Vanilla style and the dip on. */
	private static boolean ownEquip() {
		Config c = Config.get();
		return !(c.equipStyle.equals("VANILLA") && c.cooldownDip) || testRise;
	}

	/** Called before the redraw: notes the swing and a new item, returns the equip vanilla should use. */
	public static float begin(AbstractClientPlayer player, float swingProgress, ItemStack stack, float equipProgress) {
		active = true;
		swing = swingProgress;
		arm = player.getMainArm();
		if (player instanceof LocalPlayer lp) {
			int slot = lp.getInventory().getSelectedSlot();
			String name = stack.getHoverName().getString();
			// a different slot or item (not a lore / count update) plays the pull-out
			if (slot != lastSlot || !ItemStack.isSameItem(stack, lastItem) || !name.equals(lastName)) {
				if (lastSlot >= 0) equipStart = System.currentTimeMillis();
				lastSlot = slot;
				lastItem = stack.copy();
				lastName = name;
			}
		}
		return ownEquip() ? 0f : equipProgress;
	}

	public static void end() {
		active = false;
	}

	/** Restart the pull-out (the settings screen's test button). */
	public static void replayEquip() {
		equipStart = System.currentTimeMillis();
		testRise = Config.get().equipStyle.equals("VANILLA");
	}

	/** Swing duration in ticks at this speed (percent; higher = faster), at least 1. */
	static int swingTicks(int vanillaTicks, int speedPercent) {
		return Math.max(1, Math.round(vanillaTicks * 100f / Math.max(1, speedPercent)));
	}

	/** Pull-out progress 0..1 (1 = up) after {@code ms} at this speed, eased out. */
	static float equipProgress(long ms, int speedPercent) {
		float t = Math.min(1f, ms * Math.max(1, speedPercent) / (100f * EQUIP_MS));
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	/** LivingEntityMixin: your own swing, sped up or slowed down while holding an item. */
	public static int swingDuration(Object entity, int vanilla) {
		Minecraft mc = Minecraft.getInstance();
		Config c = Config.get();
		if (!c.handAnim || c.swingSpeed == 100 || entity != mc.player || mc.player.getMainHandItem().isEmpty()) return vanilla;
		return swingTicks(vanilla, c.swingSpeed);
	}

	/** Right before vanilla draws the item: pull-out, position, swing, rotation and size. */
	public static void apply(PoseStack pose) {
		if (!active) return;
		Config c = Config.get();
		float side = arm == HumanoidArm.RIGHT ? 1 : -1;
		// pull-out
		if (ownEquip() && equipStart >= 0) {
			float q = equipProgress(System.currentTimeMillis() - equipStart, c.equipSpeed);
			if (q >= 1) testRise = false;
			float r = 1 - q;
			String style = testRise || c.equipStyle.equals("VANILLA") ? "RISE" : c.equipStyle;
			switch (style) {
				case "RISE" -> pose.translate(0, -0.6f * r, 0);
				case "SLIDE" -> pose.translate(side * 0.9f * r, -0.1f * r, 0);
				case "SPIN" -> {
					pose.translate(0, -0.3f * r, 0);
					pose.mulPose(Axis.YP.rotationDegrees(side * 360 * r));
				}
				case "FLIP" -> {
					pose.translate(0, -0.4f * r, 0);
					pose.mulPose(Axis.XP.rotationDegrees(-180 * r));
				}
				case "ZOOM" -> pose.scale(Math.max(0.01f, q), Math.max(0.01f, q), Math.max(0.01f, q));
				case "DROP" -> pose.translate(0, 0.7f * r, 0);
				default -> {} // NONE
			}
		}
		// where it sits
		pose.translate(side * c.handX / 100f, c.handY / 100f, c.handZ / 100f);
		// the swing
		if (!c.swingMotion.equals("NONE") && swing > 0) swing(pose, swing, side, c.swingStrength / 100f, c.swingMotion.equals("FULL"), c.swingStyle);
		// turned and sized
		pose.mulPose(Axis.XP.rotationDegrees(c.handRotX));
		pose.mulPose(Axis.YP.rotationDegrees(side * c.handRotY));
		pose.mulPose(Axis.ZP.rotationDegrees(side * c.handRotZ));
		float s = c.handScale / 100f;
		pose.scale(s, s, s);
	}

	/** One swing at progress p (0..1); {@code move} = slide as well as turn. Vanilla is vanilla's own maths. */
	private static void swing(PoseStack pose, float p, float side, float k, boolean move, String style) {
		float sq = (float) Math.sqrt(p), arc = (float) Math.sin(p * PI);
		switch (style) {
			case "SMOOTH" -> {
				if (move) pose.translate(0, -0.1f * k * arc, -0.15f * k * arc);
				pose.mulPose(Axis.XP.rotationDegrees(-60 * k * arc));
			}
			case "CHOP" -> {
				float e = (float) Math.sin(sq * PI);
				if (move) pose.translate(0, 0.15f * k * e, -0.1f * k * e);
				pose.mulPose(Axis.XP.rotationDegrees(-100 * k * e));
			}
			case "SPIN" -> {
				if (move) pose.translate(0, 0, -0.15f * k * arc);
				pose.mulPose(Axis.YP.rotationDegrees(side * 360 * (1 - (1 - p) * (1 - p))));
			}
			case "PUNCH" -> {
				if (move) pose.translate(0, 0, -0.45f * k * arc);
				pose.mulPose(Axis.XP.rotationDegrees(-15 * k * arc));
			}
			case "SLIDE" -> {
				if (move) pose.translate(side * -0.3f * k * arc, 0, 0);
				pose.mulPose(Axis.YP.rotationDegrees(side * 70 * k * arc));
			}
			default -> { // VANILLA: ItemInHandRenderer's swing translate + applyItemArmAttackTransform
				if (move) pose.translate(side * -0.4f * k * (float) Math.sin(sq * PI), 0.2f * k * (float) Math.sin(sq * PI * 2), -0.2f * k * arc);
				float f = (float) Math.sin(p * p * PI), f1 = (float) Math.sin(sq * PI);
				pose.mulPose(Axis.YP.rotationDegrees(side * (45 + f * -20 * k)));
				pose.mulPose(Axis.ZP.rotationDegrees(side * f1 * -20 * k));
				pose.mulPose(Axis.XP.rotationDegrees(f1 * -80 * k));
				pose.mulPose(Axis.YP.rotationDegrees(side * -45));
			}
		}
	}
}
