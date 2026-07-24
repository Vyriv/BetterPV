package dev.vy.vypv.client.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Catacombs / class / run stats for the Dungeons tab. */
public final class DungeonSnapshot {
	public record ClassEntry(
		String id,
		String name,
		int level,
		float xp,
		float progress,
		boolean maxed,
		boolean selected,
		String xpHover
	) {
	}

	public record FloorEntry(
		String id,
		String label,
		long completions,
		long milestoneCompletions,
		long mobsKilled,
		long bestScore,
		long mostMobsKilled,
		long fastestMs,
		long fastestSMs,
		long fastestSPlusMs,
		double mostHealing,
		String mostDamageClass,
		double mostDamage
	) {
		public static FloorEntry empty(String id, String label) {
			return new FloorEntry(id, label, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0D, null, 0D);
		}
	}

	public record ModeStats(long totalRuns, List<FloorEntry> floors) {
		public static ModeStats empty() {
			return new ModeStats(0L, List.of());
		}
	}

	private final int cataLevel;
	private final float cataXp;
	private final float cataProgress;
	private final boolean cataMaxed;
	private final String cataHover;
	private final long secrets;
	private final double secretsPerRun;
	private final float classAverage;
	private final float classAverageProgress;
	private final boolean classAverageMaxed;
	private final String classAverageHover;
	private final List<ClassEntry> classes;
	private final ModeStats normal;
	private final ModeStats master;
	private final boolean expertRing;
	private final int hecatombLevel;
	private final double scarfBonus;
	private final Map<String, Double> classEssenceBonuses;
	private final double mayorFactor;
	private final String mayorName;

	public DungeonSnapshot(
		int cataLevel,
		float cataXp,
		float cataProgress,
		boolean cataMaxed,
		String cataHover,
		long secrets,
		double secretsPerRun,
		float classAverage,
		float classAverageProgress,
		boolean classAverageMaxed,
		String classAverageHover,
		List<ClassEntry> classes,
		ModeStats normal,
		ModeStats master,
		boolean expertRing,
		int hecatombLevel,
		double scarfBonus,
		Map<String, Double> classEssenceBonuses,
		double mayorFactor,
		String mayorName
	) {
		this.cataLevel = cataLevel;
		this.cataXp = cataXp;
		this.cataProgress = cataProgress;
		this.cataMaxed = cataMaxed;
		this.cataHover = cataHover;
		this.secrets = secrets;
		this.secretsPerRun = secretsPerRun;
		this.classAverage = classAverage;
		this.classAverageProgress = classAverageProgress;
		this.classAverageMaxed = classAverageMaxed;
		this.classAverageHover = classAverageHover;
		this.classes = List.copyOf(classes);
		this.normal = normal == null ? ModeStats.empty() : normal;
		this.master = master == null ? ModeStats.empty() : master;
		this.expertRing = expertRing;
		this.hecatombLevel = hecatombLevel;
		this.scarfBonus = scarfBonus;
		this.classEssenceBonuses = classEssenceBonuses == null
			? Map.of()
			: Collections.unmodifiableMap(Map.copyOf(classEssenceBonuses));
		this.mayorFactor = mayorFactor;
		this.mayorName = mayorName == null ? "" : mayorName;
	}

	public int cataLevel() {
		return this.cataLevel;
	}

	public float cataXp() {
		return this.cataXp;
	}

	public float cataProgress() {
		return this.cataProgress;
	}

	public boolean cataMaxed() {
		return this.cataMaxed;
	}

	public String cataHover() {
		return this.cataHover;
	}

	public long secrets() {
		return this.secrets;
	}

	public double secretsPerRun() {
		return this.secretsPerRun;
	}

	public float classAverage() {
		return this.classAverage;
	}

	public float classAverageProgress() {
		return this.classAverageProgress;
	}

	public boolean classAverageMaxed() {
		return this.classAverageMaxed;
	}

	public String classAverageHover() {
		return this.classAverageHover;
	}

	public List<ClassEntry> classes() {
		return this.classes;
	}

	public ModeStats mode(boolean masterMode) {
		return masterMode ? this.master : this.normal;
	}

	public ModeStats normal() {
		return this.normal;
	}

	public ModeStats master() {
		return this.master;
	}

	public boolean expertRing() {
		return this.expertRing;
	}

	public int hecatombLevel() {
		return this.hecatombLevel;
	}

	public double scarfBonus() {
		return this.scarfBonus;
	}

	public double classEssenceBonus(String classId) {
		if (classId == null || this.classEssenceBonuses.isEmpty()) {
			return 0;
		}
		Double value = this.classEssenceBonuses.get(classId.toLowerCase());
		return value == null ? 0 : value;
	}

	public double mayorFactor() {
		return this.mayorFactor;
	}

	public String mayorName() {
		return this.mayorName;
	}

	public static DungeonSnapshot empty() {
		return new DungeonSnapshot(
			0, 0F, 0F, false, "Loading…",
			0L, 0D,
			0F, 0F, false, "Loading…",
			List.of(),
			ModeStats.empty(),
			ModeStats.empty(),
			false, 0, 0, Map.of(), 1.0, ""
		);
	}
}
