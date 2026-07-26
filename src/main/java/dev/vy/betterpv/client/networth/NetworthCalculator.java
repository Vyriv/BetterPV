package dev.vy.betterpv.client.networth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class NetworthCalculator {
	private NetworthCalculator() {
	}

	public static NetworthBreakdown calculate(JsonObject member, JsonObject profileRoot, JsonObject museumMember) {
		if (member == null) {
			return NetworthBreakdown.empty("No profile member");
		}
		return calculate(
			member,
			profileRoot,
			museumMember,
			InventoryDecoder.parseCategories(member, museumMember),
			true,
			NetworthMode.SoulboundFilter.ALL
		);
	}

	public static NetworthBreakdown calculate(
		JsonObject member,
		JsonObject profileRoot,
		JsonObject museumMember,
		Map<String, List<InventoryDecoder.Stack>> categories
	) {
		return calculate(member, profileRoot, museumMember, categories, true, NetworthMode.SoulboundFilter.ALL);
	}

	public static NetworthBreakdown calculate(
		JsonObject member,
		JsonObject profileRoot,
		JsonObject museumMember,
		Map<String, List<InventoryDecoder.Stack>> categories,
		boolean includeCosmetics
	) {
		return calculate(member, profileRoot, museumMember, categories, includeCosmetics, NetworthMode.SoulboundFilter.ALL);
	}

	public static NetworthBreakdown calculate(
		JsonObject member,
		JsonObject profileRoot,
		JsonObject museumMember,
		Map<String, List<InventoryDecoder.Stack>> categories,
		NetworthMode mode
	) {
		if (mode == null) {
			mode = NetworthMode.NORMAL;
		}
		return calculate(
			member,
			profileRoot,
			museumMember,
			categories,
			mode.includeCosmetics(),
			mode.soulboundFilter()
		);
	}

	public static NetworthBreakdown calculate(
		JsonObject member,
		JsonObject profileRoot,
		JsonObject museumMember,
		Map<String, List<InventoryDecoder.Stack>> categories,
		boolean includeCosmetics,
		NetworthMode.SoulboundFilter filter
	) {
		NetworthData.ensureLoaded();
		if (member == null) {
			return NetworthBreakdown.empty("No profile member");
		}
		if (filter == null) {
			filter = NetworthMode.SoulboundFilter.ALL;
		}
		if (categories == null) {
			categories = InventoryDecoder.parseCategories(member, museumMember);
		}

		List<NetworthBreakdown.Line> lines = new ArrayList<>();
		double total = 0;

		for (var entry : categories.entrySet()) {
			if ("pets".equals(entry.getKey())) {
				continue;
			}
			boolean museumCategory = "museum".equals(entry.getKey());
			double sum = 0;
			for (InventoryDecoder.Stack stack : entry.getValue()) {
				boolean soulbound = stack.soulbound() || museumCategory;
				if (!filter.accepts(soulbound)) {
					continue;
				}
				sum += ItemWorth.value(stack, includeCosmetics);
			}
			if (sum > 0) {
				lines.add(new NetworthBreakdown.Line(entry.getKey(), sum));
				total += sum;
			}
		}

		double pets = petsValue(member, includeCosmetics, filter);
		if (pets > 0) {
			lines.add(new NetworthBreakdown.Line("pets", pets));
			total += pets;
		}

		if (filter.includesLiquid()) {
			double purse = purse(member);
			double bank = bank(profileRoot);
			double personalBank = personalBank(member);
			if (purse > 0) {
				lines.add(new NetworthBreakdown.Line("purse", purse));
				total += purse;
			}
			if (bank > 0) {
				lines.add(new NetworthBreakdown.Line("bank", bank));
				total += bank;
			}
			if (personalBank > 0) {
				lines.add(new NetworthBreakdown.Line("personal_bank", personalBank));
				total += personalBank;
			}
		}

		String note = "";
		boolean anyItems = categories.values().stream().anyMatch(list -> !list.isEmpty());
		if (!anyItems && pets <= 0) {
			note = "Inventory API may be disabled";
		}
		lines.sort((a, b) -> Double.compare(b.value(), a.value()));
		return new NetworthBreakdown(total, lines, note);
	}

	private static double petsValue(
		JsonObject member,
		boolean includeCosmetics,
		NetworthMode.SoulboundFilter filter
	) {
		JsonObject petsData = obj(member.get("pets_data"));
		JsonArray pets = petsData != null && petsData.has("pets") && petsData.get("pets").isJsonArray()
			? petsData.getAsJsonArray("pets")
			: (member.has("pets") && member.get("pets").isJsonArray() ? member.getAsJsonArray("pets") : null);
		if (pets == null) {
			return 0;
		}
		double total = 0;
		for (JsonElement element : pets) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject pet = element.getAsJsonObject();
			if (!filter.accepts(PetWorth.isSoulbound(pet))) {
				continue;
			}
			total += PetWorth.value(pet, includeCosmetics);
		}
		return total;
	}

	private static double purse(JsonObject member) {
		JsonObject currencies = obj(member.get("currencies"));
		if (currencies != null) {
			Double purse = num(currencies.get("coin_purse"));
			if (purse != null) {
				return purse;
			}
			Double alt = num(currencies.get("purse"));
			if (alt != null) {
				return alt;
			}
		}
		Double direct = num(member.get("coin_purse"));
		return direct == null ? 0 : direct;
	}

	private static double bank(JsonObject profileRoot) {
		JsonObject banking = obj(profileRoot == null ? null : profileRoot.get("banking"));
		Double balance = banking == null ? null : num(banking.get("balance"));
		return balance == null ? 0 : balance;
	}

	private static double personalBank(JsonObject member) {
		JsonObject profile = obj(member.get("profile"));
		Double balance = profile == null ? null : num(profile.get("bank_account"));
		return balance == null ? 0 : balance;
	}

	private static JsonObject obj(JsonElement element) {
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	private static Double num(JsonElement element) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		return element.getAsDouble();
	}
}
