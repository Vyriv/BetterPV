package dev.vy.betterpv.client.data;

import java.util.Locale;

/** Hypixel player online status for the Home Status control. */
public final class PlayerStatus {
	public enum State {
		IDLE,
		LOADING,
		ONLINE,
		OFFLINE,
		ERROR
	}

	private final State state;
	private final String gameType;
	private final String mode;
	private final String map;
	private final String error;

	private PlayerStatus(State state, String gameType, String mode, String map, String error) {
		this.state = state == null ? State.IDLE : state;
		this.gameType = gameType == null ? "" : gameType;
		this.mode = mode == null ? "" : mode;
		this.map = map == null ? "" : map;
		this.error = error == null ? "" : error;
	}

	public static PlayerStatus idle() {
		return new PlayerStatus(State.IDLE, "", "", "", "");
	}

	public static PlayerStatus loading() {
		return new PlayerStatus(State.LOADING, "", "", "", "");
	}

	public static PlayerStatus online(String gameType, String mode, String map) {
		return new PlayerStatus(State.ONLINE, gameType, mode, map, "");
	}

	public static PlayerStatus offline() {
		return new PlayerStatus(State.OFFLINE, "", "", "", "");
	}

	public static PlayerStatus error(String message) {
		return new PlayerStatus(State.ERROR, "", "", "", message);
	}

	public State state() {
		return this.state;
	}

	public String gameType() {
		return this.gameType;
	}

	public String mode() {
		return this.mode;
	}

	public String map() {
		return this.map;
	}

	public String error() {
		return this.error;
	}

	public String buttonLabel() {
		return switch (this.state) {
			case IDLE -> "Status";
			case LOADING -> "…";
			case ONLINE -> {
				String loc = prettyLocation(this.map);
				if (loc.isBlank()) {
					loc = prettyLocation(this.mode);
				}
				if (loc.isBlank()) {
					loc = prettyLocation(this.gameType);
				}
				yield loc.isBlank() ? "Online" : "Online - " + loc;
			}
			case OFFLINE -> "Offline";
			case ERROR -> "Status";
		};
	}

	public int buttonColor(int accent, int online, int offline, int muted) {
		return switch (this.state) {
			case ONLINE -> online;
			case OFFLINE -> offline;
			case LOADING -> muted;
			case ERROR -> muted;
			case IDLE -> accent;
		};
	}

	/** {@code dungeon_hub} → {@code Dungeon Hub}. */
	public static String prettyLocation(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String trimmed = raw.trim().replace('-', '_');
		String[] parts = trimmed.split("_+");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				out.append(part.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return out.toString();
	}
}
