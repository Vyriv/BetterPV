package dev.vy.vypv.client.data;

import java.util.List;
import java.util.UUID;

/**
 * UI-facing profile snapshot. Starts as loading placeholder, then filled from Hypixel.
 */
public final class ProfileSnapshot {
	public record SkillEntry(String id, String name, int level, float progress, boolean maxed, String xpHover) {
	}

	public record SlayerEntry(String id, String name, int tier, float progress, boolean maxed, String xpHover) {
	}

	private final String playerName;
	private final UUID playerUuid;
	private final String profileName;
	private final int skyBlockLevel;
	private final int skyBlockXpIntoLevel;
	private final String weightText;
	private final String networthText;
	private final List<SkillEntry> skills;
	private final List<SlayerEntry> slayers;

	public ProfileSnapshot(
		String playerName,
		UUID playerUuid,
		String profileName,
		int skyBlockLevel,
		int skyBlockXpIntoLevel,
		String weightText,
		String networthText,
		List<SkillEntry> skills,
		List<SlayerEntry> slayers
	) {
		this.playerName = playerName;
		this.playerUuid = playerUuid;
		this.profileName = profileName;
		this.skyBlockLevel = skyBlockLevel;
		this.skyBlockXpIntoLevel = Math.max(0, Math.min(100, skyBlockXpIntoLevel));
		this.weightText = weightText;
		this.networthText = networthText;
		this.skills = List.copyOf(skills);
		this.slayers = List.copyOf(slayers);
	}

	public String playerName() {
		return this.playerName;
	}

	public UUID playerUuid() {
		return this.playerUuid;
	}

	public String profileName() {
		return this.profileName;
	}

	public int skyBlockLevel() {
		return this.skyBlockLevel;
	}

	public int skyBlockXpIntoLevel() {
		return this.skyBlockXpIntoLevel;
	}

	public float skyBlockProgress() {
		return this.skyBlockXpIntoLevel / 100.0f;
	}

	public String weightText() {
		return this.weightText;
	}

	public String networthText() {
		return this.networthText;
	}

	public List<SkillEntry> skills() {
		return this.skills;
	}

	public List<SlayerEntry> slayers() {
		return this.slayers;
	}

	public ProfileSnapshot withWeightText(String weightText) {
		return new ProfileSnapshot(
			this.playerName, this.playerUuid, this.profileName, this.skyBlockLevel, this.skyBlockXpIntoLevel,
			weightText, this.networthText, this.skills, this.slayers
		);
	}

	public static ProfileSnapshot loading(String name) {
		String player = name == null || name.isBlank() ? "Player" : name.trim();
		List<SkillEntry> skills = List.of(
			entry("combat", "Combat"), entry("foraging", "Foraging"),
			entry("farming", "Farming"), entry("enchanting", "Enchanting"),
			entry("mining", "Mining"), entry("alchemy", "Alchemy"),
			entry("fishing", "Fishing"), entry("carpentry", "Carpentry"),
			entry("taming", "Taming"), entry("hunting", "Hunting")
		);
		List<SlayerEntry> slayers = List.of(
			slayer("zombie", "Revenant"), slayer("enderman", "Enderman"),
			slayer("spider", "Tarantula"), slayer("blaze", "Blaze"),
			slayer("wolf", "Sven"), slayer("vampire", "Vampire")
		);
		return new ProfileSnapshot(player, null, "…", 0, 0, "…", "—", skills, slayers);
	}

	private static SkillEntry entry(String id, String name) {
		return new SkillEntry(id, name, 0, 0F, false, "Loading…");
	}

	private static SlayerEntry slayer(String id, String name) {
		return new SlayerEntry(id, name, 0, 0F, false, "Loading…");
	}
}
