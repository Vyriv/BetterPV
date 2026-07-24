package dev.vy.vypv.client.networth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

final class NbtAttrs {
	private NbtAttrs() {
	}

	static String string(CompoundTag tag, String key) {
		if (tag == null || !tag.contains(key)) {
			return null;
		}
		try {
			return tag.getStringOr(key, null);
		} catch (Throwable ignored) {
			Tag value = tag.get(key);
			if (value == null) {
				return null;
			}
			String text = value.toString();
			if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
				return text.substring(1, text.length() - 1);
			}
			return text;
		}
	}

	static int intValue(CompoundTag tag, String key, int fallback) {
		if (tag == null || !tag.contains(key)) {
			return fallback;
		}
		try {
			return tag.getIntOr(key, fallback);
		} catch (Throwable ignored) {
			Tag value = tag.get(key);
			if (value instanceof NumericTag numeric) {
				return numeric.intValue();
			}
			try {
				return Integer.parseInt(string(tag, key));
			} catch (Exception ignored2) {
				return fallback;
			}
		}
	}

	static double doubleValue(CompoundTag tag, String key, double fallback) {
		if (tag == null || !tag.contains(key)) {
			return fallback;
		}
		try {
			return tag.getDoubleOr(key, fallback);
		} catch (Throwable ignored) {
			Tag value = tag.get(key);
			if (value instanceof NumericTag numeric) {
				return numeric.doubleValue();
			}
			return fallback;
		}
	}

	static boolean has(CompoundTag tag, String key) {
		return tag != null && tag.contains(key);
	}

	static CompoundTag compound(CompoundTag tag, String key) {
		if (tag == null || !tag.contains(key)) {
			return null;
		}
		Tag value = tag.get(key);
		return value instanceof CompoundTag c ? c : null;
	}

	static Map<String, Integer> intMap(CompoundTag tag, String key) {
		Map<String, Integer> out = new LinkedHashMap<>();
		CompoundTag map = compound(tag, key);
		if (map == null) {
			return out;
		}
		for (String entryKey : map.keySet()) {
			out.put(entryKey.toUpperCase(Locale.ROOT), intValue(map, entryKey, 0));
		}
		return out;
	}

	static List<String> stringList(CompoundTag tag, String key) {
		List<String> out = new ArrayList<>();
		if (tag == null || !tag.contains(key)) {
			return out;
		}
		Tag value = tag.get(key);
		if (value instanceof ListTag list) {
			for (Tag child : list) {
				String text = child.toString();
				if (text.startsWith("\"") && text.endsWith("\"")) {
					text = text.substring(1, text.length() - 1);
				}
				out.add(text);
			}
		} else if (value instanceof CompoundTag compound) {
			for (String entryKey : compound.keySet()) {
				String s = string(compound, entryKey);
				if (s != null) {
					out.add(s);
				}
			}
		}
		return out;
	}

	static List<String> stringListValues(CompoundTag tag, String key) {
		return stringList(tag, key);
	}
}
