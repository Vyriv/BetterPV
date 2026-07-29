package dev.vy.betterpv.client.gui.rift;

import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.InventorySnapshot;
import dev.vy.betterpv.client.data.RiftSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Rift: Overview progress + side-by-side Inventory / Ender Chest. */
public final class RiftPage {
	private static final int PAD = 6;
	private static final int GAP = 6;
	private static final int STAT_ROW = 12;
	private static final int SEP_GAP = 10;
	private static final int BAR_LABEL_GAP = 2;
	private static final int BAR_AFTER = 4;
	/** Match Inventories tab slot sizing so icons sit flush. */
	private static final int SLOT = 18;
	private static final int SLOT_GAP = 2;
	private static final int COL_GAP = 4;
	private static final int ARMOR_GAP = 8;
	private static final int HOTBAR_GAP = SLOT_GAP;
	private static final int PAGE_BTN = 18;
	private static final int CHARM_ICON = 16;
	private static final int CHARM_ROW = 18;
	private static final int ITEM_SLOT_BG = 0xFF101018;
	private static final int ITEM_SLOT_BORDER = 0xFF2A2A35;
	private static final int OBTAINED = 0xFF55FF55;
	private static final int NOT_OBTAINED = 0xFFFF5555;

	private static final int MOTES_COLOR = 0xFFC97FFF;
	private static final int ENIGMA_COLOR = 0xFF55FFFF;
	private static final int TIMECHARM_COLOR = 0xFFFFAA00;
	private static final int BURGER_COLOR = 0xFFFF8855;
	private static final int CAT_COLOR = 0xFFFF77AA;
	private static final int EYE_COLOR = 0xFFAA55FF;
	private static final int ZONE_COLOR = 0xFF88AADD;

	private RiftSnapshot snapshot = RiftSnapshot.empty();
	private final List<HoverZone> zones = new ArrayList<>();
	private final List<RunnableHit> hits = new ArrayList<>();

	private int enderPage;
	private InventorySnapshot.Slot hoveredSlot;
	private ItemStack hoveredStack = ItemStack.EMPTY;

	public void apply(RiftSnapshot snapshot) {
		this.snapshot = snapshot == null ? RiftSnapshot.empty() : snapshot;
		this.enderPage = 0;
		this.zones.clear();
		this.hits.clear();
		SkyBlockItemFactory.prefetch(toWarmSnapshot(this.snapshot));
		List<String> charmIds = new ArrayList<>();
		for (RiftSnapshot.Timecharm charm : this.snapshot.timecharms()) {
			charmIds.add(charm.itemId());
		}
		SkyBlockItemFactory.prefetchIds(charmIds);
	}

	public RiftSnapshot snapshot() {
		return this.snapshot;
	}

	public boolean mouseClicked(double mx, double my) {
		for (RunnableHit hit : this.hits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				hit.action.run();
				return true;
			}
		}
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		if (sub != PvSubTab.RIFT_INVENTORY) {
			return false;
		}
		int total = Math.max(1, this.snapshot.enderPages().size());
		if (total <= 1 || scrollY == 0) {
			return false;
		}
		int next = this.enderPage - (int) Math.signum(scrollY);
		next = Math.max(0, Math.min(total - 1, next));
		if (next == this.enderPage) {
			return false;
		}
		this.enderPage = next;
		return true;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, PvSubTab sub,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		this.zones.clear();
		this.hits.clear();
		this.hoveredSlot = null;
		this.hoveredStack = ItemStack.EMPTY;

