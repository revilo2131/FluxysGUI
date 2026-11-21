package cleanui.feature;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;


/**
 * The buff Scatha as a blocky 3D model, drawn with GUI blits: the real Scatha pet head (skin + its
 * teeth/spikes layer) on a huge body made of boxes (torso, abs, traps, delts, arms, fists, legs).
 * Each box face is the texture skewed into place by an affine pose; faces turned away are skipped
 * and the rest are drawn back to front, shaded by which way they face. Texture: buff_scatha.png,
 * boxes in Minecraft's usual skin layout. Model units: head = 8, feet at y = 0, y up, front = +z.
 */
final class BuffScatha {
	private static final Identifier TEX = Identifier.fromNamespaceAndPath("cleanui", "textures/gui/buff_scatha.png");
	private static final int TEX_SIZE = 128;

	/**
	 * One arm, radians: raise = out to the side (0 hanging, 90° horizontal, 180° straight up),
	 * forward = swung forward (negative: back), bend = elbow toward the outside/up (negative: across
	 * the body), bendX = elbow bending the forearm forward.
	 */
	record Arm(float raise, float forward, float bend, float bendX) {
		static Arm deg(float raise, float forward, float bend, float bendX) {
			return new Arm((float) Math.toRadians(raise), forward, (float) Math.toRadians(bend), bendX);
		}

		Arm lerp(Arm o, float k) {
			return new Arm(raise + (o.raise - raise) * k, forward + (o.forward - forward) * k, bend + (o.bend - bend) * k, bendX + (o.bendX - bendX) * k);
		}
	}

	/** A pose: both arms, which way he faces, hip shift (lean), head nod (down = positive) and head turn. */
	record Look(Arm left, Arm right, float yaw, float lean, float nod, float headTurn) {
		Look lerp(Look o, float k) {
			return new Look(left.lerp(o.left, k), right.lerp(o.right, k), yaw + (o.yaw - yaw) * k, lean + (o.lean - lean) * k,
					nod + (o.nod - nod) * k, headTurn + (o.headTurn - headTurn) * k);
		}
	}

	/** Arms hanging, facing left: how he walks in. */
	static final Look WALK = new Look(Arm.deg(14, 0, 8, 0), Arm.deg(14, 0, 8, 0), -1.25f, 0, 0, 0);
	static final Look DOUBLE_BICEPS = new Look(Arm.deg(90, 0, 120, 0.25f), Arm.deg(90, 0, 120, 0.25f), 0.35f, 0, -0.08f, 0);
	/** The gym-motivation ab reveal: right hand behind the head, left hand pulling the waistband, hips shifted, looking down at the abs. */
	static final Look AB_REVEAL = new Look(Arm.deg(12, 0.3f, -55, 0.2f), Arm.deg(160, -0.4f, 112, 0), 0.3f, 0.07f, 0.38f, -0.18f);
	/** Hands on the hips, elbows wide: a lat spread. */
	static final Look LAT_SPREAD = new Look(Arm.deg(42, -0.1f, -100, 0.2f), Arm.deg(42, -0.1f, -100, 0.2f), 0f, 0, -0.12f, 0);

	/** pitch = camera tilt; walk = stride phase (radians); swing 0..1 = how much the legs and arms swing. */
	record Pose(Look look, float pitch, float walk, float swing) {}

	private BuffScatha() {}

	/** Draws the model with its feet at (feetX, feetY), {@code scale} px per model unit. */
	static void draw(GuiGraphicsExtractor g, float feetX, float feetY, float scale, Pose p) {
		Look look = p.look();
		Matrix4f view = new Matrix4f().rotateX(p.pitch()).rotateY(look.yaw());
		BlockModel m = new BlockModel(TEX, TEX_SIZE, view, feetX, feetY, scale);
		float stride = (float) Math.sin(p.walk()) * 0.7f * p.swing();
		float bob = (float) Math.abs(Math.sin(p.walk())) * 0.8f * p.swing();
		Matrix4f root = new Matrix4f().translate(0, bob, 0);

		// legs swing from the hips
		for (int s = -1; s <= 1; s += 2) {
			Matrix4f leg = new Matrix4f(root).translate(s * 3.2f, 11, 0).rotateX(s * stride).translate(-s * 3.2f, -11, 0);
			m.box(leg, s * 3.2f - 3, 0, -3, 6, 11, 6, 56, 40, 0);
		}
		// everything from the hips up can shift sideways (the hip pop of the ab reveal)
		Matrix4f body = new Matrix4f(root).translate(0, 11, 0).rotateZ(look.lean()).translate(0, -11, 0);
		m.box(body, -6, 10, -3, 12, 3, 6, 0, 60, 0);   // pelvis
		m.box(body, -6, 13, -3, 12, 6, 6, 48, 16, 0);  // abs
		m.box(body, -8, 19, -4, 16, 12, 8, 0, 16, 0);  // chest
		m.box(body, 4, 31, -3, 4, 3, 6, 80, 40, 0);    // traps
		m.box(body, -8, 31, -3, 4, 3, 6, 80, 40, 0);
		// the Scatha head: turns and nods with the pose, a little bob while walking, then its teeth/spikes layer
		Matrix4f head = new Matrix4f(body).translate(0, 31, 0).rotateY(look.headTurn()).rotateX(look.nod() - bob * 0.08f).translate(0, -31, 0);
		m.box(head, -4, 31, -4, 8, 8, 8, 0, 0, 0);
		m.box(head, -4, 31, -4, 8, 8, 8, 32, 0, 0.5f);
		// delts, then the arms (s = +1 his left, on +x; -1 his right), swinging opposite the legs while walking
		for (int s = -1; s <= 1; s += 2) {
			Arm a = s > 0 ? look.left() : look.right();
			m.box(body, s > 0 ? 7.5f : -13.5f, 25, -3.5f, 6, 6, 7, 84, 16, 0);
			Matrix4f arm = new Matrix4f(body).translate(s * 10.5f, 27.5f, 0).rotateZ(s * a.raise()).rotateX(-a.forward() - s * stride);
			m.box(arm, -2.5f, -8, -2.5f, 5, 8, 5, 0, 40, 0);
			Matrix4f forearm = new Matrix4f(arm).translate(0, -8, 0).rotateZ(s * a.bend()).rotateX(-a.bendX());
			m.box(forearm, -2.5f, -8, -2.5f, 5, 8, 5, 20, 40, 0);
			m.box(forearm, -2, -12, -2, 4, 4, 4, 40, 40, 0);
		}

		m.draw(g, 1f);
	}
}
