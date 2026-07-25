package dev.vy.betterpv.client.gui.inventories;

import dev.vy.betterpv.client.data.InventorySnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.SkyBlockStats;
import dev.vy.betterpv.client.gui.nav.InventoryPane;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Left grid renderer + page controls for Inventories. Right pane buttons stay in ProfileViewerScreen. */
public final class InventoryPage {
	private static final int SLOT = 18;
	private static final int SLOT_GAP = 2;
	private static final int PAGE_BTN = 18;
	private static final int ARMOR_GAP = 8;
	/** Same spacing as between inventory rows so the hotbar sits flush under the 3×9 grid. */
	private static final int HOTBAR_GAP = SLOT_GAP;
	private static final int GRID_PAD = 4;
	/** Small gap under the title for non-accessory-bag previews. */
	private static final int PREVIEW_TOP_PAD = 6;
	private static final int COL_GAP = 4;
	private static final int META_GAP = 6;
	private static final int LOADOUT_GAP = 14;
	private static final int LOADOUTS_PER_PAGE = 2;
	/** Fixed band under the title so every pane’s preview shares the same top edge. */
	private static final int META_BAND_LINES = 2;
	private static final int SEARCH_HIGHLIGHT = 0xFF55FF55;

	private InventorySnapshot snapshot = InventorySnapshot.empty();
	private InventoryPane pane = InventoryPane.INVENTORY;
	private final Map<InventoryPane, Integer> pageIndex = new EnumMap<>(InventoryPane.class);
	private String searchQuery = "";
	private final Set<InventoryPane> searchPanes = EnumSet.noneOf(InventoryPane.class);
	private final Map<InventoryPane, Set<Integer>> searchPages = new EnumMap<>(InventoryPane.class);
	private final List<SlotHit> hits = new ArrayList<>();
	private final List<RunnableHit> pageHits = new ArrayList<>();
	private InventorySnapshot.Slot hoveredSlot;
	private ItemStack hoveredStack = ItemStack.EMPTY;

	public void apply(InventorySnapshot snapshot) {
		this.snapshot = snapshot == null ? InventorySnapshot.empty() : snapshot;
		this.pageIndex.clear();
		rebuildSearchIndex();
		SkyBlockItemFactory.prefetch(this.snapshot);
	}

	public void setPane(InventoryPane pane) {
		if (pane != null) {
			this.pane = pane;
		}
	}

	public InventoryPane pane() {
		return this.pane;
	}

	public void setSearchQuery(String query) {
		this.searchQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		rebuildSearchIndex();
	}

	/** True when this pane contains at least one search match. */
	public boolean searchHighlightsPane(InventoryPane pane) {
		return pane != null && this.searchPanes.contains(pane);
	}