		if (sub == PvSubTab.RIFT_INVENTORY) {
			drawInventory(g, font, x, y, w, h, mouseX, mouseY);
		} else {
			drawOverview(g, font, x, y, w, h, mouseX, mouseY);
		}
		drawHover(g, font, mouseX, mouseY, screenW, screenH);
	}

	private void drawOverview(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int rightW = Math.max(200, w * 48 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);
		drawOverviewLeft(g, font, x, y, leftW, h, mx, my);
		drawOverviewRight(g, font, x + leftW + GAP, y, rightW, h, mx, my);
	}

	private void drawOverviewLeft(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;

		ly = statLine(g, font, "Motes", FormatUtil.commas(this.snapshot.motesPurse()),
			lx, ly, lw, MOTES_COLOR) + 2;
		ly = statLine(g, font, "Lifetime motes", FormatUtil.commas(this.snapshot.lifetimeMotes()),
			lx, ly, lw, PvDraw.COLOR_MUTED);
		if (this.snapshot.visits() > 0) {
			ly = statLine(g, font, "Visits", FormatUtil.commas(this.snapshot.visits()),
				lx, ly, lw, PvDraw.COLOR_MUTED);
		}

		ly = sectionSeparator(g, font, x, ly, w);

		RiftSnapshot.VampireProgress vamp = this.snapshot.vampire();
		ly = drawLabeledBar(g, font, "Vampire", "T" + vamp.level(),
			vamp.fill(), vamp.maxed(), PvDraw.COLOR_BAR_FILL_SLAYER, vamp.hover(),
			lx, ly, lw, mx, my) + BAR_AFTER;

		ly = sectionSeparator(g, font, x, ly, w);

		String enigmaTip = "Found " + this.snapshot.enigmaFound() + " / " + RiftSnapshot.ENIGMA_MAX
			+ (this.snapshot.enigmaCloakBought() ? " · Cloak owned" : " · Cloak not bought");
		ly = drawLabeledBar(g, font, "Enigma souls",
			this.snapshot.enigmaFound() + "/" + RiftSnapshot.ENIGMA_MAX,
			this.snapshot.enigmaFill(), this.snapshot.enigmaFound() >= RiftSnapshot.ENIGMA_MAX,
			ENIGMA_COLOR, enigmaTip, lx, ly, lw, mx, my) + BAR_AFTER;

		String charmTip = this.snapshot.timecharmsSecured() + " / " + RiftSnapshot.TIMECHARM_MAX
			+ " secured in the gallery";
		ly = drawLabeledBar(g, font, "Timecharms",
			this.snapshot.timecharmsSecured() + "/" + RiftSnapshot.TIMECHARM_MAX,
			this.snapshot.timecharmFill(), this.snapshot.timecharmsSecured() >= RiftSnapshot.TIMECHARM_MAX,
			TIMECHARM_COLOR, charmTip, lx, ly, lw, mx, my) + BAR_AFTER;

		ly = drawLabeledBar(g, font, "McGrubber's burgers",
			this.snapshot.burgers() + "/" + RiftSnapshot.BURGER_MAX,
			this.snapshot.burgerFill(), this.snapshot.burgers() >= RiftSnapshot.BURGER_MAX,
			BURGER_COLOR, "Grubber stacks from Castle burgers", lx, ly, lw, mx, my) + BAR_AFTER;

		String catTip = this.snapshot.montezumaUnlocked()
			? "Montezuma unlocked"
				+ (this.snapshot.montezumaTier().isBlank() ? "" : " · " + prettyTier(this.snapshot.montezumaTier()))
			: "Montezuma not unlocked";
		ly = drawLabeledBar(g, font, "Montezuma's Souls",
			this.snapshot.catsFound() + "/" + RiftSnapshot.MONTEZUMA_CATS_MAX,
			this.snapshot.catsFill(), this.snapshot.catsFound() >= RiftSnapshot.MONTEZUMA_CATS_MAX,
			CAT_COLOR, catTip, lx, ly, lw, mx, my) + BAR_AFTER;

		drawLabeledBar(g, font, "Porhtal eyes",
			this.snapshot.eyesKilled() + "/" + RiftSnapshot.EYES_MAX,
			this.snapshot.eyesFill(), this.snapshot.eyesKilled() >= RiftSnapshot.EYES_MAX,
			EYE_COLOR, "Rogue eyes calmed for Porhtal", lx, ly, lw, mx, my);
	}

	private void drawOverviewRight(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Timecharms", lx, ly, TIMECHARM_COLOR);
		ly += STAT_ROW + 2;

		for (RiftSnapshot.Timecharm charm : this.snapshot.timecharms()) {
			if (ly + CHARM_ROW > bottom) {
				break;
			}
			ly = drawTimecharmRow(g, font, charm, lx, ly, lw, mx, my);
		}

		ly = sectionSeparator(g, font, x, ly, w);
		if (ly + STAT_ROW > bottom) {
			return;
		}

		PvDraw.text(g, font, "Zone unlocks", lx, ly, ZONE_COLOR);
		ly += STAT_ROW;
		List<String> zones = this.snapshot.purchasedBoundaries();
		if (zones.isEmpty()) {
			PvDraw.text(g, font, "None purchased", lx, ly, PvDraw.COLOR_MUTED);
			return;
		}
		PvDraw.text(g, font, zones.size() + " boundaries", lx, ly, PvDraw.COLOR_MUTED);
		ly += STAT_ROW + 2;
		for (String zone : zones) {
			if (ly + STAT_ROW > bottom) {
				break;
			}
			String label = InventoryDecoder.prettyWords(zone);
			PvDraw.text(g, font, trim(font, label, lw), lx, ly, PvDraw.COLOR_TEXT);
			this.zones.add(new HoverZone(lx, ly, lw, STAT_ROW, List.of(
				PvTooltip.Line.title(label, PvDraw.COLOR_TEXT),
				PvTooltip.Line.divider(),
				PvTooltip.Line.meta(zone)
			)));
			ly += STAT_ROW;
		}

		if (!this.snapshot.foundCats().isEmpty() && ly + SEP_GAP + STAT_ROW * 2 < bottom) {
			ly = sectionSeparator(g, font, x, ly, w);
			PvDraw.text(g, font, "Montezuma's Souls", lx, ly, CAT_COLOR);
			ly += STAT_ROW;
			for (String cat : this.snapshot.foundCats()) {
				if (ly + STAT_ROW > bottom) {
					break;
				}
				String label = InventoryDecoder.prettyWords(cat);
				PvDraw.text(g, font, trim(font, label, lw), lx, ly, PvDraw.COLOR_TEXT);
				ly += STAT_ROW;
			}
		}

		if (!this.snapshot.killedEyes().isEmpty() && ly + SEP_GAP + STAT_ROW * 2 < bottom) {
			ly = sectionSeparator(g, font, x, ly, w);
			PvDraw.text(g, font, "Killed eyes", lx, ly, EYE_COLOR);
			ly += STAT_ROW;
			for (String eye : this.snapshot.killedEyes()) {
				if (ly + STAT_ROW > bottom) {
					break;
				}
				String label = InventoryDecoder.prettyWords(eye);
				PvDraw.text(g, font, trim(font, label, lw), lx, ly, PvDraw.COLOR_TEXT);
				ly += STAT_ROW;
			}
		}
	}

	private int drawTimecharmRow(
		GuiGraphicsExtractor g, Font font, RiftSnapshot.Timecharm charm,
		int x, int y, int w, int mx, int my
	) {
		ItemStack icon = SkyBlockItemFactory.toStack(new InventorySnapshot.Slot(
			charm.itemId(), 1, List.of(), charm.name(), null, null, null
		));

		int iconY = y + Math.max(0, (CHARM_ROW - CHARM_ICON) / 2);
		drawItemIcon(g, icon, charm.itemId(), x, iconY, CHARM_ICON);

		int textX = x + CHARM_ICON + 4;
		String status = charm.secured() ? "Obtained" : "Not obtained";
		int statusColor = charm.secured() ? OBTAINED : NOT_OBTAINED;
		int statusW = font.width(status);
		int nameMax = Math.max(20, w - CHARM_ICON - 4 - statusW - 6);
		String name = trim(font, charm.name(), nameMax);
		int textY = y + Math.max(0, (CHARM_ROW - font.lineHeight) / 2);
		PvDraw.text(g, font, name, textX, textY, charm.color());
		PvDraw.textRight(g, font, status, x + w, textY, statusColor);

		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title(charm.name(), charm.color()));
		tip.add(PvTooltip.Line.divider());
		tip.add(PvTooltip.Line.row(
			"Status", PvDraw.COLOR_MUTED,
			status, statusColor
		));
		if (charm.visitsToGet() > 0) {
			tip.add(PvTooltip.Line.row(
				"Visits to get", PvDraw.COLOR_MUTED,
				String.valueOf(charm.visitsToGet()), PvDraw.COLOR_TEXT
			));
		}
		if (charm.secured() && charm.securedAtMs() > 0L) {
			tip.add(PvTooltip.Line.meta("Secured " + formatAgo(charm.securedAtMs())));
		}
		this.zones.add(new HoverZone(x, y, w, CHARM_ROW, tip));
		return y + CHARM_ROW + 1;
	}

	private void drawInventory(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int leftW = (w - GAP) / 2;
		int rightW = w - leftW - GAP;
		int leftX = x;
		int rightX = x + leftW + GAP;

		PvDraw.innerPanel(g, leftX, y, leftW, h);
		PvDraw.innerPanel(g, rightX, y, rightW, h);

		int titleY = y + PAD;
		PvDraw.text(g, font, "Inventory", leftX + PAD, titleY, PvDraw.COLOR_TEXT);

		int contentTop = titleY + font.lineHeight + PREVIEW_TOP_PAD();
		drawPlayerInventory(
			g, font,
			leftX + PAD, contentTop,
			leftW - PAD * 2, h - (contentTop - y) - PAD,
			this.snapshot.inventory().slots(), mx, my
		);

		List<InventorySnapshot.Page> pages = this.snapshot.enderPages();
		int total = Math.max(1, pages.size());
		this.enderPage = Math.max(0, Math.min(this.enderPage, total - 1));
		InventorySnapshot.Page page = pages.get(this.enderPage);

		PvDraw.text(g, font, "Ender Chest", rightX + PAD, titleY, PvDraw.COLOR_TEXT);
		int gridTop = contentTop;
		int pagerY = y + h - PAD - PAGE_BTN;
		int gridBottom = pagerY - 4;
		drawGrid(
			g, font,
			rightX + PAD, gridTop,
			rightW - PAD * 2, Math.max(SLOT, gridBottom - gridTop),
			page.columns(), page.slots(), mx, my
		);
		drawPager(g, font, rightX + PAD, pagerY, rightW - PAD * 2, this.enderPage, total, mx, my, page.title());
	}

	private static int PREVIEW_TOP_PAD() {
		return 6;
	}

	private void drawPager(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int page, int total,
		int mx, int my, String centerLabel
	) {
		int prevX = x;
		int nextX = x + w - PAGE_BTN;
		drawPageButton(g, font, prevX, y, "<", mx, my, page > 0, () -> this.enderPage = Math.max(0, page - 1));
		drawPageButton(g, font, nextX, y, ">", mx, my, page < total - 1,
			() -> this.enderPage = Math.min(total - 1, page + 1));
		String label = centerLabel == null || centerLabel.isBlank()
			? (page + 1) + " / " + total
			: (page + 1) + " / " + total;
		int labelMax = Math.max(40, nextX - prevX - PAGE_BTN - 12);
		if (font.width(label) > labelMax) {
			label = trim(font, label, labelMax);
		}
		PvDraw.textCentered(g, font, label, x + w / 2, y + (PAGE_BTN - font.lineHeight) / 2, PvDraw.COLOR_MUTED);
	}

	private void drawPageButton(
		GuiGraphicsExtractor g, Font font, int x, int y, String label,
		int mx, int my, boolean enabled, Runnable action
	) {
		boolean hovered = enabled && mx >= x && mx < x + PAGE_BTN && my >= y && my < y + PAGE_BTN;
		int bg = !enabled ? 0xFF121218 : hovered ? 0xFF2A3A55 : 0xFF16161E;
		int border = hovered ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;
		PvDraw.fill(g, x, y, PAGE_BTN, PAGE_BTN, bg);
		g.outline(x, y, PAGE_BTN, PAGE_BTN, border);
		PvDraw.textCentered(g, font, label, x + PAGE_BTN / 2, y + (PAGE_BTN - font.lineHeight) / 2,
			enabled ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED);
		if (enabled) {
			this.hits.add(new RunnableHit(x, y, PAGE_BTN, PAGE_BTN, action));
		}
	}

	private void drawPlayerInventory(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
		List<InventorySnapshot.Slot> slots, int mouseX, int mouseY
	) {
		InventorySnapshot.Slot[] equipment = new InventorySnapshot.Slot[4];
		InventorySnapshot.Slot[] armor = new InventorySnapshot.Slot[4];
		InventorySnapshot.Slot[] hotbar = new InventorySnapshot.Slot[9];
		InventorySnapshot.Slot[] main = new InventorySnapshot.Slot[27];
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
			PvDraw.textCentered(g, font, "Empty", x + w / 2, y + Math.min(h / 2, totalH / 2) - font.lineHeight / 2,
				PvDraw.COLOR_MUTED);
		}
	}

	private void drawGrid(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
		int columns, List<InventorySnapshot.Slot> slots, int mouseX, int mouseY
	) {
		int cols = Math.max(1, columns);
		int rows = Math.max(1, (slots.size() + cols - 1) / cols);
		int maxRows = Math.max(1, (h + SLOT_GAP) / (SLOT + SLOT_GAP));
		rows = Math.min(rows, maxRows);
		int gridW = cols * SLOT + (cols - 1) * SLOT_GAP;
		int gridH = rows * SLOT + (rows - 1) * SLOT_GAP;
		int startX = x + Math.max(0, (w - gridW) / 2);
		int startY = y + Math.max(0, (h - gridH) / 2);
		int shown = Math.min(slots.size(), cols * rows);
		for (int i = 0; i < shown; i++) {
			int col = i % cols;
			int row = i / cols;
			drawSlot(g, font, startX + col * (SLOT + SLOT_GAP), startY + row * (SLOT + SLOT_GAP),
				slots.get(i), mouseX, mouseY);
		}
		if (slots.isEmpty() || slots.stream().allMatch(s -> s == null || s.isEmpty())) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, startY + SLOT, PvDraw.COLOR_MUTED);
		}
	}

	private void drawSlot(
		GuiGraphicsExtractor g, Font font, int sx, int sy, InventorySnapshot.Slot slot, int mouseX, int mouseY
	) {
		boolean hovered = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
		PvDraw.fill(g, sx, sy, SLOT, SLOT, hovered ? 0xFF2A2A38 : ITEM_SLOT_BG);
		g.outline(sx, sy, SLOT, SLOT, hovered ? PvDraw.COLOR_ACCENT : ITEM_SLOT_BORDER);
		if (slot == null || slot.isEmpty()) {
			return;
		}
		ItemStack stack = SkyBlockItemFactory.toStack(slot);
		drawItemIcon(g, stack, slot.id(), sx + 1, sy + 1, 16);
		if (slot.count() > 1 && slot.count() <= 64) {
			PvDraw.textRight(g, font, String.valueOf(slot.count()), sx + SLOT - 1, sy + SLOT - font.lineHeight,
				PvDraw.COLOR_TEXT);
		}
		if (hovered) {
			this.hoveredSlot = slot;
			this.hoveredStack = stack;
		}
	}

	private static void drawItemIcon(
		GuiGraphicsExtractor g, ItemStack stack, String skyblockId, int x, int y, int size
	) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		Identifier icon = SkyBlockItemFactory.customIcon(skyblockId);
		if (icon != null) {
			int tex = SkyBlockItemFactory.customIconSize(skyblockId);
			int pad = Math.max(0, (size - 16) / 2);
			g.blit(
				RenderPipelines.GUI_TEXTURED,
				icon,
				x + pad, y + pad,
				0, 0,
				16, 16,
				tex, tex,
				tex, tex
			);
			return;
		}
		int pad = Math.max(0, (size - 16) / 2);
		g.item(stack, x + pad, y + pad);
	}

	private int drawLabeledBar(
		GuiGraphicsExtractor g, Font font, String label, String value, float fill, boolean maxed,
		int color, String hover, int x, int y, int w, int mx, int my
	) {
		PvDraw.labeledBar(g, font, label, value, fill, x, y, w, color, maxed);
		int bottom = y + font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT;
		if (hover != null && !hover.isBlank() && mx >= x && mx < x + w && my >= y && my < bottom) {
			this.zones.add(new HoverZone(x, y, w, bottom - y, List.of(PvTooltip.Line.of(hover, PvDraw.COLOR_TEXT))));
		}
		return bottom;
	}

	private static int sectionSeparator(GuiGraphicsExtractor g, Font font, int panelX, int y, int panelW) {
		int lineInset = PAD + 4;
		int lineW = Math.max(0, panelW - lineInset * 2);
		int lineY = y + (SEP_GAP - 1) / 2;
		if (lineW > 0) {
			PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, 0x33FFFFFF);
		}
		return y + SEP_GAP;
	}

	private static int statLine(
		GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int valueColor
	) {
		PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, value == null || value.isBlank() ? "-" : value, x + w, y, valueColor);
		return y + STAT_ROW;
	}

	private void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		if (this.hoveredSlot != null) {
			List<Component> tip = SkyBlockItemFactory.tooltipLines(this.hoveredSlot, this.hoveredStack);
			if (tip != null && !tip.isEmpty()) {
				PvTooltip.drawComponents(g, font, tip, mx, my, screenW, screenH);
				return;
			}
		}
		for (HoverZone zone : this.zones) {
			if (mx >= zone.x && mx < zone.x + zone.w && my >= zone.y && my < zone.y + zone.h) {
				PvTooltip.drawStyled(g, font, zone.lines, mx, my, screenW, screenH);
				return;
			}
		}
	}

	private static InventorySnapshot toWarmSnapshot(RiftSnapshot rift) {
		return new InventorySnapshot(
			rift.inventory(),
			rift.enderPages(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			InventorySnapshot.emptyPage("Fishing Bag", 9),
			InventorySnapshot.emptyPage("Potion Bag", 9),
			InventorySnapshot.emptyPage("Quiver", 9),
			List.of(),
			InventorySnapshot.AccessoryInfo.empty(),
			InventorySnapshot.emptyPage("Time Pocket", 9),
			InventorySnapshot.emptyPage("Personal Vault", 9)
		);
	}

	private static String prettyTier(String tier) {
		return InventoryDecoder.prettyWords(tier);
	}

	private static String trim(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (maxW <= 0 || font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "…";
		int budget = maxW - font.width(ellipsis);
		if (budget <= 0) {
			return ellipsis;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (font.width(sb.toString() + c) > budget) {
				break;
			}
			sb.append(c);
		}
		return sb + ellipsis;
	}

	private static String formatAgo(long epochMs) {
		long age = System.currentTimeMillis() - epochMs;
		if (age < 0L) {
			age = 0L;
		}
		long sec = age / 1000L;
		if (sec < 60) {
			return sec + "s ago";
		}
		long min = sec / 60L;
		if (min < 60) {
			return min + "m ago";
		}
		long hr = min / 60L;
		if (hr < 48) {
			return hr + "h ago";
		}
		long days = hr / 24L;
		if (days < 60) {
			return days + "d ago";
		}
		return (days / 30L) + "mo ago";
	}

	private record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
	}

	private record RunnableHit(int x, int y, int w, int h, Runnable action) {
	}
}
