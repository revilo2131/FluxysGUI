package cleanui.ui;

/**
 * A value that eases toward a target. Exponential smoothing on real elapsed time,
 * so it looks the same at 30 or 300 FPS. Call {@link #get()} once per frame.
 */
public final class Anim {
	private final float speed; // higher = faster; ~14 settles in about 0.25s
	private float value, target;
	private long last = System.nanoTime();

	public Anim(float start, float speed) {
		this.value = this.target = start;
		this.speed = speed;
	}

	public void target(float target) {
		this.target = target;
	}

	/** Jump to a value with no animation (e.g. the start point of a slide). */
	public void snap(float value) {
		this.value = value;
	}

	public float get() {
		long now = System.nanoTime();
		float dt = Math.min((now - last) / 1e9f, 0.1f); // clamp so a lag spike doesn't teleport
		last = now;
		value += (target - value) * (1f - (float) Math.exp(-speed * dt));
		if (Math.abs(target - value) < 0.002f) value = target;
		return value;
	}

	public boolean done() {
		return value == target;
	}
}
