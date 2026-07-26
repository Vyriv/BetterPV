package dev.vy.betterpv.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * NEU-style “stuck loading forever” easter egg for the profile viewer.
 * Escalating copy while the PV never becomes ready (missing player / failed fetch / hung load).
 *
 * <p><b>TEST TIMING:</b> hour-scale beats compressed to a 30s finale
 * ({@code originalMinutes} → seconds). Restore to 60m before shipping.
 * At finale: close PV → /limbo → fake Hypixel ban. Unlocked lines stack.
 */
public final class LoadingEgg {
	/** Trigger the limbo → fake-ban finale after this long on a stuck load. */
	public static final long BREAK_AT_MS = 30L * 1000L;

	/** Threshold seconds → message (oldest first). */
	private static final Beat[] BEATS = {
		new Beat(1, "This is taking awhile, try again?", 0xFF9A9AAC, false, false),
		new Beat(3, "Okay, really.. try again", 0xFFE8E8F0, false, false),
		new Beat(5, "You're still here..?", 0xFFFFCC66, false, false),
		new Beat(8, "Im starting to get concerned", 0xFFFFAA55, false, false),
		new Beat(10, "Okay, whats wrong with you", 0xFFFFAA00, true, false),
		new Beat(13, "Seriously, what is wrong with you", 0xFFFF8844, true, false),
		new Beat(15, "If you dont close it somethings gonna happen..", 0xFFFF6666, true, false),
		new Beat(18, "Im warning you..", 0xFFFF5555, true, false),
		new Beat(20, "Dont make me tell you again..", 0xFFFF4444, true, false),
		new Beat(23, "Seriously.", 0xFFFF3333, true, false),
		new Beat(25, "FINAL WARNING.", 0xFFFF0000, true, true),
		new Beat(30, "TIME TO TAKE A BREAK", 0xFFFF5555, true, true)
	};

	private LoadingEgg() {
	}

	public record Stage(Component line, int color, boolean bold, boolean shout) {
	}

	private record Beat(long atSeconds, String text, int color, boolean bold, boolean shout) {
	}

	/** All messages unlocked so far, oldest → newest (stack top to bottom). */
	public static List<Stage> stagesUnlocked(long elapsedMs) {
		long sec = Math.max(0L, elapsedMs / 1000L);
		List<Stage> out = new ArrayList<>();
		for (Beat beat : BEATS) {
			if (sec >= beat.atSeconds()) {
				out.add(stage(beat.text(), beat.color(), beat.bold(), beat.shout()));
			}
		}
		return out;
	}

	private static Stage stage(String text, int color, boolean bold, boolean shout) {
		String shown = shout ? text.toUpperCase(Locale.ROOT) : text;
		MutableComponent line = Component.literal(shown).setStyle(
			Style.EMPTY
				.withColor(TextColor.fromRgb(color & 0xFFFFFF))
				.withBold(bold || shout)
		);
		return new Stage(line, color, bold || shout, shout);
	}
}
