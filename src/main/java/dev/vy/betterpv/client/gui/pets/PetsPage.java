package dev.vy.betterpv.client.gui.pets;

import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.PetSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Left ~70% pet icon grid + right ~30% selected stats. */
public final class PetsPage {
	private static final int GAP = 8;
	private static final int PAD = 8;
	private static final int MIN_SLOT = 18;
	private static final int MAX_SLOT = 32;
	private static final int SLOT_GAP = 2;
	private static final int ITEM_ICON = 16;
	private static final int SELECTED_BORDER = PvDraw.COLOR_ACCENT;
	private static final int ACTIVE_BORDER = 0xFF55FF55;

	private PetSnapshot snapshot = PetSnapshot.empty();
	private int selected = -1;
	private int scroll;
	private int gridY;
	private int gridH;
	private int maxScroll;
	private int cols = 1;
	private int slotSize = MIN_SLOT;
	private final List<SlotHit> hits = new ArrayList<>();
	private int xpBarX;
	private int xpBarY;
	private int xpBarW;
	private int xpBarH;
	private String xpHover;

	public void apply(PetSnapshot snapshot) {
		this.snapshot = snapshot == null ? PetSnapshot.empty() : snapshot;
		this.scroll = 0;
		this.selected = this.snapshot.isEmpty() ? -1 : 0;
		SkyBlockItemFactory.prefetchPets(this.snapshot);
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
		this.xpHover = null;
		this.xpBarW = 0;
		int leftW = Math.max(160, (int) Math.round(w * 0.70));
		int rightW = Math.max(110, w - leftW - GAP);
		leftW = w - rightW - GAP;
		int rightX = x + leftW + GAP;

		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, rightX, y, rightW, h);

		drawGrid(g, font, x, y, leftW, h, mouseX, mouseY);
		drawStats(g, font, rightX, y, rightW, h);

