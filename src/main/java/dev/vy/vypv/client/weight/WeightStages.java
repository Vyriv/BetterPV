package dev.vy.vypv.client.weight;

public final class WeightStages {
	private WeightStages() {
	}

	public static String senitherStage(double total) {
		if (total >= 30_000) {
			return "No Life";
		}
		if (total >= 15_000) {
			return "End Game";
		}
		if (total >= 10_000) {
			return "Early End";
		}
		if (total >= 7_000) {
			return "Late Game";
		}
		if (total >= 2_000) {
			return "Mid Game";
		}
		return "Early Game";
	}

	public static String lilyRank(double total) {
		if (total >= 50_000) {
			return "Prestigious";
		}
		if (total >= 44_500) {
			return "Grandmaster";
		}
		if (total >= 37_000) {
			return "Diamond";
		}
		if (total >= 24_500) {
			return "Platinum";
		}
		if (total >= 17_900) {
			return "Gold";
		}
		if (total >= 13_425) {
			return "Silver";
		}
		return "Bronze";
	}
}
