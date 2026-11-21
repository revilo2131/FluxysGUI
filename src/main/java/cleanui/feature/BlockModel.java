package cleanui.feature;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A blocky 3D model drawn with GUI blits (the buff Scatha, the ouroboros). Add boxes in model
 * space (y up, front = +z), then {@link #draw}: each box face is its texture rect skewed into place
 * by an affine pose, faces turned away are skipped, the rest drawn back to front and shaded by
 * which way they face. Boxes use Minecraft's usual skin texture layout.
 */
final class BlockModel {
	private static final Vector3f LIGHT = new Vector3f(-0.45f, 0.65f, 0.6f).normalize();

	private record Face(float depth, float tlx, float tly, float trx, float try_, float blx, float bly, int u, int v, int w, int h, int shade) {}

	private final Identifier texture;
	private final int texSize;
	private final Matrix4f view;
	private final float ox, oy, scale;
	private final List<Face> faces = new ArrayList<>();

	/** {@code view} turns model space to the camera; (ox, oy) is where the model origin lands, {@code scale} px per unit. */
	BlockModel(Identifier texture, int texSize, Matrix4f view, float ox, float oy, float scale) {
		this.texture = texture;
		this.texSize = texSize;
		this.view = view;
		this.ox = ox;
		this.oy = oy;
		this.scale = scale;
	}

	/** A box whose size in texture pixels is its size in units. */
	void box(Matrix4f model, float x, float y, float z, int w, int h, int d, int u, int v, float grow) {
		box(model, x, y, z, w, h, d, u, v, w, h, d, grow);
	}

	/** A box of any size wearing the texture of a tw×th×td box at (u, v), grown by {@code grow} on every side. */
	void box(Matrix4f model, float x, float y, float z, float w, float h, float d, int u, int v, int tw, int th, int td, float grow) {
		float e = grow + 0.03f; // a hair bigger so faces meet without hairline gaps
		float x0 = x - e, y0 = y - e, z0 = z - e, x1 = x + w + e, y1 = y + h + e, z1 = z + d + e;
		// each face: top-left, top-right, bottom-left corner (as the texture sees it) and its texture rect
		face(model, x0, y1, z1, x1, y1, z1, x0, y0, z1, u + td, v + td, tw, th);                  // front
		face(model, x1, y1, z0, x0, y1, z0, x1, y0, z0, u + 2 * td + tw, v + td, tw, th);         // back
		face(model, x0, y1, z0, x0, y1, z1, x0, y0, z0, u, v + td, td, th);                       // right side
		face(model, x1, y1, z1, x1, y1, z0, x1, y0, z1, u + td + tw, v + td, td, th);             // left side
		face(model, x0, y1, z0, x1, y1, z0, x0, y1, z1, u + td, v, tw, td);                       // top
		face(model, x0, y0, z1, x1, y0, z1, x0, y0, z0, u + td + tw, v, tw, td);                  // bottom
	}

	/** Where a model-space point lands on screen: {x, y, depth (bigger = nearer)}. */
	float[] project(Matrix4f model, float x, float y, float z) {
		Vector3f p = view.transformPosition(model.transformPosition(new Vector3f(x, y, z)));
		return new float[]{ox + p.x * scale, oy - p.y * scale, p.z};
	}

	private void face(Matrix4f model, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, int u, int v, int w, int h) {
		Vector3f tl = view.transformPosition(model.transformPosition(new Vector3f(ax, ay, az)));
		Vector3f tr = view.transformPosition(model.transformPosition(new Vector3f(bx, by, bz)));
		Vector3f bl = view.transformPosition(model.transformPosition(new Vector3f(cx, cy, cz)));
		Vector3f across = new Vector3f(tr).sub(tl), down = new Vector3f(bl).sub(tl);
		Vector3f normal = new Vector3f(down).cross(across).normalize();
		if (normal.z <= 0.001f) return; // facing away
		float light = 0.48f + 0.52f * Math.max(0f, normal.dot(LIGHT));
		int grey = Math.round(255 * Math.min(1f, light));
		float depth = (tl.z + tr.z + bl.z + (tr.z + bl.z - tl.z)) / 4;
		faces.add(new Face(depth, ox + tl.x * scale, oy - tl.y * scale, ox + tr.x * scale, oy - tr.y * scale,
				ox + bl.x * scale, oy - bl.y * scale, u, v, w, h, grey << 16 | grey << 8 | grey));
	}

	/** Draws every visible face back to front; {@code alpha} 0..1 fades the whole model. */
	void draw(GuiGraphicsExtractor g, float alpha) {
		faces.sort(Comparator.comparingDouble(Face::depth));
		int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha))) << 24;
		Matrix3x2fStack pose = g.pose();
		for (Face f : faces) {
			pose.pushMatrix();
			// texture pixel (1,0) runs along the face's top edge, (0,1) down its side
			pose.mul(new Matrix3x2f((f.trx() - f.tlx()) / f.w(), (f.try_() - f.tly()) / f.w(),
					(f.blx() - f.tlx()) / f.h(), (f.bly() - f.tly()) / f.h(), f.tlx(), f.tly()));
			g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, f.u(), f.v(), f.w(), f.h(), texSize, texSize, a | f.shade());
			pose.popMatrix();
		}
	}
}
