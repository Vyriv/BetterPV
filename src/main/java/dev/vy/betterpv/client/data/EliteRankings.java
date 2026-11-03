package dev.vy.betterpv.client.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Every Elite leaderboard placement for one player on one profile, best rank first. */
public final class EliteRankings {
	/** Ranks down to this one show white; lower ranks show grey. */
	public static final int TOP_RANK = 1000;
	/** Elite API terms require a clickable credit to this URL wherever its data is shown. */
	public static final String CREDIT_URL = "https://eliteskyblock.com/";

	private static final Pattern MONTH_ID = Pattern.compile("(\\d{4})-(\\d{2})");
	private static final Pattern WEEK_ID = Pattern.compile("(\\d{4})-W(\\d{1,2})");

	public enum State {
		IDLE,
		LOADING,
		READY,
		ERROR
	}

	public enum Interval {
		CURRENT,
		MONTHLY,
		WEEKLY,
		OTHER
	}

	public record Entry(
		String id,
		String title,
		String shortTitle,
		int rank,
		double amount,
		boolean decimal,
		Interval interval,
		String intervalId,
		boolean profileBoard
	) {
		public String label() {
			return this.shortTitle == null || this.shortTitle.isBlank() ? this.title : this.shortTitle;
		}

		public EliteBoards.Board board() {
			return EliteBoards.of(this.id, this.title);
		}

		/** Row value, e.g. {@code 306k}, {@code 1.32m}, {@code 48.25}. */
		public String compactAmount() {
			if (this.decimal && Math.abs(this.amount) < 1_000D) {
				return FormatUtil.twoDecimals(this.amount);
			}
			return FormatUtil.compactCoins(this.amount);
		}

		/** Tooltip value, e.g. {@code 306,071.14} or {@code 19,213}. */
		public String exactAmount() {
			return this.decimal ? FormatUtil.twoDecimals(this.amount) : FormatUtil.commas(Math.round(this.amount));
		}

		/** Empty for all-time boards. */
		public String intervalLabel() {
			return switch (this.interval) {
				case CURRENT -> "";
				case MONTHLY -> "Monthly";
				case WEEKLY -> "Weekly";
				case OTHER -> this.intervalId;
			};
		}

		/** e.g. {@code November 2026} / {@code Week 45, 2026}; empty for all-time boards. */
		public String intervalDetail() {
			String id = this.intervalId == null ? "" : this.intervalId.trim();
			Matcher week = WEEK_ID.matcher(id);
			if (week.matches()) {
				return "Week " + Integer.parseInt(week.group(2)) + ", " + week.group(1);
			}
			if (MONTH_ID.matcher(id).matches()) {
				try {
					YearMonth ym = YearMonth.parse(id);
					return ym.getMonth().getDisplayName(TextStyle.FULL, Locale.US) + " " + ym.getYear();
				} catch (DateTimeParseException ignored) {
				}
			}
			return id;
		}
	}

	/** Best rank first; ties put all-time boards before monthly/weekly, then by name. */
	public static final Comparator<Entry> ORDER = Comparator.comparingInt(Entry::rank)
		.thenComparing(entry -> entry.interval().ordinal())
		.thenComparing(entry -> entry.title().toLowerCase(Locale.ROOT))
		.thenComparing(Entry::id);

	private static final EliteRankings IDLE = new EliteRankings(State.IDLE, "", List.of(), "");

	private final State state;
	private final String key;
	private final List<Entry> entries;
	private final String error;

	private EliteRankings(State state, String key, List<Entry> entries, String error) {
		this.state = state;
		this.key = key == null ? "" : key;
		this.entries = entries == null ? List.of() : List.copyOf(entries);
		this.error = error == null ? "" : error;
	}

	public static EliteRankings idle() {
		return IDLE;
	}

	public static EliteRankings loading(String key) {
		return new EliteRankings(State.LOADING, key, List.of(), "");
	}

	public static EliteRankings ready(String key, List<Entry> entries) {
		return new EliteRankings(State.READY, key, entries, "");
	}

	public static EliteRankings error(String key, String message) {
		return new EliteRankings(State.ERROR, key, List.of(), message);
	}

	/** Identity of a ranking request: undashed lowercase player UUID + profile id. */
	public static String key(UUID playerUuid, String profileId) {
		if (playerUuid == null || profileId == null || profileId.isBlank()) {
			return "";
		}
		String player = playerUuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
		String profile = profileId.trim().replace("-", "").toLowerCase(Locale.ROOT);
		return player + "|" + profile;
	}