	public void render(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	) {
		this.hits.clear();
		this.pageHits.clear();
		this.hoveredSlot = null;
		this.hoveredStack = ItemStack.EMPTY;

		PvDraw.innerPanel(g, x, y, w, h);

		boolean loadouts = this.pane == InventoryPane.LOADOUTS;
		List<InventorySnapshot.Page> pages = loadouts ? List.of() : pagesFor(this.pane);
		List<InventorySnapshot.Loadout> loadoutList = this.snapshot.loadouts();
		int pageCount = loadouts
			? Math.max(1, (Math.max(1, loadoutList.size()) + LOADOUTS_PER_PAGE - 1) / LOADOUTS_PER_PAGE)
			: Math.max(1, pages.size());
		int page = clampPage(this.pane, pageCount);
		boolean multi = pageCount > 1;

		String title = titleFor(loadouts, loadoutList, pages, page);
		int headerY = y + 6;
		PvDraw.text(g, font, title, x + 8, headerY, PvDraw.COLOR_TEXT);

		int metaTop = headerY + font.lineHeight + 4;
		int metaBandH = META_BAND_LINES * font.lineHeight + (META_BAND_LINES - 1) * 2;
		// Accessory bag keeps its MP/power/tuning band; every other pane starts the grid under the title.
		int previewTop;
		if (this.pane == InventoryPane.ACCESSORY_BAG) {
			drawAccessoryMeta(g, font, x + 8, metaTop, w - 16, this.snapshot.accessoryInfo());
			previewTop = metaTop + metaBandH + 6;
		} else if (this.pane == InventoryPane.INVENTORY
			|| this.pane == InventoryPane.SACKS
			|| this.pane == InventoryPane.FISHING_BAG
			|| this.pane == InventoryPane.QUIVER
			|| this.pane == InventoryPane.TIME_POCKET) {
			// Center in the full panel so the title sits in the top margin.
			previewTop = y + 6;
		} else {
			previewTop = metaTop + PREVIEW_TOP_PAD;
		}
		int gridBottom = y + h - (multi ? PAGE_BTN + 10 : 6);
		int previewH = Math.max(SLOT, gridBottom - previewTop);
		int previewX = x + 8;
		int previewW = w - 16;

		if (loadouts) {
			drawLoadoutsPage(g, font, previewX, previewTop, previewW, previewH, loadoutList, page, mouseX, mouseY);
		} else if (this.pane == InventoryPane.INVENTORY) {
			InventorySnapshot.Page current = pages.isEmpty()
				? InventorySnapshot.emptyPage("Inventory", 9)
				: pages.get(Math.min(page, pages.size() - 1));
			drawPlayerInventory(g, font, previewX, previewTop, previewW, previewH, current.slots(), mouseX, mouseY);
		} else {
			InventorySnapshot.Page current = pages.isEmpty()
				? InventorySnapshot.emptyPage(this.pane.label().getString(), 9)
				: pages.get(Math.min(page, pages.size() - 1));
			boolean markers = this.pane == InventoryPane.WARDROBE || this.pane == InventoryPane.EQUIPMENT_WARDROBE;
			drawGrid(
				g,
				font,
				previewX,
				previewTop,
				previewW,
				previewH,
				current.columns(),
				current.slots(),
				markers,
				markers ? current.equippedColumn() : -1,
				mouseX,
				mouseY
			);
		}

		if (multi) {
			Set<Integer> matches = this.searchPages.getOrDefault(this.pane, Set.of());
			boolean highlightPrev = matches.stream().anyMatch(p -> p < page);
			boolean highlightNext = matches.stream().anyMatch(p -> p > page);
			String pagerLabel = null;
			if (this.pane == InventoryPane.SACKS && !pages.isEmpty()) {
				pagerLabel = pages.get(Math.min(page, pages.size() - 1)).title();
			}
			drawPager(g, font, x, y + h - PAGE_BTN - 6, w, page, pageCount, mouseX, mouseY, highlightPrev, highlightNext, pagerLabel);
		}
	}

