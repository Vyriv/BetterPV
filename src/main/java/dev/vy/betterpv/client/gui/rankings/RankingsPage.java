package dev.vy.betterpv.client.gui.rankings;

import dev.vy.betterpv.client.data.EliteBoards;
import dev.vy.betterpv.client.data.EliteRankings;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.collections.CollectionsUi;
import dev.vy.betterpv.client.gui.garden.GardenUi;
import dev.vy.betterpv.client.gui.inventories.SkyBlockIconRenderer;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Home > Rankings: every Elite leaderboard placement in one list, best rank first. */
public final class RankingsPage {
	/**
	 * Few placements get roomy single-column cards; more switch to a compact grid. Icons stay at the native
	 * 16px because fractional item scales drop texture pixels and look muddy.
	 */
	enum Layout {
		SPACIOUS(20, 18, 16),
		COMPACT(16, 16, 16);

		final int rowH;
		final int cardH;
		final int icon;

		Layout(int rowH, int cardH, int icon) {
			this.rowH = rowH;
			this.cardH = cardH;
			this.icon = icon;
		}
	}

	static final int SPACIOUS_MAX_ENTRIES = 12;
	static final int SPACIOUS_MAX_W = 340;
	static final int MIN_COL_W = 200;
	static final int COL_GAP = 10;
	private static final int PAD = 8;
	private static final int CELL_PAD = 4;
	private static final int CELL_GAP = 6;
	private static final int ICON_GAP = 5;
	private static final int COLOR_ERROR = 0xFFFF5555;
	static final int COLOR_RANK_1 = 0xFF55FFFF;
	static final int COLOR_RANK_2 = 0xFFFFAA00;
	static final int COLOR_RANK_3 = 0xFFFF55FF;
	static final int COLOR_RANK_TOP = 0xFFFFFFFF;
	static final int COLOR_RANK_REST = 0xFFAAAAAA;
	private static final int COLOR_CARD = 0x12FFFFFF;
	private static final int COLOR_ROW_HOVER = 0x22FFFFFF;
	private static final String HEADER = "Leaderboards";
	private static final String CREDIT_LABEL = "Powered by Elite";
	private static final String CREDIT_LINK = "eliteskyblock.com";
	private static final String SEP = " · ";
	private static final String RETRY = "Click to retry";
	private static final Map<String, ItemStack> VANILLA_ICONS = new HashMap<>();
	private static final Map<String, String> COLLECTION_ICONS = new HashMap<>();
	private static final Map<String, ItemStack> SKYBLOCK_HEADS = new HashMap<>();

	private EliteRankings rankings = EliteRankings.idle();
	private int scroll;
	private int maxScroll;
	private int listX;
	private int listY;
	private int listW;
	private int listH;
	private EliteRankings.Entry hovered;
	private int creditX;
	private int creditY;
	private int creditW;
	private int creditH;
	private boolean creditHovered;
	private boolean retryVisible;
	private int retryX;
	private int retryY;
	private int retryW;
	private int retryH;
	private EliteRankings measuredFor;
	private int rankColW;
	private int valueColW;
	private int rowH = Layout.COMPACT.rowH;

	public EliteRankings rankings() {
		return this.rankings;
	}

	public void apply(EliteRankings next) {
		EliteRankings value = next == null ? EliteRankings.idle() : next;
		if (!value.key().equals(this.rankings.key())) {
			this.scroll = 0;
		}
		this.rankings = value;
	}

	public void reset() {
		this.rankings = EliteRankings.idle();
		this.scroll = 0;
		this.maxScroll = 0;
		this.hovered = null;
	}

	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
		this.hovered = null;
		this.retryVisible = false;
		this.maxScroll = 0;
		PvDraw.innerPanel(g, x, y, w, h);
		int cx = x + PAD;
		int cw = w - PAD * 2;
		int cy = y + PAD;

		PvDraw.sectionHeader(g, font, title(), cx, cy, cw);
		cy += font.lineHeight + 5;

		int footerY = y + h - PAD - font.lineHeight;
		drawCredit(g, font, cx, footerY, mouseX, mouseY);

		this.listX = cx;
		this.listY = cy;
		this.listW = cw;
		this.listH = Math.max(Layout.COMPACT.rowH, footerY - 4 - cy);
		int midX = cx + cw / 2;
		int midY = this.listY + this.listH / 2;