		if (this.xpHover != null
			&& mouseX >= this.xpBarX && mouseX < this.xpBarX + this.xpBarW
			&& mouseY >= this.xpBarY && mouseY < this.xpBarY + this.xpBarH) {
			PvTooltip.draw(g, font, List.of(this.xpHover), mouseX, mouseY, screenW, screenH);
		}
	}

	public boolean mouseClicked(double mx, double my) {
		for (SlotHit hit : this.hits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				this.selected = hit.index;
				return true;
			}
		}
		return false;
	}

	public boolean mouseScrolled(double scrollY) {
		if (this.maxScroll <= 0) {
			return false;
		}
		int before = this.scroll;
		int step = this.slotSize + SLOT_GAP;
		if (scrollY > 0) {
			this.scroll = Math.max(0, this.scroll - step);
		} else if (scrollY < 0) {
			this.scroll = Math.min(this.maxScroll, this.scroll + step);
		}
		return this.scroll != before;
	}

	private void drawGrid(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
		PvDraw.text(g, font, "Pets", x + PAD, y + 6, PvDraw.COLOR_TEXT);
		// Fixed top-left origin - never shift up / left / right when slot size changes.
		int gridX = x + PAD;
		this.gridY = y + 6 + font.lineHeight + 6;
		int gridW = w - PAD * 2;
		this.gridH = Math.max(0, y + h - PAD - this.gridY);

		List<PetSnapshot.Entry> pets = this.snapshot.pets();
		if (pets.isEmpty()) {
			PvDraw.textCentered(g, font, "No pets", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED);
			this.maxScroll = 0;
			return;
		}

		this.slotSize = fitSlotSize(pets.size(), gridW, this.gridH);
		this.cols = Math.max(1, (gridW + SLOT_GAP) / (this.slotSize + SLOT_GAP));
		int rows = (pets.size() + this.cols - 1) / this.cols;
		int contentH = rows * this.slotSize + Math.max(0, rows - 1) * SLOT_GAP;
		this.maxScroll = Math.max(0, contentH - this.gridH);
		this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll));

		for (int i = 0; i < pets.size(); i++) {
			int col = i % this.cols;
			int row = i / this.cols;
			int sx = gridX + col * (this.slotSize + SLOT_GAP);
			int sy = this.gridY + row * (this.slotSize + SLOT_GAP) - this.scroll;
			if (sy + this.slotSize < this.gridY || sy > this.gridY + this.gridH) {
				continue;
			}
			drawSlot(g, font, pets.get(i), i, sx, sy, mouseX, mouseY);
		}
	}

	/** Largest slot in [{@link #MIN_SLOT}, {@link #MAX_SLOT}] that still fits all pets in the grid height. */
	private static int fitSlotSize(int petCount, int gridW, int gridH) {
		int best = MIN_SLOT;
		int max = Math.min(MAX_SLOT, Math.max(MIN_SLOT, gridH));
		for (int slot = max; slot >= MIN_SLOT; slot--) {
			int cols = Math.max(1, (gridW + SLOT_GAP) / (slot + SLOT_GAP));
			int rows = (petCount + cols - 1) / cols;
			int contentH = rows * slot + Math.max(0, rows - 1) * SLOT_GAP;
			if (contentH <= gridH) {
				best = slot;
				break;
			}
		}
		return best;
	}

	private void drawSlot(
		GuiGraphicsExtractor g,
		Font font,
		PetSnapshot.Entry pet,
		int index,
		int sx,
		int sy,
		int mouseX,
		int mouseY
	) {
		int slot = this.slotSize;
		boolean selected = index == this.selected;
		boolean hovered = mouseX >= sx && mouseX < sx + slot && mouseY >= sy && mouseY < sy + slot;
		int rarity = SkyBlockItemFactory.tierArgb(pet.tier());
		int bg = raritySlotBackground(rarity, pet.active(), selected, hovered);
		int border = selected ? SELECTED_BORDER : pet.active() ? ACTIVE_BORDER : hovered ? PvDraw.COLOR_ACCENT : 0xFF2A2A35;
		PvDraw.fill(g, sx, sy, slot, slot, bg);
		g.outline(sx, sy, slot, slot, border);

		ItemStack icon = SkyBlockItemFactory.iconStack(pet.neuId());
		Identifier texture = SkyBlockItemFactory.customIcon(pet.neuId());
		int draw = Math.max(ITEM_ICON, slot - 2);
		int ix = sx + (slot - draw) / 2;
		int iy = sy + (slot - draw) / 2;
		if (texture != null) {
			int tex = SkyBlockItemFactory.customIconSize(pet.neuId());
			g.blit(RenderPipelines.GUI_TEXTURED, texture, ix, iy, 0, 0, draw, draw, tex, tex, tex, tex);
		} else if (!icon.isEmpty()) {
			// Item renders are fixed 16×16 - center inside the larger slot.
			g.item(icon, sx + (slot - ITEM_ICON) / 2, sy + (slot - ITEM_ICON) / 2);
		}

		String level = String.valueOf(pet.level());
		int levelY = sy + slot - font.lineHeight + 1;
		int levelRight = sx + slot - 1;
		PvDraw.textRight(g, font, level, levelRight + 1, levelY + 1, 0xFF000000);
		PvDraw.textRight(g, font, level, levelRight, levelY, 0xFFFFFFFF);

		this.hits.add(new SlotHit(sx, sy, slot, slot, index));
	}

	/** Dark rarity-tinted slot fill (keeps icons readable). */
	private static int raritySlotBackground(int rarityArgb, boolean active, boolean selected, boolean hovered) {
		int r = (rarityArgb >>> 16) & 0xFF;
		int green = (rarityArgb >>> 8) & 0xFF;
		int b = rarityArgb & 0xFF;
		float tint = hovered ? 0.38f : selected ? 0.32f : 0.26f;
		int outR = Math.round(16 + r * tint);
		int outG = Math.round(16 + green * tint);
		int outB = Math.round(16 + b * tint);
		if (active) {
			outG = Math.min(255, outG + 18);
			outR = Math.max(0, outR - 4);
		}
		return 0xFF000000 | (clampByte(outR) << 16) | (clampByte(outG) << 8) | clampByte(outB);
	}

	private static int clampByte(int value) {
		return Math.max(0, Math.min(255, value));
	}

	private void drawStats(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		PvDraw.text(g, font, "Details", x + PAD, y + 6, PvDraw.COLOR_TEXT);
		int cy = y + 6 + font.lineHeight + 8;
		int contentW = w - PAD * 2;
		int cx = x + PAD;

		if (this.selected < 0 || this.selected >= this.snapshot.pets().size()) {
			PvDraw.textCentered(g, font, "Select a pet", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED);
			return;
		}
		PetSnapshot.Entry pet = this.snapshot.pets().get(this.selected);

		ItemStack icon = SkyBlockItemFactory.iconStack(pet.neuId());
		Identifier texture = SkyBlockItemFactory.customIcon(pet.neuId());
		if (texture != null) {
			int tex = SkyBlockItemFactory.customIconSize(pet.neuId());
			g.blit(RenderPipelines.GUI_TEXTURED, texture, cx, cy, 0, 0, ITEM_ICON, ITEM_ICON, tex, tex, tex, tex);
		} else if (!icon.isEmpty()) {
			g.item(icon, cx, cy);
		}
		PvDraw.text(
			g,
			font,
			trim(font, pet.displayName(), contentW - ITEM_ICON - 6),
			cx + ITEM_ICON + 4,
			cy + (ITEM_ICON - font.lineHeight) / 2,
			SkyBlockItemFactory.tierArgb(pet.tier())
		);
		cy += ITEM_ICON + 8;

		cy += row(g, font, "Tier", InventoryDecoder.prettyWords(pet.tier()), cx, cy, contentW, SkyBlockItemFactory.tierArgb(pet.tier()));
		cy += row(g, font, "Level", pet.level() + " / " + pet.maxLevel(), cx, cy, contentW, PvDraw.COLOR_TEXT);

		boolean maxed = pet.level() >= pet.maxLevel();
		float progress = maxed ? 1f : pet.progressToNext();
		int xpLabelY = cy;
		PvDraw.text(g, font, "XP", cx, cy, PvDraw.COLOR_MUTED);
		cy += font.lineHeight + 2;
		PvDraw.progressBar(g, cx, cy, contentW, PvDraw.BAR_HEIGHT, progress, PvDraw.COLOR_BAR_FILL, maxed);
		this.xpBarX = cx;
		this.xpBarY = xpLabelY;
		this.xpBarW = contentW;
		this.xpBarH = (cy + PvDraw.BAR_HEIGHT) - xpLabelY;
		if (maxed) {
			double overflow = Math.max(0, pet.exp() - pet.xpMax());
			this.xpHover = "Overflow: " + FormatUtil.shortXp(overflow);
		} else {
			this.xpHover = FormatUtil.shortXp(pet.exp()) + "/" + FormatUtil.shortXp(pet.xpMax());
		}
		cy += PvDraw.BAR_HEIGHT + 8;

		cy += row(
			g,
			font,
			"Active",
			pet.active() ? "Yes" : "No",
			cx,
			cy,
			contentW,
			pet.active() ? ACTIVE_BORDER : PvDraw.COLOR_MUTED
		);

		if (pet.candyUsed() > 0) {
			cy += row(g, font, "Candy", String.valueOf(pet.candyUsed()), cx, cy, contentW, PvDraw.COLOR_TEXT);
		}

		PvDraw.text(g, font, "Held item", cx, cy, PvDraw.COLOR_MUTED);
		cy += font.lineHeight + 2;
		if (pet.hasHeldItem()) {
			ItemStack held = SkyBlockItemFactory.iconStack(pet.heldItem());
			Identifier heldTex = SkyBlockItemFactory.customIcon(pet.heldItem());
			if (heldTex != null) {
				int tex = SkyBlockItemFactory.customIconSize(pet.heldItem());
				g.blit(RenderPipelines.GUI_TEXTURED, heldTex, cx, cy, 0, 0, ITEM_ICON, ITEM_ICON, tex, tex, tex, tex);
			} else if (!held.isEmpty()) {
				g.item(held, cx, cy);
			}
			String heldName = SkyBlockItemFactory.plainDisplayName(pet.heldItem());
			int heldColor = SkyBlockItemFactory.tierArgb(SkyBlockItemFactory.resolveTier(pet.heldItem()));
			PvDraw.text(
				g,
				font,
				trim(font, heldName, contentW - ITEM_ICON - 6),
				cx + ITEM_ICON + 4,
				cy + (ITEM_ICON - font.lineHeight) / 2,
				heldColor
			);
			cy += ITEM_ICON + 6;
		} else {
			PvDraw.text(g, font, "None", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 6;
		}

		if (pet.hasSkin()) {
			cy += row(
				g,
				font,
				"Skin",
				InventoryDecoder.prettyWords(pet.skin()),
				cx,
				cy,
				contentW,
				PvDraw.COLOR_TEXT
			);
		}

		row(
			g,
			font,
			"Networth",
			pet.networth() > 0 ? FormatUtil.shortCoins(pet.networth()) : "—",
			cx,
			cy,
			contentW,
			PvDraw.COLOR_GOLD
		);
	}

	private static int row(GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int valueColor) {
		PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, trim(font, value, Math.max(20, w - font.width(label) - 8)), x + w, y, valueColor);
		return font.lineHeight + 4;
	}

	private static String trim(Font font, String value, int maxW) {
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

	private record SlotHit(int x, int y, int w, int h, int index) {
	}
}
