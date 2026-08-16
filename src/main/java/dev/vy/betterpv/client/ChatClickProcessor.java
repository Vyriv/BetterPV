package dev.vy.betterpv.client;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Rewrites Hypixel chat name clicks/hovers to open BetterPV, and makes
 * party/guild/officer/PM/all-chat sender names clickable when needed.
 * Also marks party / Party Finder join names for PV (no duplicate messages).
 */
public final class ChatClickProcessor {
	private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final Pattern VIEW_PROFILE_HOVER = Pattern.compile(
		"(?i)Click here to view ([A-Za-z0-9_]{1,16})'s profile"
	);
	/** {@code [MVP+] Name joined the party.} */
	private static final Pattern PARTY_JOIN = Pattern.compile(
		"(?i)^(?:\\[[^\\]]+]\\s*)?([A-Za-z0-9_]{3,16})\\s+joined the party\\.?\\s*$"
	);
	/** {@code You have joined [MVP+] Name's party!} */
	private static final Pattern YOU_JOINED_PARTY = Pattern.compile(
		"(?i)^You have joined\\s+(?:\\[[^\\]]+]\\s*)?([A-Za-z0-9_]{3,16})'s?\\s+party!?\\s*$"
	);
	/**
	 * Dungeon / Kuudra Party Finder:
	 * {@code Party Finder > Name joined the dungeon group! (...)}
	 * {@code Party Finder > Name joined the group! (...)}
	 */
	private static final Pattern PARTY_FINDER_JOIN = Pattern.compile(
		"(?i)^Party Finder\\s*>\\s*(?:\\[[^\\]]+]\\s*)?([A-Za-z0-9_]{3,16})\\s+joined the (?:dungeon )?group!"
	);

	private ChatClickProcessor() {
	}

	public static Component process(Component component) {
		if (component == null) {
			return null;
		}

		String plain = component.getString();
		NameRange range = findClickableNameRange(plain);
		MutableComponent result = Component.empty();
		int[] index = {0};
		boolean[] changed = {false};

		component.visit((style, segment) -> {
			if (!segment.isEmpty()) {
				appendSegment(result, segment, style, index[0], range, changed);
				index[0] += segment.length();
			}
			return Optional.empty();
		}, Style.EMPTY);

		Component rewritten = changed[0] ? result : component;
		String joinName = PartyJoinPvNotifier.extractJoinName(plain);
		if (joinName != null) {
			PartyJoinPvNotifier.schedule(joinName);
		}
		return rewritten;
	}

	private static NameRange findClickableNameRange(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String trimmed = text.trim();
		NameRange party = partyJoinRange(text, trimmed);
		if (party != null) {
			return party;
		}
		return findChatSenderRange(text);
	}

	private static NameRange partyJoinRange(String full, String trimmed) {
		Matcher m = PARTY_JOIN.matcher(trimmed);
		if (m.find()) {
			return rangeForGroup(full, trimmed, m, 1);
		}
		m = YOU_JOINED_PARTY.matcher(trimmed);
		if (m.find()) {
			return rangeForGroup(full, trimmed, m, 1);
		}
		m = PARTY_FINDER_JOIN.matcher(trimmed);
		if (m.find()) {
			return rangeForGroup(full, trimmed, m, 1);
		}
		return null;
	}

	private static NameRange rangeForGroup(String full, String trimmed, Matcher matcher, int group) {
		String name = matcher.group(group);
		if (name == null || !PLAYER_NAME_PATTERN.matcher(name).matches()) {
			return null;
		}
		int trimOffset = full.indexOf(trimmed);
		if (trimOffset < 0) {
			trimOffset = 0;
		}
		int start = trimOffset + matcher.start(group);
		int end = trimOffset + matcher.end(group);
		if (start < 0 || end > full.length() || start >= end) {
			return null;
		}
		return new NameRange(start, end, name);
	}

	private static NameRange findChatSenderRange(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}

		int delimiter = text.indexOf(": ");
		if (delimiter <= 0) {
			return null;
		}
		if (isRosterOrSystemLabel(text.substring(0, delimiter))) {
			return null;
		}

		int end = delimiter;
		int start = end;
		while (start > 0 && isNameChar(text.charAt(start - 1))) {
			start--;
		}

