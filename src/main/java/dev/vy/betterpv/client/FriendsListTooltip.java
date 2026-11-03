package dev.vy.betterpv.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Friends List ({@code /f list}) name hovers. Hypixel's hover already carries the friend details
 * ("Last seen 8 days ago", "Friends for a month and 28 days") followed by its own click action. Those detail
 * lines are kept exactly as Hypixel sent them; only the trailing click action is replaced by BetterPV's.
 */
public final class FriendsListTooltip {
	private static final Pattern HEADER = Pattern.compile("\\bFriends \\(Page \\d+ of \\d+\\)");

	private FriendsListTooltip() {
	}

	/** True for a Friends List chat message (its page header is part of the same message). */
	public static boolean isFriendsList(String plainMessage) {
		return plainMessage != null && HEADER.matcher(plainMessage.replaceAll("\u00a7.", "")).find();
	}

	/**
	 * Hypixel's detail lines, a blank line, then {@code clickLine} unchanged. Just {@code clickLine} when the
	 * original hover has no details.
	 */
	public static Component tooltip(HoverEvent original, Component clickLine) {
		List<MutableComponent> details = original instanceof HoverEvent.ShowText show ? detailLines(show.value()) : List.of();
		if (details.isEmpty()) {
			return clickLine;
		}
		MutableComponent out = Component.empty();
		for (MutableComponent line : details) {
			out.append(line).append(Component.literal("\n"));
		}
		return out.append(Component.literal("\n")).append(clickLine);
	}

	/** Hover lines without the trailing click action and blank lines; styling and legacy codes are kept. */
	static List<MutableComponent> detailLines(Component hover) {
		List<MutableComponent> lines = splitLines(hover);
		while (!lines.isEmpty()) {
			String last = plain(lines.get(lines.size() - 1));
			if (last.isEmpty() || isClickAction(last)) {
				lines.remove(lines.size() - 1);
			} else {
				break;
			}
		}
		while (!lines.isEmpty() && plain(lines.get(0)).isEmpty()) {
			lines.remove(0);
		}
		return lines;
	}

	private static boolean isClickAction(String plain) {
		String lower = plain.toLowerCase(Locale.ROOT);
		return lower.startsWith("click") || lower.startsWith("right click") || lower.startsWith("shift");
	}

	private static String plain(Component line) {
		return line.getString().replaceAll("\u00a7.", "").trim();
	}

	/**
	 * Splits on newlines across styled parts. A legacy colour code active at a split carries onto the next line
	 * so Hypixel's {@code §7} text keeps its colour.
	 */
	static List<MutableComponent> splitLines(Component component) {
		List<MutableComponent> lines = new ArrayList<>();
		MutableComponent[] current = {Component.empty()};
		component.visit((style, text) -> {
			String[] parts = text.split("\n", -1);
			String carried = "";
			for (int i = 0; i < parts.length; i++) {
				if (i > 0) {
					lines.add(current[0]);
					current[0] = Component.empty();
				}
				String part = startsWithColor(parts[i]) ? parts[i] : carried + parts[i];
				if (!part.isEmpty()) {
					current[0].append(Component.literal(part).withStyle(style));
				}
				carried = activeLegacyCodes(part);
			}
			return Optional.empty();
		}, Style.EMPTY);
		lines.add(current[0]);
		return lines;
	}

	private static boolean startsWithColor(String text) {
		if (text.length() < 2 || text.charAt(0) != '\u00a7') {
			return false;
		}
		char code = Character.toLowerCase(text.charAt(1));
		return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r';
	}

	/** Legacy {@code §} codes in effect at the end of {@code text}: last colour (or reset) plus later formats. */
	static String activeLegacyCodes(String text) {
		StringBuilder active = new StringBuilder();
		for (int i = 0; i + 1 < text.length(); i++) {
			if (text.charAt(i) != '\u00a7') {
				continue;
			}
			char code = Character.toLowerCase(text.charAt(i + 1));
			if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r') {
				active.setLength(0);
				if (code != 'r') {
					active.append('\u00a7').append(code);
				}
			} else if (code >= 'k' && code <= 'o') {
				active.append('\u00a7').append(code);
			}
			i++;
		}
		return active.toString();
	}
}
