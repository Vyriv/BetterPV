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
				tip.add(PvTooltip.Line.meta("Loading…"));
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

	static List<PvTooltip.Line> bank(ProfileSnapshot snapshot) {
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(PvTooltip.Line.title("Bank", PvDraw.COLOR_TEXT));
		lines.add(PvTooltip.Line.divider());
		lines.add(PvTooltip.Line.row(
			"Balance", PvDraw.COLOR_MUTED,
			FormatUtil.shortCoins(snapshot.bankCoins()), PvDraw.COLOR_GOLD
		));
		List<ProfileSnapshot.BankTransaction> txs = snapshot.bankTransactions();
		if (txs == null || txs.isEmpty()) {
			lines.add(PvTooltip.Line.meta("No recent transactions (API off or empty)"));
			return lines;
		}
		lines.add(PvTooltip.Line.blank());
		lines.add(PvTooltip.Line.of("Recent", PvDraw.COLOR_MUTED));
		int shown = 0;
		for (ProfileSnapshot.BankTransaction tx : txs) {
			if (shown >= 6) {
				break;
			}
			String action = formatBankAction(tx.action());
			String amount = FormatUtil.shortCoins(tx.amount());
			String who = tx.initiatorName().isBlank() ? "" : tx.initiatorName();
			String when = tx.timestampMs() > 0L ? FormatUtil.ago(tx.timestampMs()) : "";
			String left = action + " " + amount;
			String right = when;
			if (!who.isBlank() && !when.isBlank()) {
				right = who + " · " + when;
			} else if (!who.isBlank()) {
				right = who;
			}
			lines.add(PvTooltip.Line.row(left, PvDraw.COLOR_TEXT, right, PvDraw.COLOR_MUTED));
			shown++;
		}
		return lines;
	}

	static List<PvTooltip.Line> status(PlayerStatus playerStatus) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.of("Hypixel status", PvDraw.COLOR_TEXT));
		switch (playerStatus.state()) {
			case IDLE -> tip.add(PvTooltip.Line.of("Click to check online status", PvDraw.COLOR_MUTED));
			case LOADING -> tip.add(PvTooltip.Line.of("Loading…", PvDraw.COLOR_MUTED));
			case ONLINE -> {
				tip.add(PvTooltip.Line.of(playerStatus.buttonLabel(), ENABLED_GREEN));
				if (!playerStatus.gameType().isBlank()) {
					tip.add(PvTooltip.Line.of(
						PlayerStatus.prettyLocation(playerStatus.gameType()),
						PvDraw.COLOR_MUTED));
				}
			}
			case OFFLINE -> tip.add(PvTooltip.Line.of("Offline", OFFLINE_RED));
			case ERROR -> tip.add(PvTooltip.Line.of(
				playerStatus.error().isBlank() ? "Unavailable" : playerStatus.error(),
				OFFLINE_RED));
		}
		return tip;
	}

	static String shortDate(String iso) {
		if (iso == null || iso.isBlank()) {
			return "";
		}
		// Ashcon: 2015-04-01T00:00:00.000Z -> 2015-04-01
		int t = iso.indexOf('T');
		return t > 0 ? iso.substring(0, t) : iso;
	}

	private static String formatBankAction(String action) {
		if (action == null || action.isBlank()) {
			return "Txn";
		}
		return switch (action.trim().toUpperCase(java.util.Locale.ROOT)) {
			case "DEPOSIT" -> "Deposit";
			case "WITHDRAW" -> "Withdraw";
			default -> {
				String lower = action.trim().toLowerCase(java.util.Locale.ROOT);
				yield Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
			}
		};
	}
}