		if (start == end) {
			return null;
		}
		String name = text.substring(start, end);
		if (!PLAYER_NAME_PATTERN.matcher(name).matches()) {
			return null;
		}
		return new NameRange(start, end, name);
	}

	/**
	 * Skip party/guild roster dumps ("PARTY LEADER: ...") but allow real chat
	 * ("Party > Name: ...", "Guild > Name: ...", "From Name: ...").
	 */
	private static boolean isRosterOrSystemLabel(String beforeColon) {
		String upper = beforeColon.trim().toUpperCase(Locale.ROOT);
		if (upper.contains(" > ")) {
			return false;
		}
		if (upper.startsWith("FROM ") || upper.startsWith("TO ")) {
			return false;
		}
		return upper.startsWith("PARTY ")
			|| upper.equals("PARTY")
			|| upper.equals("PARTY LEADER")
			|| upper.equals("PARTY MODERATORS")
			|| upper.equals("PARTY MEMBERS")
			|| upper.startsWith("GUILD ")
			|| upper.equals("GUILD")
			|| upper.equals("OFFICER")
			|| upper.startsWith("OFFICER ")
			|| upper.startsWith("ONLINE ")
			|| upper.startsWith("MEMBERS ")
			|| upper.startsWith("TRADE");
	}

	private static boolean isNameChar(char c) {
		return c == '_' || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
	}

	private static void appendSegment(
		MutableComponent target,
		String text,
		Style style,
		int segmentStart,
		NameRange range,
		boolean[] changed
	) {
		String hypixelName = usernameFromHypixelStyle(style);
		if (hypixelName != null) {
			target.append(Component.literal(text).setStyle(pvStyle(style, hypixelName)));
			changed[0] = true;
			return;
		}

		int segmentEnd = segmentStart + text.length();
		if (range == null || range.end <= segmentStart || range.start >= segmentEnd) {
			target.append(Component.literal(text).setStyle(style));
			return;
		}

		int localStart = Math.max(0, range.start - segmentStart);
		int localEnd = Math.min(text.length(), range.end - segmentStart);

		if (localStart > 0) {
			target.append(Component.literal(text.substring(0, localStart)).setStyle(style));
		}

		target.append(Component.literal(text.substring(localStart, localEnd)).setStyle(pvStyle(style, range.name)));
		changed[0] = true;

		if (localEnd < text.length()) {
			target.append(Component.literal(text.substring(localEnd)).setStyle(style));
		}
	}

	private static String usernameFromHypixelStyle(Style style) {
		ClickEvent click = style.getClickEvent();
		if (click instanceof ClickEvent.RunCommand run) {
			String name = usernameFromCommand(run.command());
			if (name != null) {
				return name;
			}
		}

		HoverEvent hover = style.getHoverEvent();
		if (hover instanceof HoverEvent.ShowText show) {
			String hoverText = show.value().getString();
			Matcher matcher = VIEW_PROFILE_HOVER.matcher(hoverText);
			if (matcher.find()) {
				String name = matcher.group(1);
				if (PLAYER_NAME_PATTERN.matcher(name).matches()) {
					return name;
				}
			}
		}
		return null;
	}

	private static String usernameFromCommand(String command) {
		if (command == null || command.isBlank()) {
			return null;
		}
		String trimmed = command.trim();
		if (trimmed.startsWith("/")) {
			trimmed = trimmed.substring(1);
		}
		String lower = trimmed.toLowerCase(Locale.ROOT);
		String rest;
		if (lower.startsWith("socialoptions ")) {
			rest = trimmed.substring("socialoptions ".length()).trim();
		} else if (lower.startsWith("viewprofile ")) {
			rest = trimmed.substring("viewprofile ".length()).trim();
		} else {
			return null;
		}
		int space = rest.indexOf(' ');
		if (space > 0) {
			rest = rest.substring(0, space);
		}
		return PLAYER_NAME_PATTERN.matcher(rest).matches() ? rest : null;
	}

	private static Style pvStyle(Style style, String name) {
		Component tip = Component.translatable("betterpv.chat.click_pv", name);
		return style
			.withClickEvent(new ClickEvent.RunCommand("/pv " + name))
			.withHoverEvent(new HoverEvent.ShowText(tip));
	}

	private record NameRange(int start, int end, String name) {
	}
}
