package dev.vy.vypv.client.data;

import java.util.List;

/** Decoded SkyBlock inventories for the Inventories tab. */
public final class InventorySnapshot {
	public record Slot(
		String id,
		int count,
		List<String> lore,
		String displayName,
		Integer dyeColor,
		String skullValue,
		String skullSignature
	) {
		public static Slot empty() {
			return null;
		}

		public boolean isEmpty() {
			return id == null || id.isBlank();
		}
	}

	public record Page(String title, List<Slot> slots, int columns, int equippedColumn) {
		public Page(String title, List<Slot> slots, int columns) {
			this(title, slots, columns, -1);
		}

		public Page {
			// Empty inventory slots are intentionally null; List.copyOf forbids nulls.
			if (slots == null) {
				slots = List.of();
			} else {
				slots = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(slots));
			}
			columns = Math.max(1, columns);
			title = title == null ? "" : title;
		}
	}

	/** One accessory tuning template (non-zero stats only). */
	public record TuningTemplate(int slot, List<StatPoint> stats) {
		public TuningTemplate {
			stats = stats == null ? List.of() : List.copyOf(stats);
		}
	}

	public record StatPoint(String id, String label, int value) {
	}

	/** Magical power / power stone / tuning for the Accessory Bag pane. */
	public record AccessoryInfo(int magicalPower, String selectedPower, List<TuningTemplate> tunings) {
		public AccessoryInfo {
			selectedPower = selectedPower == null ? "" : selectedPower;
			tunings = tunings == null ? List.of() : List.copyOf(tunings);
		}

		public static AccessoryInfo empty() {
			return new AccessoryInfo(0, "", List.of());
		}
	}

	/**
	 * Named loadout preset: equipment column + armor column + metadata.
	 * HOTM/HOTF are not in the public API loadout object, so they are omitted.
	 */
	public record Loadout(
		String name,
		List<Slot> equipment,
		List<Slot> armor,
		String powerStone,
		Integer tuningSlot,
		List<StatPoint> tuning,
		Slot pet,
		String petLabel
	) {
		public Loadout {
			name = name == null || name.isBlank() ? "Loadout" : name;
			equipment = padSlots(equipment, 4);
			armor = padSlots(armor, 4);
			powerStone = powerStone == null ? "" : powerStone;
			tuning = tuning == null ? List.of() : List.copyOf(tuning);
			petLabel = petLabel == null ? "" : petLabel;
		}

		private static List<Slot> padSlots(List<Slot> slots, int size) {
			List<Slot> out = new java.util.ArrayList<>(size);
			if (slots != null) {
				out.addAll(slots);
			}
			while (out.size() < size) {
				out.add(null);
			}
			if (out.size() > size) {
				out = new java.util.ArrayList<>(out.subList(0, size));
			}
			return java.util.Collections.unmodifiableList(out);
		}
	}

	private final Page inventory;
	private final List<Page> enderChest;
	private final List<Page> backpacks;
	private final List<Page> wardrobe;
	private final List<Page> equipmentWardrobe;
	private final List<Loadout> loadouts;
	private final List<Page> sacks;
	private final Page fishingBag;
	private final Page potionBag;
	private final Page quiver;
	private final List<Page> accessoryBag;
	private final AccessoryInfo accessoryInfo;
	private final Page timePocket;
	private final Page personalVault;

	public InventorySnapshot(
		Page inventory,
		List<Page> enderChest,
		List<Page> backpacks,
		List<Page> wardrobe,
		List<Page> equipmentWardrobe,
		List<Loadout> loadouts,
		List<Page> sacks,
		Page fishingBag,
		Page potionBag,
		Page quiver,
		List<Page> accessoryBag,
		AccessoryInfo accessoryInfo,
		Page timePocket,
		Page personalVault
	) {
		this.inventory = inventory == null ? emptyPage("Inventory", 9) : inventory;
		this.enderChest = enderChest == null ? List.of() : List.copyOf(enderChest);
		this.backpacks = backpacks == null ? List.of() : List.copyOf(backpacks);
		this.wardrobe = wardrobe == null ? List.of() : List.copyOf(wardrobe);
		this.equipmentWardrobe = equipmentWardrobe == null ? List.of() : List.copyOf(equipmentWardrobe);
		this.loadouts = loadouts == null ? List.of() : List.copyOf(loadouts);
		this.sacks = sacks == null || sacks.isEmpty()
			? List.of(emptyPage("Sacks", 9))
			: List.copyOf(sacks);
		this.fishingBag = fishingBag == null ? emptyPage("Fishing Bag", 9) : fishingBag;
		this.potionBag = potionBag == null ? emptyPage("Potion Bag", 9) : potionBag;
		this.quiver = quiver == null ? emptyPage("Quiver", 9) : quiver;
		this.accessoryBag = accessoryBag == null || accessoryBag.isEmpty()
			? List.of(emptyPage("Accessory Bag", 9))
			: List.copyOf(accessoryBag);
		this.accessoryInfo = accessoryInfo == null ? AccessoryInfo.empty() : accessoryInfo;
		this.timePocket = timePocket == null ? emptyPage("Time Pocket", 9) : timePocket;
		this.personalVault = personalVault == null ? emptyPage("Personal Vault", 9) : personalVault;
	}

	public static InventorySnapshot empty() {
		return new InventorySnapshot(
			emptyPage("Inventory", 9),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(emptyPage("Sacks", 9)),
			emptyPage("Fishing Bag", 9),
			emptyPage("Potion Bag", 9),
			emptyPage("Quiver", 9),
			List.of(emptyPage("Accessory Bag", 9)),
			AccessoryInfo.empty(),
			emptyPage("Time Pocket", 9),
			emptyPage("Personal Vault", 9)
		);
	}

	public static Page emptyPage(String title, int columns) {
		return new Page(title, List.of(), columns);
	}

	public Page inventory() {
		return this.inventory;
	}

	public List<Page> enderChest() {
		return this.enderChest;
	}

	public List<Page> backpacks() {
		return this.backpacks;
	}

	public List<Page> wardrobe() {
		return this.wardrobe;
	}

	public List<Page> equipmentWardrobe() {
		return this.equipmentWardrobe;
	}

	public List<Loadout> loadouts() {
		return this.loadouts;
	}

	public List<Page> sacks() {
		return this.sacks;
	}

	public Page fishingBag() {
		return this.fishingBag;
	}

	public Page potionBag() {
		return this.potionBag;
	}

	public Page quiver() {
		return this.quiver;
	}

	public List<Page> accessoryBag() {
		return this.accessoryBag;
	}

	public AccessoryInfo accessoryInfo() {
		return this.accessoryInfo;
	}

	public Page timePocket() {
		return this.timePocket;
	}

	public Page personalVault() {
		return this.personalVault;
	}
}
