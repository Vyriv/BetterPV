package dev.vy.vypv.client.networth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NetworthCalculator {
	private NetworthCalculator() {
	}

	public static NetworthBreakdown calculate(JsonObject member, JsonObject profileRoot, JsonObject museumMember) {
		NetworthData.ensureLoaded();
		if (member == null) {
			return NetworthBreakdown.empty("No profile member");
		}

		Map<String, List<InventoryDecoder.Stack>> categories = InventoryDecoder.parseCategories(member, museumMember);
		List<NetworthBreakdown.Line> lines = new ArrayList<>();
		double total = 0;

		for (var entry : categories.entrySet()) {
			if ("pets".equals(entry.getKey())) {
				continue;
			}
			double sum = 0;
			for (InventoryDecoder.Stack stack : entry.getValue()) {
				sum += ItemWorth.value(stack);
			}
			if (sum > 0) {
				lines.add(new NetworthBreakdown.Line(entry.getKey(), sum));
				total += sum;
			}
		}

		double pets = petsValue(member);
		if (pets > 0) {
			lines.add(new NetworthBreakdown.Line("pets", pets));
			total += pets;
		}

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

		String note = "";
		boolean anyItems = categories.values().stream().anyMatch(list -> !list.isEmpty());
		if (!anyItems && pets <= 0) {
			note = "Inventory API may be disabled";
		}
		lines.sort((a, b) -> Double.compare(b.value(), a.value()));
		return new NetworthBreakdown(total, lines, note);
	}

	private static double petsValue(JsonObject member) {
		JsonObject petsData = obj(member.get("pets_data"));
		JsonArray pets = petsData != null && petsData.has("pets") && petsData.get("pets").isJsonArray()
			? petsData.getAsJsonArray("pets")
			: (member.has("pets") && member.get("pets").isJsonArray() ? member.getAsJsonArray("pets") : null);
		if (pets == null) {
			return 0;
		}
		double total = 0;
		for (JsonElement element : pets) {
			if (element.isJsonObject()) {
				total += PetWorth.value(element.getAsJsonObject());
			}
		}
		return total;
	}

	private static double purse(JsonObject member) {
		Double direct = num(member.get("coin_purse"));
		if (direct != null) {
			return direct;
		}
		JsonObject currencies = obj(member.get("currencies"));
		if (currencies == null) {
			return 0;
		}
		Double purse = num(currencies.get("coin_purse"));
		if (purse != null) {
			return purse;
		}
		Double alt = num(currencies.get("purse"));
		return alt == null ? 0 : alt;
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