	/** Drawn after the right button panel so tips are never covered by it. */
	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		if (this.hoveredSlot != null && !this.hoveredStack.isEmpty()) {
			List<Component> tip = SkyBlockItemFactory.tooltipLines(this.hoveredSlot, this.hoveredStack);
			PvTooltip.drawComponents(g, font, tip, mouseX, mouseY, screenW, screenH);
		}
	}

	public boolean mouseClicked(double mx, double my) {
		for (RunnableHit hit : this.pageHits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				hit.action.run();
				return true;
			}
		}
		return false;
	}

	public boolean mouseScrolled(double scrollY) {
		int size = this.pane == InventoryPane.LOADOUTS
			? Math.max(1, (Math.max(1, this.snapshot.loadouts().size()) + LOADOUTS_PER_PAGE - 1) / LOADOUTS_PER_PAGE)
			: Math.max(1, pagesFor(this.pane).size());
		if (size <= 1) {
			return false;
		}
		int page = clampPage(this.pane, size);
		if (scrollY > 0 && page > 0) {
			this.pageIndex.put(this.pane, page - 1);
			return true;
		}
		if (scrollY < 0 && page < size - 1) {
			this.pageIndex.put(this.pane, page + 1);
			return true;
		}
		return false;
	}

	private String titleFor(
		boolean loadouts,
		List<InventorySnapshot.Loadout> loadoutList,
		List<InventorySnapshot.Page> pages,
		int page
	) {
		if (loadouts) {
			return "Loadouts";
		}
		if (pages.isEmpty()) {
			return this.pane.label().getString();
		}
		return pages.get(Math.min(page, pages.size() - 1)).title();
	}

	private void drawAccessoryMeta(GuiGraphicsExtractor g, Font font, int x, int y, int w, InventorySnapshot.AccessoryInfo info) {
		if (info == null) {
			return;
		}
		MutableComponent line1 = Component.empty();
		line1.append(PvDraw.styled("MP ", PvDraw.COLOR_MUTED, false));
		line1.append(PvDraw.styled(String.valueOf(info.magicalPower()), 0xFF55FFFF, true));
		line1.append(PvDraw.styled("  ·  Power ", PvDraw.COLOR_MUTED, false));
		line1.append(SkyBlockStats.powerName(info.selectedPower()));
		PvDraw.text(g, font, trimComponent(font, line1, w), x, y);

		MutableComponent tune = Component.empty();
		tune.append(PvDraw.styled("Tuning ", PvDraw.COLOR_MUTED, false));
		if (info.tunings().isEmpty()) {
			tune.append(PvDraw.styled("—", PvDraw.COLOR_MUTED, false));
		} else {
			boolean first = true;
			for (InventorySnapshot.TuningTemplate template : info.tunings()) {
				if (!first) {
					tune.append(PvDraw.styled("  |  ", PvDraw.COLOR_MUTED, false));
				}
				first = false;
				tune.append(PvDraw.styled("#" + template.slot() + " ", PvDraw.COLOR_MUTED, false));
				tune.append(SkyBlockStats.tuningStats(template.stats()));
			}
		}
		PvDraw.text(g, font, trimComponent(font, tune, w), x, y + font.lineHeight + 2);
	}

	private void drawLoadoutsPage(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		List<InventorySnapshot.Loadout> all,
		int page,
		int mouseX,
		int mouseY
	) {
		if (all.isEmpty()) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED);
			return;
		}
		int start = page * LOADOUTS_PER_PAGE;
		int end = Math.min(all.size(), start + LOADOUTS_PER_PAGE);
		int count = Math.max(1, end - start);
		int cardW = (w - LOADOUT_GAP * (count - 1)) / count;
		for (int i = 0; i < count; i++) {
			InventorySnapshot.Loadout loadout = all.get(start + i);
			int cx = x + i * (cardW + LOADOUT_GAP);
			drawLoadoutCard(g, font, cx, y, cardW, h, loadout, mouseX, mouseY);
		}
	}

	/** Equipment column left of armor (both top→bottom), metadata under/ beside. */
	private void drawLoadoutCard(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		InventorySnapshot.Loadout loadout,
		int mouseX,
		int mouseY
	) {
		int gearW = SLOT + COL_GAP + SLOT;
		int startY = y + GRID_PAD;
		PvDraw.text(g, font, SkyBlockStats.loadoutName(loadout.name()), x + GRID_PAD, startY);
		int gearTop = startY + font.lineHeight + 4;
		int eqX = x + GRID_PAD;
		int armorX = eqX + SLOT + COL_GAP;
		for (int i = 0; i < 4; i++) {
			int sy = gearTop + i * (SLOT + SLOT_GAP);
			drawSlot(g, font, eqX, sy, i < loadout.equipment().size() ? loadout.equipment().get(i) : null, mouseX, mouseY);
			drawSlot(g, font, armorX, sy, i < loadout.armor().size() ? loadout.armor().get(i) : null, mouseX, mouseY);
		}

		int metaX = eqX + gearW + META_GAP;
		int metaW = Math.max(40, w - (metaX - x) - GRID_PAD);
		int my = gearTop;
		PvDraw.text(g, font, "Power", metaX, my, PvDraw.COLOR_MUTED);
		my += font.lineHeight + 1;
		PvDraw.text(g, font, trimComponent(font, SkyBlockStats.powerName(loadout.powerStone()), metaW), metaX, my);
		my += font.lineHeight + 4;

		PvDraw.text(g, font, "Tuning", metaX, my, PvDraw.COLOR_MUTED);
		my += font.lineHeight + 1;
		Component tune = SkyBlockStats.tuningStats(loadout.tuning());
		PvDraw.text(g, font, trimComponent(font, tune, metaW), metaX, my);
		my += font.lineHeight + 4;

		PvDraw.text(g, font, "Pet", metaX, my, PvDraw.COLOR_MUTED);
		my += font.lineHeight + 2;
		drawSlot(g, font, metaX, my, loadout.pet(), mouseX, mouseY);
		if (loadout.pet() == null || loadout.pet().isEmpty()) {
			g.item(new ItemStack(Items.BONE), metaX + 1, my + 1);
		}
		String petText = loadout.petLabel().isBlank() ? "—" : loadout.petLabel();
		int petColor = PvDraw.COLOR_TEXT;
		if (loadout.pet() != null && !loadout.pet().isEmpty()) {
			String tier = SkyBlockItemFactory.resolveTier(loadout.pet().id());
			if (!tier.isBlank()) {
				petColor = SkyBlockItemFactory.tierArgb(tier);
			}
		}
		PvDraw.text(
			g,
			font,
			trimToWidth(font, petText, Math.max(20, metaW - SLOT - 4)),
			metaX + SLOT + 4,
			my + (SLOT - font.lineHeight) / 2,
			petColor
		);
	}

	private void drawPlayerInventory(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		List<InventorySnapshot.Slot> slots,
		int mouseX,
		int mouseY
	) {
		InventorySnapshot.Slot[] equipment = new InventorySnapshot.Slot[4];
		InventorySnapshot.Slot[] armor = new InventorySnapshot.Slot[4];
		InventorySnapshot.Slot[] hotbar = new InventorySnapshot.Slot[9];
		InventorySnapshot.Slot[] main = new InventorySnapshot.Slot[27];
		// Layout: [equipment×4][armor×4][inv×36], with legacy fallback without equipment.
		boolean hasEquipment = slots.size() >= 44;
		int armorOff = hasEquipment ? 4 : 0;
		int invOff = armorOff + 4;
		for (int i = 0; i < 4; i++) {
			equipment[i] = hasEquipment && i < slots.size() ? slots.get(i) : null;
			armor[i] = armorOff + i < slots.size() ? slots.get(armorOff + i) : null;
		}
		for (int i = 0; i < 36; i++) {
			InventorySnapshot.Slot slot = invOff + i < slots.size() ? slots.get(invOff + i) : null;
			if (i < 9) {
				hotbar[i] = slot;
			} else {
				main[i - 9] = slot;
			}
		}

		int armorH = 4 * SLOT + 3 * SLOT_GAP;
		int mainH = 3 * SLOT + 2 * SLOT_GAP;
		// Hotbar sits under the main 3×9 grid (not under the taller armor column).
		int totalH = Math.max(armorH, mainH + HOTBAR_GAP + SLOT);
		int mainW = 9 * SLOT + 8 * SLOT_GAP;
		int gearW = SLOT + COL_GAP + SLOT;
		int totalW = gearW + ARMOR_GAP + mainW;
		int startX = x + Math.max(0, (w - totalW) / 2);
		int startY = y + Math.max(0, (h - totalH) / 2);

		int eqX = startX;
		int armorX = eqX + SLOT + COL_GAP;
		int bodyX = startX + gearW + ARMOR_GAP;
		int bodyY = startY;
		int hotbarY = bodyY + mainH + HOTBAR_GAP;

		for (int i = 0; i < 4; i++) {
			int sy = startY + i * (SLOT + SLOT_GAP);
			drawSlot(g, font, eqX, sy, equipment[i], mouseX, mouseY);
			drawSlot(g, font, armorX, sy, armor[i], mouseX, mouseY);
		}
		for (int i = 0; i < 27; i++) {
			int col = i % 9;
			int row = i / 9;
			drawSlot(g, font, bodyX + col * (SLOT + SLOT_GAP), bodyY + row * (SLOT + SLOT_GAP), main[i], mouseX, mouseY);
		}
		for (int i = 0; i < 9; i++) {
			drawSlot(g, font, bodyX + i * (SLOT + SLOT_GAP), hotbarY, hotbar[i], mouseX, mouseY);
		}

		boolean empty = true;
		for (InventorySnapshot.Slot s : slots) {
			if (s != null && !s.isEmpty()) {
				empty = false;
				break;
			}
		}
		if (empty) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, y + Math.min(h / 2, totalH / 2) - font.lineHeight / 2, PvDraw.COLOR_MUTED);
		}
	}

	private void drawPager(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int page,
		int total,
		int mouseX,
		int mouseY,
		boolean highlightPrev,
		boolean highlightNext,
		String centerLabel
	) {
		int prevX = x + 8;
		int nextX = x + w - 8 - PAGE_BTN;
		drawPageButton(g, font, prevX, y, "<", mouseX, mouseY, page > 0, highlightPrev, () -> this.pageIndex.put(this.pane, page - 1));
		drawPageButton(g, font, nextX, y, ">", mouseX, mouseY, page < total - 1, highlightNext, () -> this.pageIndex.put(this.pane, page + 1));
		String label = centerLabel != null && !centerLabel.isBlank()
			? centerLabel
			: (page + 1) + " / " + total;
		int labelMax = Math.max(40, nextX - prevX - PAGE_BTN - 16);
		if (font.width(label) > labelMax) {
			label = trimToWidth(font, label, labelMax);
		}
		PvDraw.textCentered(g, font, label, x + w / 2, y + (PAGE_BTN - font.lineHeight) / 2, PvDraw.COLOR_MUTED);
	}

	private void drawPageButton(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		String label,
		int mouseX,
		int mouseY,
		boolean enabled,
		boolean searchHighlight,
		Runnable action
	) {
		boolean hovered = enabled && mouseX >= x && mouseX < x + PAGE_BTN && mouseY >= y && mouseY < y + PAGE_BTN;
		int bg = !enabled ? 0xFF121218 : searchHighlight ? 0xFF3A2A10 : hovered ? 0xFF2A3A55 : 0xFF16161E;
		int border = searchHighlight ? SEARCH_HIGHLIGHT : hovered ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;
		PvDraw.fill(g, x, y, PAGE_BTN, PAGE_BTN, bg);
		g.outline(x, y, PAGE_BTN, PAGE_BTN, border);
		PvDraw.textCentered(g, font, label, x + PAGE_BTN / 2, y + (PAGE_BTN - font.lineHeight) / 2, enabled ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED);
		if (enabled) {
			this.pageHits.add(new RunnableHit(x, y, PAGE_BTN, PAGE_BTN, action));
		}
	}

	private void drawGrid(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int columns,
		List<InventorySnapshot.Slot> slots,
		boolean statusRow,
		int equippedColumn,
		int mouseX,
		int mouseY
	) {
		int cols = Math.max(1, columns);
		int rows = Math.max(1, (slots.size() + cols - 1) / cols);
		int statusReserve = statusRow ? SLOT + SLOT_GAP : 0;
		int maxRows = Math.max(1, (h - GRID_PAD - statusReserve + SLOT_GAP) / (SLOT + SLOT_GAP));
		rows = Math.min(rows, maxRows);
		int gridW = cols * SLOT + (cols - 1) * SLOT_GAP;
		int gridH = rows * SLOT + (rows - 1) * SLOT_GAP + statusReserve;
		int startX = x + Math.max(GRID_PAD, (w - gridW) / 2);
		int startY = y + Math.max(GRID_PAD, (h - gridH) / 2);
		int maxSlots = cols * rows;

		int shown = Math.min(slots.size(), maxSlots);
		for (int i = 0; i < shown; i++) {
			InventorySnapshot.Slot slotData = slots.get(i);
			int col = i % cols;
			int row = i / cols;
			drawSlot(g, font, startX + col * (SLOT + SLOT_GAP), startY + row * (SLOT + SLOT_GAP), slotData, mouseX, mouseY);
		}

		if (statusRow) {
			int dyeY = startY + rows * (SLOT + SLOT_GAP);
			for (int col = 0; col < cols; col++) {
				int dyeX = startX + col * (SLOT + SLOT_GAP);
				drawSlot(g, font, dyeX, dyeY, null, mouseX, mouseY);
				ItemStack dye = new ItemStack(
					col == equippedColumn ? Items.LIME_DYE : Items.LIGHT_GRAY_DYE
				);
				g.item(dye, dyeX + 1, dyeY + 1);
			}
		}

		if (slots.isEmpty() || slots.stream().allMatch(s -> s == null || s.isEmpty())) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, startY + SLOT, PvDraw.COLOR_MUTED);
		}
	}

	private void drawSlot(
		GuiGraphicsExtractor g,
		Font font,
		int sx,
		int sy,
		InventorySnapshot.Slot slot,
		int mouseX,
		int mouseY
	) {
		boolean hovered = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
		boolean match = slotMatches(slot);
		PvDraw.fill(g, sx, sy, SLOT, SLOT, match ? 0xFF2A2410 : hovered ? 0xFF2A2A38 : 0xFF101018);
		g.outline(sx, sy, SLOT, SLOT, match ? SEARCH_HIGHLIGHT : hovered ? PvDraw.COLOR_ACCENT : 0xFF2A2A35);

		if (slot != null && !slot.isEmpty()) {
			ItemStack stack = SkyBlockItemFactory.toStack(slot);
			Identifier icon = SkyBlockItemFactory.customIcon(slot.id());
			if (icon != null) {
				int tex = SkyBlockItemFactory.customIconSize(slot.id());
				g.blit(
					RenderPipelines.GUI_TEXTURED,
					icon,
					sx + 1, sy + 1,
					0, 0,
					16, 16,
					tex, tex,
					tex, tex
				);
			} else {
				g.item(stack, sx + 1, sy + 1);
			}
			if (slot.count() > 1 && slot.count() <= 64) {
				String count = String.valueOf(slot.count());
				PvDraw.textRight(g, font, count, sx + SLOT - 1, sy + SLOT - font.lineHeight, PvDraw.COLOR_TEXT);
			}
			this.hits.add(new SlotHit(sx, sy, SLOT, SLOT, slot, stack));
			if (hovered) {
				this.hoveredSlot = slot;
				this.hoveredStack = stack;
			}
		}
	}

	private void rebuildSearchIndex() {
		this.searchPanes.clear();
		this.searchPages.clear();
		if (this.searchQuery.isEmpty()) {
			return;
		}
		for (InventoryPane pane : InventoryPane.values()) {
			if (pane == InventoryPane.LOADOUTS) {
				List<InventorySnapshot.Loadout> loadouts = this.snapshot.loadouts();
				for (int i = 0; i < loadouts.size(); i++) {
					if (loadoutMatches(loadouts.get(i))) {
						int page = i / LOADOUTS_PER_PAGE;
						this.searchPanes.add(pane);
						this.searchPages.computeIfAbsent(pane, ignored -> new HashSet<>()).add(page);
					}
				}
				continue;
			}
			List<InventorySnapshot.Page> pages = pagesFor(pane);
			for (int i = 0; i < pages.size(); i++) {
				if (pageMatches(pages.get(i))) {
					this.searchPanes.add(pane);
					this.searchPages.computeIfAbsent(pane, ignored -> new HashSet<>()).add(i);
				}
			}
		}
	}

	private boolean pageMatches(InventorySnapshot.Page page) {
		if (page == null || page.slots() == null) {
			return false;
		}
		for (InventorySnapshot.Slot slot : page.slots()) {
			if (slotMatches(slot)) {
				return true;
			}
		}
		return false;
	}

	private boolean loadoutMatches(InventorySnapshot.Loadout loadout) {
		if (loadout == null) {
			return false;
		}
		for (InventorySnapshot.Slot slot : loadout.equipment()) {
			if (slotMatches(slot)) {
				return true;
			}
		}
		for (InventorySnapshot.Slot slot : loadout.armor()) {
			if (slotMatches(slot)) {
				return true;
			}
		}
		return slotMatches(loadout.pet());
	}

	private boolean slotMatches(InventorySnapshot.Slot slot) {
		if (this.searchQuery.isEmpty() || slot == null || slot.isEmpty()) {
			return false;
		}
		String id = slot.id() == null ? "" : slot.id().toLowerCase(Locale.ROOT);
		String name = plainText(slot.displayName());
		if (id.contains(this.searchQuery) || name.contains(this.searchQuery)) {
			return true;
		}
		ItemStack preview = SkyBlockItemFactory.toStack(slot);
		var customName = preview.get(DataComponents.CUSTOM_NAME);
		if (customName != null && plainText(customName.getString()).contains(this.searchQuery)) {
			return true;
		}
		if (slot.lore() != null) {
			for (String line : slot.lore()) {
				if (line != null && plainText(line).contains(this.searchQuery)) {
					return true;
				}
			}
		}
		return false;
	}

	private static String plainText(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return value.replaceAll("§.", "").toLowerCase(Locale.ROOT);
	}

	private static String trimToWidth(Font font, String value, int maxW) {
		if (value == null) {
			return "";
		}
		if (maxW <= 0 || font.width(value) <= maxW) {
			return value;
		}
		String ellipsis = "...";
		int ellipsisW = font.width(ellipsis);
		if (maxW <= ellipsisW) {
			return ellipsis;
		}
		StringBuilder sb = new StringBuilder(value);
		while (sb.length() > 0 && font.width(sb.toString()) + ellipsisW > maxW) {
			sb.setLength(sb.length() - 1);
		}
		return sb + ellipsis;
	}

	private static Component trimComponent(Font font, Component value, int maxW) {
		if (value == null) {
			return Component.empty();
		}
		if (maxW <= 0 || font.width(value) <= maxW) {
			return value;
		}
		String plain = trimToWidth(font, value.getString(), maxW);
		return PvDraw.styled(plain, PvDraw.COLOR_TEXT, false);
	}

	private List<InventorySnapshot.Page> pagesFor(InventoryPane pane) {
		return switch (pane) {
			case INVENTORY -> List.of(this.snapshot.inventory());
			case ENDER_CHEST -> this.snapshot.enderChest();
			case BACKPACKS -> this.snapshot.backpacks();
			case WARDROBE -> this.snapshot.wardrobe();
			case EQUIPMENT_WARDROBE -> this.snapshot.equipmentWardrobe();
			case LOADOUTS -> List.of();
			case SACKS -> this.snapshot.sacks();
			case FISHING_BAG -> List.of(this.snapshot.fishingBag());
			case POTION_BAG -> List.of(this.snapshot.potionBag());
			case QUIVER -> List.of(this.snapshot.quiver());
			case ACCESSORY_BAG -> this.snapshot.accessoryBag();
			case TIME_POCKET -> List.of(this.snapshot.timePocket());
			case PERSONAL_VAULT -> List.of(this.snapshot.personalVault());
		};
	}

	private int clampPage(InventoryPane pane, int size) {
		if (size <= 0) {
			return 0;
		}
		int page = this.pageIndex.getOrDefault(pane, 0);
		if (page < 0) page = 0;
		if (page >= size) page = size - 1;
		this.pageIndex.put(pane, page);
		return page;
	}

	private record SlotHit(int x, int y, int w, int h, InventorySnapshot.Slot slot, ItemStack stack) {
	}

	private record RunnableHit(int x, int y, int w, int h, Runnable action) {
	}
}
