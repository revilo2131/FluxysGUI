package cleanui.feature;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * A UI sound cut to a length, with a fade in and out (ticks). Used to trim long sounds
 * (the Wither spawn) down to an animation's length.
 */
final class FadingSound extends AbstractTickableSoundInstance {
	private final float peak;
	private final int length, fadeIn, fadeOut;
	private int age;

	FadingSound(SoundEvent sound, float pitch, float peak, int length, int fadeIn, int fadeOut) {
		super(sound, SoundSource.MASTER, RandomSource.create());
		this.pitch = pitch;
		this.peak = peak;
		this.length = length;
		this.fadeIn = Math.max(1, fadeIn);
		this.fadeOut = Math.max(1, fadeOut);
		this.volume = peak * 0.05f; // not 0: a silent sound may never start
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
	}

	@Override
	public void tick() {
		age++;
		float k = age < fadeIn ? age / (float) fadeIn : age > length - fadeOut ? (length - age) / (float) fadeOut : 1f;
		volume = peak * Math.max(0.01f, Math.min(1f, k));
		if (age >= length) stop();
	}
}
