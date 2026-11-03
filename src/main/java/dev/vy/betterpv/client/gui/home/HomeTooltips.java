package dev.vy.betterpv.client.gui.home;

import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.PlayerStatus;
import dev.vy.betterpv.client.data.ProfileSnapshot;
import dev.vy.betterpv.client.data.UsernameHistory;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Username history, bank, and status tooltip builders for Home. */
final class HomeTooltips {
	static final int ENABLED_GREEN = 0xFF55FF55;
	static final int OFFLINE_RED = 0xFFFF5555;

	private HomeTooltips() {
	}

	static void drawUsernameHistory(
		GuiGraphicsExtractor g,
		Font font,
		UsernameHistory history,
		int historyScroll,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH,
		int[] scrollOut
	) {
		List<PvTooltip.Line> lines = usernameHistory(history);
		if (lines.isEmpty()) {
			scrollOut[0] = historyScroll;
			scrollOut[1] = 0;
			return;
		}
		int lineH = font.lineHeight + 3;
		int maxBodyH = Math.max(lineH * 4, (int) (screenH * 0.65) - 16 - lineH);
		int[] maxScroll = new int[1];
		PvTooltip.drawStyled(
			g, font, lines, mouseX, mouseY, screenW, screenH,
			historyScroll, maxBodyH, maxScroll
		);
		int clamped = Math.min(historyScroll, maxScroll[0]);
		scrollOut[0] = clamped;
		scrollOut[1] = maxScroll[0];
	}

	static List<PvTooltip.Line> usernameHistory(UsernameHistory history) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		switch (history.state()) {
			case IDLE -> {
				tip.add(PvTooltip.Line.title("Username history", PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.divider());
				tip.add(PvTooltip.Line.action("Click to load username history"));
			}
			case LOADING -> {
				tip.add(PvTooltip.Line.title("Username history", PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.divider());
				tip.add(PvTooltip.Line.meta("Loading..."));
			}
			case ERROR -> {
				tip.add(PvTooltip.Line.title("Username history", PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.divider());
				tip.add(PvTooltip.Line.of(
					history.error().isBlank() ? "Unavailable" : history.error(),
					OFFLINE_RED));
			}
			case READY -> {
				List<UsernameHistory.Entry> entries = history.entries();
				tip.add(PvTooltip.Line.title(
					"Username history (" + entries.size() + ")", PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.divider());
				if (entries.isEmpty()) {
					tip.add(PvTooltip.Line.meta("No history"));
				} else {
					// Newest first.
					for (int i = entries.size() - 1; i >= 0; i--) {
						UsernameHistory.Entry entry = entries.get(i);
						boolean current = i == entries.size() - 1;
						boolean original = i == 0;
						List<PvTooltip.Span> nameSpans = List.of(
							current
								? PvTooltip.Span.bold(entry.username(), PvDraw.COLOR_ACCENT)
								: PvTooltip.Span.of(entry.username(), PvDraw.COLOR_TEXT)
						);
						List<PvTooltip.Span> dateSpans;
						if (!entry.changedAt().isBlank()) {
							dateSpans = List.of(PvTooltip.Span.of(shortDate(entry.changedAt()), PvDraw.COLOR_MUTED));
						} else if (original) {
							dateSpans = List.of(PvTooltip.Span.bold("original", PvDraw.COLOR_ACCENT));
						} else {
							dateSpans = List.of();
						}
						tip.add(PvTooltip.Line.row(nameSpans, dateSpans));
					}
				}
			}
		}
		return tip;
	}

	static List<PvTooltip.Line> coins(ProfileSnapshot snapshot) {
		return coins(snapshot.purseCoins(), snapshot.bankCoins());
	}

	/** Full, unshortened purse and bank values. */
	static List<PvTooltip.Line> coins(double purse, double bank) {
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(PvTooltip.Line.title("Coins", PvDraw.COLOR_TEXT));
		lines.add(PvTooltip.Line.divider());
		lines.add(PvTooltip.Line.row("Purse", PvDraw.COLOR_MUTED, FormatUtil.commas(Math.round(purse)), PvDraw.COLOR_GOLD));
		lines.add(PvTooltip.Line.row("Bank", PvDraw.COLOR_MUTED, FormatUtil.commas(Math.round(bank)), PvDraw.COLOR_GOLD));
		return lines;
	}

	/** One decimal with a lowercase suffix for the Home Coins line: 86.2m, 1.9b. */
	static String coinsShort(double value) {
		if (Math.abs(value) < 1_000D) {
			return FormatUtil.commas(Math.round(value));
		}
		String[] suffixes = {"k", "m", "b", "t"};
		double scaled = value / 1_000D;
		int unit = 0;
		while (unit < suffixes.length - 1 && Math.abs(scaled) >= 999.95D) {
			scaled /= 1_000D;
			unit++;
		}
		return String.format(java.util.Locale.US, "%.1f", scaled) + suffixes[unit];
	}

	static List<PvTooltip.Line> status(PlayerStatus playerStatus) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.of("Hypixel status", PvDraw.COLOR_TEXT));
		switch (playerStatus.state()) {
			case IDLE -> tip.add(PvTooltip.Line.of("Click to check online status", PvDraw.COLOR_MUTED));
			case LOADING -> tip.add(PvTooltip.Line.of("Loading...", PvDraw.COLOR_MUTED));
			case ONLINE -> {
				tip.add(PvTooltip.Line.of("Online", ENABLED_GREEN));
				tip.add(PvTooltip.Line.of(playerStatus.location(), PvDraw.COLOR_MUTED));
				String game = PlayerStatus.prettyLocation(playerStatus.gameType());
				if (!game.isBlank() && !game.equals(playerStatus.location())) {
					tip.add(PvTooltip.Line.of(game, PvDraw.COLOR_MUTED));
				}
			}
			case OFFLINE -> {
				List<String> lines = offlineLines(playerStatus, System.currentTimeMillis());
				tip.add(PvTooltip.Line.of(lines.get(0), OFFLINE_RED));
				for (int i = 1; i < lines.size(); i++) {
					tip.add(PvTooltip.Line.of(lines.get(i), PvDraw.COLOR_MUTED));
				}
			}
			case ERROR -> tip.add(PvTooltip.Line.of(
				playerStatus.error().isBlank() ? "Unavailable" : playerStatus.error(),
				OFFLINE_RED));
		}
		return tip;
	}

	/** First line is exactly the status button text; the rest only add detail. */
	static List<String> offlineLines(PlayerStatus playerStatus, long nowMs) {
		List<String> lines = new ArrayList<>(2);
		lines.add(playerStatus.buttonLabel(nowMs));
		if (playerStatus.lastOnlineMs() > 0L) {
			String date = FormatUtil.prettyDate(playerStatus.lastOnlineMs());
			if (!date.isBlank()) {
				lines.add(date);
			}
		} else if (playerStatus.sessionHidden()) {
			lines.add("Online status hidden in Hypixel API settings");
		} else {
			lines.add("Hypixel did not share a last online time");
		}
		return lines;
	}

	static String shortDate(String iso) {
		if (iso == null || iso.isBlank()) {
			return "";
		}
		// Ashcon: 2015-04-01T00:00:00.000Z -> 2015-04-01
		int t = iso.indexOf('T');
		return t > 0 ? iso.substring(0, t) : iso;
	}
}
