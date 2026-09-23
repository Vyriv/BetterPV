package dev.vy.betterpv.client.networth;

import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.InventorySnapshot;
import dev.vy.betterpv.client.neu.NeuRepoCache;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;

/** Sack counts and sack UI page decoding. */
final class SackDecoder {
	/** NEU lists Rough/Flawed/Fine only - add Flawless/Perfect for each gem type. */
	static final String[] GEMSTONE_TYPES = {
		"RUBY", "JADE", "SAPPHIRE", "AMETHYST", "AMBER", "TOPAZ", "JASPER", "OPAL",
		"AQUAMARINE", "CITRINE", "ONYX", "PERIDOT"
	};

	private SackDecoder() {
	}

	static List<InventoryDecoder.Stack> parseSacks(JsonObject member, JsonObject inventory) {
		JsonObject counts = sackCountsObject(member, inventory);
		if (counts == null) {
			return List.of();
		}
		List<InventoryDecoder.Stack> out = new ArrayList<>();
		for (var entry : counts.entrySet()) {
			if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber()) {
				continue;
			}
			long amountLong = Math.max(0L, (long) entry.getValue().getAsDouble());
			if (amountLong <= 0L) {
				continue;
			}
			int amount = amountLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amountLong;
			out.add(new InventoryDecoder.Stack(entry.getKey(), amount, new CompoundTag(), List.of(), false));
		}
		return out;
	}

	static JsonObject sackCountsObject(JsonObject member, JsonObject inventory) {
		JsonObject counts = InventoryDecodeSupport.obj(member.get("sacks_counts"));
		if (counts == null && inventory != null) {
			counts = InventoryDecodeSupport.obj(inventory.get("sacks_counts"));
		}
		return counts;
	}

	/**
	 * One page per NEU sack type with every holdable item (count may be 0).
	 * NEU's Rune sack has an empty contents list - real runes go on {@code Rune Sack}.
	 * Gemstone sack is expanded with Flawless/Perfect (NEU only lists Rough-Fine).
	 */
	static List<InventorySnapshot.Page> parseSackPages(JsonObject member, JsonObject inventory) {
		JsonObject countsJson = sackCountsObject(member, inventory);
		Map<String, Integer> counts = new LinkedHashMap<>();
		if (countsJson != null) {
			for (var entry : countsJson.entrySet()) {
				if (entry.getValue().isJsonPrimitive()) {
					counts.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue().getAsInt());
				}
			}
		}
		List<InventorySnapshot.Page> pages = new ArrayList<>();
		Set<String> claimed = new HashSet<>();
		int gemstonePageIndex = -1;
		for (var sack : NeuRepoCache.sackDefinitions().entrySet()) {
			String sackName = sack.getKey();
			List<String> contents = sack.getValue();
			// NEU leaves Rune.contents empty - filled from leftovers below.
			if ("Rune".equalsIgnoreCase(sackName)) {
				continue;
			}
			if ("Gemstone".equalsIgnoreCase(sackName)) {
				contents = expandGemstoneContents(contents);
			} else if (contents.isEmpty()) {
				continue;
			}
			List<InventorySnapshot.Slot> slots = new ArrayList<>(contents.size());
			for (String itemId : contents) {
				String key = itemId.toUpperCase(Locale.ROOT);
				int amount = sackAmount(counts, key);
				slots.add(InventoryDecodeSupport.toUiSlot(
					new InventoryDecoder.Stack(itemId, amount, new CompoundTag(), List.of(), false)
				));
				claimSackId(claimed, key);
			}
			if ("Gemstone".equalsIgnoreCase(sackName)) {
				gemstonePageIndex = pages.size();
			}
			pages.add(new InventorySnapshot.Page(sackName, slots, 9));
		}

		if (gemstonePageIndex < 0) {
			List<String> gems = expandGemstoneContents(List.of());
			List<InventorySnapshot.Slot> slots = new ArrayList<>(gems.size());
			for (String itemId : gems) {
				String key = itemId.toUpperCase(Locale.ROOT);
				int amount = sackAmount(counts, key);
				slots.add(InventoryDecodeSupport.toUiSlot(
					new InventoryDecoder.Stack(itemId, amount, new CompoundTag(), List.of(), false)
				));
				claimSackId(claimed, key);
			}
			gemstonePageIndex = pages.size();
			pages.add(new InventorySnapshot.Page("Gemstone", slots, 9));
		}

		List<InventorySnapshot.Slot> gemExtras = new ArrayList<>();
		List<InventorySnapshot.Slot> runes = new ArrayList<>();
		List<InventorySnapshot.Slot> other = new ArrayList<>();
		for (var entry : counts.entrySet()) {
			String id = entry.getKey();
			if (isClaimedSackId(claimed, id)) {
				continue;
			}
			InventorySnapshot.Slot slot = InventoryDecodeSupport.toUiSlot(
				new InventoryDecoder.Stack(id, entry.getValue(), new CompoundTag(), List.of(), false)
			);
			if (isGemstoneSackId(id)) {
				gemExtras.add(slot);
				claimSackId(claimed, id);
			} else if (isRuneSackId(id)) {
				runes.add(slot);
			} else {
				other.add(slot);
			}
		}
		if (!gemExtras.isEmpty()) {
			if (gemstonePageIndex >= 0) {
				List<InventorySnapshot.Slot> merged = new ArrayList<>(pages.get(gemstonePageIndex).slots());
				merged.addAll(gemExtras);
				InventorySnapshot.Page prev = pages.get(gemstonePageIndex);
				pages.set(gemstonePageIndex, new InventorySnapshot.Page(prev.title(), merged, prev.columns(), prev.equippedColumn()));
			} else {
				pages.add(new InventorySnapshot.Page("Gemstone", gemExtras, 9));
			}
		}
		// Always include Rune so the menu has a stable entry (empty if no runes).
		pages.add(new InventorySnapshot.Page("Rune", runes, 9));
		if (!other.isEmpty()) {
			pages.add(new InventorySnapshot.Page("Other", other, 9));
		}
		return pages.isEmpty() ? List.of(InventorySnapshot.emptyPage("Sacks", 9)) : pages;
	}

	static List<String> expandGemstoneContents(List<String> contents) {
		String[] tiers = {"ROUGH_", "FLAWED_", "FINE_", "FLAWLESS_", "PERFECT_"};
		LinkedHashSet<String> seenGems = new LinkedHashSet<>();
		List<String> out = new ArrayList<>();
		if (contents != null) {
			for (String itemId : contents) {
				if (itemId == null || itemId.isBlank()) {
					continue;
				}
				String upper = itemId.toUpperCase(Locale.ROOT);
				String gem = null;
				for (String tier : tiers) {
					if (upper.startsWith(tier) && upper.endsWith("_GEM")) {
						gem = upper.substring(tier.length(), upper.length() - "_GEM".length());
						break;
					}
				}
				if (gem == null) {
					continue;
				}
				seenGems.add(gem);
			}
		}
		// Always include every known gem (incl. Citrine) so Perfect tier rows stay complete.
		for (String gem : GEMSTONE_TYPES) {
			seenGems.add(gem);
		}
		for (String gem : seenGems) {
			for (String tier : tiers) {
				out.add(tier + gem + "_GEM");
			}
		}
		return out;
	}

	static boolean isGemstoneSackId(String id) {
		if (id == null || id.isBlank()) {
			return false;
		}
		String key = id.toUpperCase(Locale.ROOT);
		return key.endsWith("_GEM") || key.endsWith("_GEMSTONE");
	}

	static boolean isRuneSackId(String id) {
		if (id == null || id.isBlank()) {
			return false;
		}
		String key = id.toUpperCase(Locale.ROOT);
		return key.startsWith("RUNE_") || key.contains("_RUNE");
	}

	static int sackAmount(Map<String, Integer> counts, String itemId) {
		String key = itemId.toUpperCase(Locale.ROOT);
		Integer amount = counts.get(key);
		if (amount == null) {
			amount = counts.get(key.replace('-', ':'));
		}
		if (amount == null) {
			amount = counts.get(key.replace(':', '-'));
		}
		return amount == null ? 0 : amount;
	}

	static void claimSackId(Set<String> claimed, String id) {
		String key = id.toUpperCase(Locale.ROOT);
		claimed.add(key);
		claimed.add(key.replace('-', ':'));
		claimed.add(key.replace(':', '-'));
	}

	static boolean isClaimedSackId(Set<String> claimed, String id) {
		String key = id.toUpperCase(Locale.ROOT);
		return claimed.contains(key)
			|| claimed.contains(key.replace('-', ':'))
			|| claimed.contains(key.replace(':', '-'));
	}
}
