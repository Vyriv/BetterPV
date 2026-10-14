package dev.vy.betterpv.client.gui;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.CrashReport;

/** The bottom-left tip text answers back when clicked, and crashes the game on the 20th click. One per viewer session. */
final class TipEasterEgg {
	static final List<String> MESSAGES = List.of(
		"Tip: Some panels can be clicked for more info.",
		"Tip: This isn't one of those panels.",
		"Tip: Clicking it again won't help.",
		"Tip: You seem a little confused.",
		"Are you expecting something to happen?",
		"There's literally nothing here.",
		"Please stop clicking me.",
		"You have an entire profile viewer to explore.",
		"Are you seriously still doing this?",
		"I'm starting to understand why you need a profile viewer.",
		"Okay. This is getting annoying.",
		"WHY ARE YOU STILL CLICKING ME?",
		"GO LOOK AT YOUR FUCKING SKYBLOCK STATS.",
		"I'M A TOOLTIP. NOT A BUTTON.",
		"I'm going to give you one last chance.",
		"That was your last chance.",
		"You know what? Fuck this.",
		"I hope you saved whatever you were doing.",
		"Don't say I didn't warn you."
	);
	static final int CRASH_CLICK = MESSAGES.size() + 1;
	static final String CRASH_MESSAGE = "BetterPV has encountered a fatal issue.\n"
		+ "Cause: You repeatedly harassed an innocent piece of text, you're evil";
	static final int MIN_SCALE_PERCENT = 50;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int ORANGE = 0xFFFFAA00;
	private static final int RED = 0xFFFF5555;
	private static final int LEFT_BUTTON = 0;

	/** Where the tip is drawn, in slider-viewport units; {@code text} may be trimmed to fit. */
	record Layout(int x, int y, int w, int h, float scale, String text) {
		boolean contains(double mx, double my) {
			return w > 0 && mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	/** Thrown into the crash report so the stack trace points at the easter egg, not a real fault. */
	static final class IntentionalCrash extends RuntimeException {
		IntentionalCrash() {
			super(CRASH_MESSAGE);
		}
	}

	private final Consumer<CrashReport> crasher;
	private int clicks;
	private boolean crashed;
	private Layout layout;

	TipEasterEgg(Consumer<CrashReport> crasher) {
		this.crasher = crasher;
	}

	int clicks() {
		return clicks;
	}

	String text(String defaultTip) {
		return clicks == 0 ? defaultTip : MESSAGES.get(Math.min(clicks, MESSAGES.size()) - 1);
	}

	int color() {
		return color(clicks);
	}

	// Yellow through the polite "Tip:" lines, orange by click 11, red from click 17.
	static int color(int clicks) {
		if (clicks <= 4) {
			return YELLOW;
		}
		if (clicks <= 11) {
			return blend(YELLOW, ORANGE, (clicks - 4) / 7F);
		}
		return blend(ORANGE, RED, Math.min(1F, (clicks - 11) / 6F));
	}

	void setLayout(Layout layout) {
		this.layout = layout;
	}

	Layout layout() {
		return layout;
	}

	/** True when the click landed on the tip text and was used. */
	boolean mouseClicked(double mx, double my, int button) {
		if (button != LEFT_BUTTON || layout == null || !layout.contains(mx, my)) {
			return false;
		}
		if (crashed) {
			return true;
		}
		clicks++;
		if (clicks >= CRASH_CLICK) {
			crashed = true;
			crasher.accept(crashReport());
		}
		return true;
	}

	void reset() {
		clicks = 0;
		crashed = false;
		layout = null;
	}

	static CrashReport crashReport() {
		CrashReport report = new CrashReport(CRASH_MESSAGE, new IntentionalCrash());
		report.addCategory("BetterPV easter egg")
			.setDetail("Intentional", "Yes, the profile viewer tip text was clicked " + CRASH_CLICK + " times")
			.setDetail("Defect", "No, this is a deliberate crash and not a BetterPV bug");
		return report;
	}

	/**
	 * Fits the tip between the left margin and the scale slider, shrinking it down to half size before trimming.
	 * {@code width} measures text at full size.
	 */
	static Layout layout(
		String text, java.util.function.ToIntFunction<String> width, int lineHeight,
		int viewportW, int viewportH, int margin, int rightLimit
	) {
		int x = Math.max(0, Math.min(margin, viewportW));
		int room = Math.max(1, rightLimit - x);
		int full = width.applyAsInt(text);
		float scale = full <= room ? 1F : Math.max(MIN_SCALE_PERCENT / 100F, room / (float) full);
		String shown = text;
		if (full * scale > room) {
			String base = text;
			while (!base.isEmpty() && width.applyAsInt(base.stripTrailing() + "...") * scale > room) {
				base = base.substring(0, base.length() - 1);
			}
			shown = base.stripTrailing() + "...";
		}
		int w = (int) Math.ceil(width.applyAsInt(shown) * scale);
		int h = (int) Math.ceil(lineHeight * scale);
		int y = Math.max(0, viewportH - h - margin);
		return new Layout(x, y, w, h, scale, shown);
	}

	private static int blend(int from, int to, float t) {
		int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
