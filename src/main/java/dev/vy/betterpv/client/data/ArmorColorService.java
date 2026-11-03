package dev.vy.betterpv.client.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.networth.NbtAttrs;
import dev.vy.betterpv.client.neu.NeuRepoCache;
import dev.vy.betterpv.client.price.HypixelItemsCache;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;

/** Classifies profile armour colours using the Hypixel items cache, the NEU repo and bundled hex lists. */
public final class ArmorColorService {
	private static volatile List<SeymourColors.Target> targets = List.of();
	private static volatile long targetsKey = -1L;

	private static final ArmorColorClassifier.Lookups LOOKUPS = new ArmorColorClassifier.Lookups() {
		@Override
		public Integer originalColor(String id) {
			JsonObject item = HypixelItemsCache.get(id);
			JsonElement color = item == null ? null : item.get("color");
			return color != null && color.isJsonPrimitive() ? ArmorColorClassifier.parseCommaRgb(color.getAsString()) : null;
		}

		@Override
		public String dyeName(String dyeId) {
			String name = ArmorColorClassifier.dyeDisplayName(NeuRepoCache.get(dyeId.toUpperCase(Locale.ROOT)));
			return name != null ? name : ArmorColorClassifier.prettyDyeId(dyeId);
		}

		@Override
		public Collection<SeymourColors.Target> seymourTargets() {
			return ArmorColorService.targets();
		}
	};

	private ArmorColorService() {
	}

	/**
	 * Null for slots without decoded NBT (UI placeholders) or without a leather colour. Hypixel items data is
	 * needed before anything can be called original or exotic; until it loads colours stay unlabelled.
	 */
	public static ArmorColorClassifier.Result classify(InventorySnapshot.Slot slot) {
		if (slot == null || slot.isEmpty() || slot.dyeColor() == null) {
			return null;
		}
		CompoundTag ea = slot.extraAttributes();
		if (ea == null) {
			return null;
		}
		long timestamp = NbtAttrs.longValue(ea, "timestamp", 0L);
		if (timestamp > 0L && timestamp < 10_000_000_000L) {
			timestamp *= 1000L;
		}
		ArmorColorClassifier.Input input = new ArmorColorClassifier.Input(
			slot.id(), slot.dyeColor(), NbtAttrs.string(ea, "color"), NbtAttrs.string(ea, "dye_item"), timestamp
		);
		return ArmorColorClassifier.classify(input, ArmorColorData.bundled(), LOOKUPS);
	}

	private static List<SeymourColors.Target> targets() {
		Collection<JsonObject> hypixel = HypixelItemsCache.allItems();
		long key = ((long) hypixel.size() << 32) | (NeuRepoCache.size() & 0xFFFFFFFFL);
		if (key == targetsKey) {
			return targets;
		}
		synchronized (ArmorColorService.class) {
			if (key != targetsKey) {
				List<JsonObject> dyes = new ArrayList<>();
				for (JsonObject item : NeuRepoCache.items()) {
					JsonElement id = item.get("internalname");
					if (id != null && id.isJsonPrimitive() && id.getAsString().startsWith("DYE_")) {
						dyes.add(item);
					}
				}
				targets = ArmorColorClassifier.buildTargets(hypixel, dyes, modernDyes(), ArmorColorData.bundled());
				targetsKey = key;
			}
			return targets;
		}
	}

	private static Map<String, Integer> modernDyes() {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (DyeColor dye : DyeColor.values()) {
			out.put(ArmorColorClassifier.prettyDyeId(dye.getName()), dye.getTextureDiffuseColor() & 0xFFFFFF);
		}
		return out;
	}
}