		switch (this.rankings.state()) {
			case IDLE, LOADING -> PvDraw.textCentered(
				g, font, "Loading Elite rankings...", midX, midY - font.lineHeight / 2, PvDraw.COLOR_MUTED
			);
			case ERROR -> drawError(g, font, midX, midY, mouseX, mouseY);
			case READY -> {
				if (this.rankings.entries().isEmpty()) {
					PvDraw.textCentered(g, font, "No leaderboard placements", midX, midY - font.lineHeight, PvDraw.COLOR_TEXT);
					PvDraw.textCentered(
						g, font, "Not ranked on any Elite leaderboard yet", midX, midY + 3, PvDraw.COLOR_MUTED
					);
				} else {
					drawList(g, font, mouseX, mouseY);
				}
			}
		}
	}

	/** {@code Leaderboards · #1 to #49,126} once placements are known. */
	String title() {
		if (this.rankings.state() != EliteRankings.State.READY || this.rankings.entries().isEmpty()) {
			return HEADER;
		}
		int best = this.rankings.bestRank();
		int worst = this.rankings.worstRank();
		String range = best == worst
			? EliteRankings.rankLabel(best)
			: EliteRankings.rankLabel(best) + " to " + EliteRankings.rankLabel(worst);
		return HEADER + SEP + range;
	}

	private void drawError(GuiGraphicsExtractor g, Font font, int midX, int midY, int mouseX, int mouseY) {
		String message = this.rankings.error().isBlank() ? "Couldn't load Elite rankings" : this.rankings.error();
		PvDraw.textCentered(g, font, message, midX, midY - font.lineHeight, COLOR_ERROR);
		if (this.rankings.key().isEmpty()) {
			return;
		}
		this.retryW = font.width(RETRY);
		this.retryH = font.lineHeight;
		this.retryX = midX - this.retryW / 2;
		this.retryY = midY + 3;
		this.retryVisible = true;
		boolean hover = inside(mouseX, mouseY, this.retryX, this.retryY, this.retryW, this.retryH);
		PvDraw.text(g, font, RETRY, this.retryX, this.retryY, hover ? PvDraw.COLOR_TEXT : PvDraw.COLOR_ACCENT);
	}

	static Layout layoutFor(int count, int listH) {
		return count <= SPACIOUS_MAX_ENTRIES || count * Layout.SPACIOUS.rowH <= listH ? Layout.SPACIOUS : Layout.COMPACT;
	}

	static int columnsFor(Layout layout, int listW) {
		return layout == Layout.COMPACT && listW >= MIN_COL_W * 2 + COL_GAP ? 2 : 1;
	}

	private void measure(Font font) {
		if (this.measuredFor == this.rankings) {
			return;
		}
		this.measuredFor = this.rankings;
		this.rankColW = font.width(EliteRankings.rankLabel(Math.max(EliteRankings.TOP_RANK, this.rankings.worstRank())));
		int valueW = 0;
		for (EliteRankings.Entry entry : this.rankings.entries()) {
			valueW = Math.max(valueW, font.width(entry.compactAmount()));
		}
		this.valueColW = valueW;
	}

	private void drawList(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		measure(font);
		List<EliteRankings.Entry> entries = this.rankings.entries();
		Layout layout = layoutFor(entries.size(), this.listH);
		int cols = columnsFor(layout, this.listW);
		this.rowH = layout.rowH;
		int areaW = layout == Layout.SPACIOUS ? Math.min(this.listW, SPACIOUS_MAX_W) : this.listW;
		int areaX = this.listX + (this.listW - areaW) / 2;
		int colW = (areaW - COL_GAP * (cols - 1)) / cols;
		int rows = (entries.size() + cols - 1) / cols;
		this.maxScroll = Math.max(0, rows * layout.rowH - this.listH);
		this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll));
		boolean mouseInList = inside(mouseX, mouseY, this.listX, this.listY, this.listW, this.listH);

		g.enableScissor(this.listX, this.listY, this.listX + this.listW, this.listY + this.listH);
		for (int i = 0; i < entries.size(); i++) {
			int cellX = areaX + (i % cols) * (colW + COL_GAP);
			int cellY = this.listY + (i / cols) * layout.rowH - this.scroll;
			if (cellY + layout.cardH <= this.listY || cellY >= this.listY + this.listH) {
				continue;
			}
			EliteRankings.Entry entry = entries.get(i);
			boolean hover = mouseInList && inside(mouseX, mouseY, cellX, cellY, colW, layout.cardH);
			if (layout == Layout.SPACIOUS) {
				PvDraw.fill(g, cellX, cellY, colW, layout.cardH, COLOR_CARD);
			}
			if (hover) {
				this.hovered = entry;
				PvDraw.fill(g, cellX, cellY, colW, layout.cardH, COLOR_ROW_HOVER);
			}
			drawRow(g, font, entry, layout, cellX, cellY, colW);
		}
		g.disableScissor();
	}

	private void drawRow(GuiGraphicsExtractor g, Font font, EliteRankings.Entry entry, Layout layout, int x, int y, int w) {
		EliteBoards.Board board = entry.board();
		int textY = y + (layout.cardH - font.lineHeight) / 2 + 1;
		int rankRight = x + CELL_PAD + this.rankColW;
		PvDraw.text(g, font, EliteRankings.rankLabel(entry.rank()), x + CELL_PAD, textY, rankColor(entry.rank()));

		int iconX = rankRight + CELL_GAP;
		drawIcon(g, board, iconX, y + (layout.cardH - layout.icon) / 2, layout.icon);

		int valueRight = x + w - CELL_PAD;
		PvDraw.text(g, font, entry.compactAmount(), valueRight - this.valueColW, textY, PvDraw.COLOR_TEXT);

		int nameX = iconX + layout.icon + ICON_GAP;
		int nameMax = Math.max(0, valueRight - this.valueColW - CELL_GAP - nameX);
		String suffix = entry.intervalLabel().isEmpty() ? "" : SEP + entry.intervalLabel();
		int suffixW = font.width(suffix);
		String label = entry.label();
		if (!suffix.isEmpty() && font.width(label) + suffixW > nameMax && nameMax - suffixW < font.width("...") * 3) {
			suffix = "";
			suffixW = 0;
		}
		String name = trim(font, label, nameMax - suffixW);
		PvDraw.text(g, font, name, nameX, textY, nameColor(entry));
		if (!suffix.isEmpty()) {
			PvDraw.text(g, font, suffix, nameX + font.width(name), textY, PvDraw.COLOR_MUTED);
		}
	}

	private static void drawIcon(GuiGraphicsExtractor g, EliteBoards.Board board, int x, int y, int size) {
		if (board.hasCollectionIcon()) {
			CollectionsUi.drawIcon(g, collectionIconId(board.collectionId()), x, y, size);
			return;
		}
		ItemStack head = board.hasSkyBlockIcon() ? skyblockHead(board.skyblockItem()) : ItemStack.EMPTY;
		if (!head.isEmpty()) {
			SkyBlockIconRenderer.draw(g, head, board.skyblockItem(), x, y, size);
		} else {
			CollectionsUi.drawVanillaIcon(g, vanillaIcon(board.vanillaItem()), x, y, size);
		}
	}

	/** Textured NEU head for {@code id}, or empty while the repo item is unavailable. */
	private static ItemStack skyblockHead(String id) {
		ItemStack cached = SKYBLOCK_HEADS.get(id);
		if (cached != null) {
			return cached;
		}
		ItemStack stack = SkyBlockItemFactory.iconStack(id);
		if (!GardenUi.isTexturedHead(stack)) {
			return ItemStack.EMPTY;
		}
		SKYBLOCK_HEADS.put(id, stack);
		return stack;
	}

	private static String collectionIconId(String collectionId) {
		String cached = COLLECTION_ICONS.get(collectionId);
		if (cached != null) {
			return cached;
		}
		String id = CollectionsUi.resolveIconId(collectionId);
		// Don't pin the fallback id while NEU is still loading.
		ItemStack stack = SkyBlockItemFactory.iconStack(id);
		if (SkyBlockIconRenderer.hasKnownIcon(id) || (!stack.isEmpty() && !stack.is(Items.PAPER))) {
			COLLECTION_ICONS.put(collectionId, id);
		}
		return id;
	}

	private static ItemStack vanillaIcon(String path) {
		return VANILLA_ICONS.computeIfAbsent(path, p -> new ItemStack(vanillaItem(p)));
	}

	static Item vanillaItem(String path) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(path)).orElse(Items.MAP);
	}

	private void drawCredit(GuiGraphicsExtractor g, Font font, int x, int y, int mouseX, int mouseY) {
		String lead = CREDIT_LABEL + SEP;
		int leadW = font.width(lead);
		int linkW = font.width(CREDIT_LINK);
		this.creditX = x;
		this.creditY = y;
		this.creditW = leadW + linkW;
		this.creditH = font.lineHeight;
		this.creditHovered = inside(mouseX, mouseY, this.creditX, this.creditY, this.creditW, this.creditH);
		PvDraw.text(g, font, lead, x, y, PvDraw.COLOR_MUTED);
		int linkColor = this.creditHovered ? PvDraw.COLOR_TEXT : PvDraw.COLOR_ACCENT;
		PvDraw.text(g, font, CREDIT_LINK, x + leadW, y, linkColor);
		PvDraw.fill(g, x + leadW, y + font.lineHeight, linkW, 1, linkColor);
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		if (this.hovered != null) {
			PvTooltip.drawStyled(g, font, entryTooltip(this.hovered), mouseX, mouseY, screenW, screenH);
		} else if (this.creditHovered) {
			PvTooltip.drawStyled(g, font, List.of(
				PvTooltip.Line.title("Elite", PvDraw.COLOR_TEXT),
				PvTooltip.Line.divider(),
				PvTooltip.Line.of("Leaderboard data from " + CREDIT_LINK, PvDraw.COLOR_MUTED),
				PvTooltip.Line.action("Click to open " + EliteRankings.CREDIT_URL)
			), mouseX, mouseY, screenW, screenH);
		}
	}

	static List<PvTooltip.Line> entryTooltip(EliteRankings.Entry entry) {
		EliteBoards.Board board = entry.board();
		List<PvTooltip.Line> lines = new ArrayList<>(7);
		lines.add(PvTooltip.Line.title(entry.title(), board.category().color()));
		lines.add(PvTooltip.Line.divider());
		lines.add(PvTooltip.Line.row("Rank", PvDraw.COLOR_MUTED, EliteRankings.rankLabel(entry.rank()), rankColor(entry.rank())));
		lines.add(PvTooltip.Line.row(board.valueLabel(), PvDraw.COLOR_MUTED, entry.exactAmount(), PvDraw.COLOR_TEXT));
		if (entry.interval() != EliteRankings.Interval.CURRENT && !entry.intervalDetail().isBlank()) {
			lines.add(PvTooltip.Line.row(entry.intervalLabel(), PvDraw.COLOR_MUTED, entry.intervalDetail(), PvDraw.COLOR_TEXT));
		}
		return lines;
	}

	/** Chat colours: #1 aqua, #2 gold, #3 light purple, then white to #1,000 and grey beyond. */
	static int rankColor(int rank) {
		return switch (rank) {
			case 1 -> COLOR_RANK_1;
			case 2 -> COLOR_RANK_2;
			case 3 -> COLOR_RANK_3;
			default -> rank <= EliteRankings.TOP_RANK ? COLOR_RANK_TOP : COLOR_RANK_REST;
		};
	}

	static int nameColor(EliteRankings.Entry entry) {
		return entry.board().category().color();
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.maxScroll <= 0 || scrollY == 0D
			|| !inside((int) mouseX, (int) mouseY, this.listX, this.listY, this.listW, this.listH)) {
			return false;
		}
		int step = this.rowH * 2;
		int next = this.scroll + (scrollY > 0D ? -step : step);
		this.scroll = Math.max(0, Math.min(this.maxScroll, next));
		return true;
	}

	public boolean clickRetry(double mx, double my) {
		return this.retryVisible && inside((int) mx, (int) my, this.retryX, this.retryY, this.retryW, this.retryH);
	}

	public boolean clickCredit(double mx, double my) {
		return inside((int) mx, (int) my, this.creditX, this.creditY, this.creditW, this.creditH);
	}

	private static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return w > 0 && h > 0 && mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static String trim(Font font, String text, int maxW) {
		if (text == null || maxW <= 0) {
			return "";
		}
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int budget = maxW - font.width(ellipsis);
		return budget <= 0 ? "" : font.plainSubstrByWidth(text, budget) + ellipsis;
	}
}