	/** Turns a finished Elite request into a display state for {@code key}. */
	public static EliteRankings fromResponse(String key, Optional<JsonObject> body, Throwable failure) {
		if (failure != null) {
			return error(key, "Couldn't reach Elite");
		}
		if (body == null || body.isEmpty()) {
			return error(key, "Elite rankings unavailable");
		}
		JsonElement ranks = body.get().get("ranks");
		if (ranks == null || !ranks.isJsonObject()) {
			return error(key, "Couldn't read Elite rankings");
		}
		List<Entry> parsed = SoftDataFailure.call("Elite rankings parse", null, () -> parse(ranks.getAsJsonObject()));
		return parsed == null ? error(key, "Couldn't read Elite rankings") : ready(key, parsed);
	}

	/** Placements from Elite's {@code ranks} map, dropping non-positive ranks, sorted by {@link #ORDER}. */
	public static List<Entry> parse(JsonObject ranks) {
		List<Entry> out = new ArrayList<>();
		if (ranks == null) {
			return out;
		}
		for (Map.Entry<String, JsonElement> e : ranks.entrySet()) {
			if (e.getValue() == null || !e.getValue().isJsonObject()) {
				continue;
			}
			JsonObject obj = e.getValue().getAsJsonObject();
			int rank = intOf(obj, "rank");
			if (rank < 1) {
				continue;
			}
			String slug = str(obj, "slug");
			String id = e.getKey() == null || e.getKey().isBlank() ? slug : e.getKey();
			if (id.isBlank()) {
				continue;
			}
			String title = str(obj, "title");
			if (title.isBlank()) {
				title = id;
			}
			String intervalId = str(obj, "intervalIdentifier");
			out.add(new Entry(
				id,
				title,
				str(obj, "short"),
				rank,
				doubleOf(obj, "amount"),
				"decimal".equalsIgnoreCase(str(obj, "type")),
				intervalOf(slug.isBlank() ? id : slug, intervalId),
				intervalId,
				boolOf(obj, "profile")
			));
		}
		out.sort(ORDER);
		return out;
	}

	static Interval intervalOf(String slug, String intervalId) {
		String id = intervalId == null ? "" : intervalId.trim();
		String s = slug == null ? "" : slug.toLowerCase(Locale.ROOT);
		if (WEEK_ID.matcher(id).matches() || s.endsWith("-weekly")) {
			return Interval.WEEKLY;
		}
		if (MONTH_ID.matcher(id).matches() || s.endsWith("-monthly")) {
			return Interval.MONTHLY;
		}
		return id.isEmpty() ? Interval.CURRENT : Interval.OTHER;
	}

	/**
	 * What to show once {@code incoming} lands while {@code shown} is on screen. A response only
	 * fills the loading slot it was requested for, so results for another player or profile
	 * (or a late duplicate) never replace newer state.
	 */
	public static EliteRankings resolve(EliteRankings shown, EliteRankings incoming) {
		if (incoming == null) {
			return shown == null ? IDLE : shown;
		}
		if (shown == null || shown.state != State.LOADING || shown.key.isEmpty() || !shown.key.equals(incoming.key)) {
			return shown == null ? IDLE : shown;
		}
		return incoming;
	}

	/** Display rank, e.g. {@code #1} or {@code #1,000}. */
	public static String rankLabel(int rank) {
		return "#" + FormatUtil.commas(rank);
	}

	public int bestRank() {
		return this.entries.isEmpty() ? 0 : this.entries.get(0).rank();
	}

	public int worstRank() {
		return this.entries.isEmpty() ? 0 : this.entries.get(this.entries.size() - 1).rank();
	}

	public boolean isFor(String key) {
		return !this.key.isEmpty() && this.key.equals(key);
	}

	public State state() {
		return this.state;
	}

	public String key() {
		return this.key;
	}

	public List<Entry> entries() {
		return this.entries;
	}

	public String error() {
		return this.error;
	}

	private static String str(JsonObject obj, String key) {
		JsonElement el = obj.get(key);
		if (el == null || !el.isJsonPrimitive()) {
			return "";
		}
		String value = el.getAsString();
		return value == null ? "" : value.trim();
	}

	private static int intOf(JsonObject obj, String key) {
		JsonElement el = obj.get(key);
		if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
			return -1;
		}
		double value = el.getAsDouble();
		return Double.isFinite(value) ? (int) Math.round(value) : -1;
	}

	private static double doubleOf(JsonObject obj, String key) {
		JsonElement el = obj.get(key);
		if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
			return 0D;
		}
		double value = el.getAsDouble();
		return Double.isFinite(value) ? value : 0D;
	}

	private static boolean boolOf(JsonObject obj, String key) {
		JsonElement el = obj.get(key);
		return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean() && el.getAsBoolean();
	}
}
