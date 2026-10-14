package dev.vy.betterpv.client.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.CrashReport;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public class TipEasterEggTest {
	private static final String DEFAULT = "Tip: some panels can be clicked for more info";
	// 6px per character, like a monospace stand-in for the Minecraft font.
	private static final ToIntFunction<String> WIDTH = s -> s.length() * 6;

	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static TipEasterEgg egg(List<CrashReport> crashes) {
		TipEasterEgg egg = new TipEasterEgg(crashes::add);
		egg.setLayout(new TipEasterEgg.Layout(14, 300, 200, 9, 1F, DEFAULT));
		return egg;
	}

	@Test
	public void messagesAreExactAndInOrder() {
		assertEquals(List.of(
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
		), TipEasterEgg.MESSAGES);
		assertEquals(19, TipEasterEgg.MESSAGES.size());
		assertEquals(20, TipEasterEgg.CRASH_CLICK);
	}

	@Test
	public void showsEachMessageThenCrashesOnlyOnClickTwenty() {
		List<CrashReport> crashes = new ArrayList<>();
		TipEasterEgg egg = egg(crashes);
		assertEquals(DEFAULT, egg.text(DEFAULT));
		for (int click = 1; click <= 19; click++) {
			assertTrue(egg.mouseClicked(20, 304, 0));
			assertEquals(TipEasterEgg.MESSAGES.get(click - 1), egg.text(DEFAULT));
			assertTrue("no crash on click " + click, crashes.isEmpty());
		}
		assertTrue(egg.mouseClicked(20, 304, 0));
		assertEquals(1, crashes.size());
		assertEquals("no 20th message", TipEasterEgg.MESSAGES.get(18), egg.text(DEFAULT));
		assertTrue("later clicks are swallowed", egg.mouseClicked(20, 304, 0));
		assertEquals("never crashes twice", 1, crashes.size());
	}

	@Test
	public void unrelatedClicksNeverCount() {
		List<CrashReport> crashes = new ArrayList<>();
		TipEasterEgg egg = egg(crashes);
		for (int i = 0; i < 50; i++) {
			assertFalse("right click", egg.mouseClicked(20, 304, 1));
			assertFalse("middle click", egg.mouseClicked(20, 304, 2));
			assertFalse("left of the text", egg.mouseClicked(13, 304, 0));
			assertFalse("right of the text", egg.mouseClicked(214, 304, 0));
			assertFalse("above", egg.mouseClicked(20, 299, 0));
			assertFalse("below", egg.mouseClicked(20, 309, 0));
		}
		assertEquals(0, egg.clicks());
		assertTrue(crashes.isEmpty());
		assertFalse("nothing counts before the tip is drawn",
			new TipEasterEgg(crashes::add).mouseClicked(20, 304, 0));
	}

	@Test
	public void closingTheViewerResetsTheSequence() {
		List<CrashReport> crashes = new ArrayList<>();
		TipEasterEgg egg = egg(crashes);
		for (int i = 0; i < 19; i++) {
			egg.mouseClicked(20, 304, 0);
		}
		egg.reset();
		assertEquals(0, egg.clicks());
		assertEquals(DEFAULT, egg.text(DEFAULT));
		assertNull("hitbox waits for the next frame", egg.layout());
		egg.setLayout(new TipEasterEgg.Layout(14, 300, 200, 9, 1F, DEFAULT));
		egg.mouseClicked(20, 304, 0);
		assertTrue("starts over instead of crashing", crashes.isEmpty());
		assertEquals(TipEasterEgg.MESSAGES.get(0), egg.text(DEFAULT));
	}

	@Test
	public void colourGoesYellowToOrangeToRed() {
		assertEquals(0xFFFFFF55, TipEasterEgg.color(0));
		assertEquals(0xFFFFFF55, TipEasterEgg.color(4));
		assertEquals(0xFFFFAA00, TipEasterEgg.color(11));
		assertEquals(0xFFFF5555, TipEasterEgg.color(17));
		assertEquals(0xFFFF5555, TipEasterEgg.color(19));
		for (int i = 1; i <= 19; i++) {
			int green = (TipEasterEgg.color(i) >> 8) & 0xFF;
			assertTrue("only ever gets angrier at click " + i, green <= ((TipEasterEgg.color(i - 1) >> 8) & 0xFF));
			assertEquals(0xFF, (TipEasterEgg.color(i) >> 16) & 0xFF);
		}
	}

	@Test
	public void hitboxMatchesTheDrawnText() {
		var shortTip = TipEasterEgg.layout("Please stop clicking me.", WIDTH, 9, 640, 360, 14, 540);
		assertEquals(14, shortTip.x());
		assertEquals(360 - 9 - 14, shortTip.y());
		assertEquals("Please stop clicking me.".length() * 6, shortTip.w());
		assertEquals(9, shortTip.h());
		assertEquals(1F, shortTip.scale(), 0F);
		assertTrue(shortTip.contains(14, 337));
		assertFalse(shortTip.contains(14 + shortTip.w(), 337));

		var longTip = TipEasterEgg.layout(TipEasterEgg.MESSAGES.get(9), WIDTH, 9, 640, 360, 14, 540);
		assertTrue("longer text, wider hitbox", longTip.w() > shortTip.w());
	}

	@Test
	public void longMessagesShrinkThenTrimBeforeTheSlider() {
		for (String message : TipEasterEgg.MESSAGES) {
			for (int right = 60; right <= 600; right += 7) {
				var l = TipEasterEgg.layout(message, WIDTH, 9, 640, 360, 14, right);
				assertTrue(message + " at " + right, l.x() + l.w() <= Math.max(right, l.x() + 1));
				assertTrue(l.scale() >= TipEasterEgg.MIN_SCALE_PERCENT / 100F);
				assertTrue("stays on screen", l.y() >= 0 && l.y() + l.h() <= 360);
			}
		}
		var squeezed = TipEasterEgg.layout(TipEasterEgg.MESSAGES.get(9), WIDTH, 9, 640, 360, 14, 200);
		assertTrue(squeezed.scale() < 1F);
		assertEquals("shrunk, not trimmed", TipEasterEgg.MESSAGES.get(9), squeezed.text());
		var tiny = TipEasterEgg.layout(TipEasterEgg.MESSAGES.get(9), WIDTH, 9, 640, 360, 14, 80);
		assertTrue(tiny.text().endsWith("..."));
	}

	@Test
	public void crashReportCarriesTheExactMessageAndMarksItIntentional() {
		CrashReport report = TipEasterEgg.crashReport();
		assertEquals("BetterPV has encountered a fatal issue.\n"
			+ "Cause: You repeatedly harassed an innocent piece of text, you're evil", report.getTitle());
		assertEquals(TipEasterEgg.CRASH_MESSAGE, report.getException().getMessage());
		assertTrue(report.getException() instanceof TipEasterEgg.IntentionalCrash);
		String details = report.getDetails();
		assertTrue(details, details.contains("BetterPV easter egg"));
		assertTrue(details, details.contains("not a BetterPV bug"));
	}
}
