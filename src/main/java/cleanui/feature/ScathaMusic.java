package cleanui.feature;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Original note block music for a pet win: a build-up from the moment the pet is certain (green /
 * three Scathas) and a drop exactly when the rarity shows. Driven from the client tick (50 ms),
 * so everything sits on a 100 ms sixteenth grid (150 BPM).
 *
 * Notes are note block steps: 0 = F#3 ... 12 = F#4 ... 24 = F#5 (pitch 0.5 to 2.0). Key F# minor,
 * loop F#m - D - A - E.
 */
final class ScathaMusic {
	private static final int STEP_MS = 100, GAP_MS = 250; // sixteenth; the silence right before the drop
	private static final int[] ROOTS = {0, 8, 3, 10};            // F#, D, A, E
	private static final int[][] CHORDS = {{12, 15, 19}, {8, 12, 15}, {15, 19, 22}, {14, 17, 21}};
	/** The hook, one bar per chord; -1 = rest. */
	private static final int[][] HOOK = {
			{12, -1, 15, -1, 19, -1, 15, 17, -1, 15, 12, -1, 10, -1, 12, -1},
			{8, -1, 12, -1, 15, -1, 17, 15, -1, 12, 15, -1, 17, -1, 19, -1},
			{15, -1, 19, -1, 22, -1, 19, 17, -1, 15, 19, -1, 17, -1, 15, -1},
			{14, -1, 17, -1, 21, -1, 19, 17, 14, -1, 17, -1, 19, 21, 24, -1}};
	private static final int[] BASS_STEPS = {0, 3, 6, 8, 11, 14};
	/** Build-up arpeggio over F#m, rising a semitone at a time. */
	private static final int[] ARP = {0, 3, 7, 12};

	private static long nextSnare, nextArp;
	private static int arpIndex, lastStep;

	private ScathaMusic() {}

	static void reset() {
		nextSnare = nextArp = Long.MIN_VALUE;
		arpIndex = 0;
		lastStep = -1;
	}

	/**
	 * Call every tick while a pet spin runs. {@code from} = when the build-up starts, {@code drop} =
	 * when the rarity shows (ms since the spin started); {@code tier} 0 rare, 1 epic, 2 legendary.
	 */
	static void update(long t, long from, long drop, int tier) {
		if (t < from) return;
		if (t < drop) {
			buildUp(t, from, drop);
			return;
		}
		int bars = tier == 0 ? 1 : tier == 1 ? 2 : 4;
		int step = (int) ((t - drop) / STEP_MS);
		if (step >= bars * 16) {
			if (lastStep < bars * 16) { // the last hit: everything on the root
				hit(SoundEvents.NOTE_BLOCK_BASEDRUM, 6, 1f);
				hit(SoundEvents.NOTE_BLOCK_BASS, 0, 1f);
				for (int n : CHORDS[0]) hit(SoundEvents.NOTE_BLOCK_BELL, n, 0.6f);
				lastStep = bars * 16;
			}
			return;
		}
		// play every step we passed since the last tick (ticks are 50 ms, steps 100 ms: normally one)
		for (int s = lastStep + 1; s <= step; s++) dropStep(s, tier);
		lastStep = step;
	}

	/** Faster and higher toward the drop: snare roll, rising arpeggio, bass pulse; a breath of silence at the end. */
	private static void buildUp(long t, long from, long drop) {
		long left = drop - t;
		if (left < GAP_MS) return;
		float p = Math.min(1f, (t - from) / (float) Math.max(1, drop - GAP_MS - from));
		if (t >= nextSnare) {
			hit(SoundEvents.NOTE_BLOCK_SNARE, Math.round(4 + 14 * p), 0.35f + 0.55f * p);
			if (p < 0.5f) hit(SoundEvents.NOTE_BLOCK_BASEDRUM, 3, 0.7f);
			nextSnare = t + Math.round(400 - 350 * p); // quarter notes down to a 50 ms roll
		}
		if (t >= nextArp) {
			int rise = Math.round(p * 9); // up to nine semitones over the build
			int note = Math.min(24, ARP[arpIndex % ARP.length] + rise);
			hit(SoundEvents.NOTE_BLOCK_BELL, note, 0.45f + 0.4f * p);
			if (p > 0.6f) hit(SoundEvents.NOTE_BLOCK_CHIME, note + 12 <= 24 ? note + 12 : note, 0.3f); // sparkle an octave up near the end
			arpIndex++;
			nextArp = t + Math.round(200 - 120 * p);
		}
	}

	private static void dropStep(int s, int tier) {
		int bar = s / 16 % 4, i = s % 16;
		boolean big = tier == 2;
		if (i % 4 == 0 || (big && i == 14)) hit(SoundEvents.NOTE_BLOCK_BASEDRUM, 6, 1f);
		if (i == 4 || i == 12) hit(SoundEvents.NOTE_BLOCK_SNARE, 12, 0.8f);
		if (i % 4 == 2) hit(SoundEvents.NOTE_BLOCK_HAT, 18, 0.5f);
		for (int b : BASS_STEPS) if (b == i) hit(SoundEvents.NOTE_BLOCK_BASS, ROOTS[bar] + (i == 6 || i == 14 ? 12 : 0), 0.9f);
		int lead = HOOK[bar][i];
		if (lead >= 0) {
			hit(tier == 0 ? SoundEvents.NOTE_BLOCK_BELL : SoundEvents.NOTE_BLOCK_PLING, lead, 0.8f);
			if (big) {
				hit(SoundEvents.NOTE_BLOCK_TRUMPET, lead, 0.55f);
				hit(SoundEvents.NOTE_BLOCK_BIT, Math.max(0, lead - 12), 0.35f);
			}
		}
		if (i == 0 && tier > 0) for (int n : CHORDS[bar]) hit(SoundEvents.NOTE_BLOCK_BELL, n, big ? 0.5f : 0.35f);
	}

	private static void hit(Holder<SoundEvent> sound, int note, float volume) {
		ScathaSlots.music(sound, (float) Math.pow(2, (Math.max(0, Math.min(24, note)) - 12) / 12.0), volume);
	}
}
