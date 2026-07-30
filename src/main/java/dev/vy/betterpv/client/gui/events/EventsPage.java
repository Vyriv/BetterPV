package dev.vy.betterpv.client.gui.events;

import dev.vy.betterpv.client.data.ChocolateEmployees;
import dev.vy.betterpv.client.data.EventsSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.HoppityRabbitsData;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Events tab: Bingo / Chocolate Factory - layout matches Mining/Rift overviews. */
public final class EventsPage {
	private static final int PAD = 6;
	private static final int GAP = 6;
	private static final int STAT_ROW = 12;
	private static final int SEP_GAP = 10;
	private static final int BINGO_COLS = 5;
	private static final int BINGO_ROWS = 5;
	private static final int RABBIT_SLOT = 16;
	private static final int RABBIT_ROW = 18;
	private static final int COLOR_COMPLETE = 0xFF55FF55;
	private static final int COLOR_COMMUNITY = 0xFF7C9CFF;
	private static final int COLOR_CHOCOLATE = 0xFFD4A574;
	private static final int SLOT_BG = 0xFF101018;
	private static final int SLOT_BORDER = 0xFF2A2A35;

	public enum BingoLoadState {
		IDLE,
		LOADING,
		READY,
		ERROR
	}

	private EventsSnapshot snapshot = EventsSnapshot.empty();
	private BingoLoadState bingoState = BingoLoadState.IDLE;
	private String bingoError = "";
	/** True when resources loaded but player history did not. */
	private boolean bingoHistoryMissing;
	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;
	private int contentX;
	private int contentY;
	private int contentW;
	private int contentH;
	private final List<HoverZone> zones = new ArrayList<>();

	public void apply(EventsSnapshot snapshot) {
		this.snapshot = snapshot == null ? EventsSnapshot.empty() : snapshot;
		this.scroll = 0;
		this.zones.clear();
		// Fresh profile - bingo resources/history load lazily on Events tab.
		if (this.bingoState == BingoLoadState.READY
			&& (this.snapshot.bingo().currentGoals().isEmpty() && this.snapshot.bingo().history().isEmpty())) {
			this.bingoState = BingoLoadState.IDLE;
		}
	}

	public void applyBingoLoading() {
		this.bingoState = BingoLoadState.LOADING;
		this.bingoError = "";
		this.bingoHistoryMissing = false;
	}

	public void applyBingoReady(EventsSnapshot snapshot) {
		applyBingoReady(snapshot, true);
	}

	public void applyBingoReady(EventsSnapshot snapshot, boolean historyLoaded) {
		this.snapshot = snapshot == null ? EventsSnapshot.empty() : snapshot;
		this.bingoState = BingoLoadState.READY;
		this.bingoError = "";
		this.bingoHistoryMissing = !historyLoaded || this.snapshot.bingo().history().isEmpty();
	}

	public void applyBingoError(String message) {
		this.bingoState = BingoLoadState.ERROR;
		this.bingoError = message == null || message.isBlank() ? "Bingo unavailable" : message;
		this.bingoHistoryMissing = false;
	}

	public BingoLoadState bingoState() {
		return this.bingoState;
	}

	/** Resources present but {@code /skyblock/bingo} history missing - worth retrying. */
	public boolean needsBingoHistory() {
		return this.bingoState == BingoLoadState.READY && this.bingoHistoryMissing;
	}

	public void resetBingoFetch() {
		this.bingoState = BingoLoadState.IDLE;
		this.bingoError = "";
		this.bingoHistoryMissing = false;
	}

	public EventsSnapshot snapshot() {
		return this.snapshot;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		if (this.maxScroll <= 0 || this.scrollH <= 0) {
			return false;
		}
		if (mouseY < this.scrollTop || mouseY >= this.scrollTop + this.scrollH) {
			return false;
		}
		if (mouseX < this.contentX || mouseX >= this.contentX + this.contentW) {
			return false;
		}
		int step = STAT_ROW * 3;
		int next = Math.max(0, Math.min(this.maxScroll, this.scroll + (scrollY > 0 ? -step : step)));
		if (next != this.scroll) {
			this.scroll = next;
			return true;
		}
		return false;
	}

	public void render(
		GuiGraphicsExtractor g,
		Font font,
		PvSubTab sub,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	) {
		this.zones.clear();
		this.contentX = x;
		this.contentY = y;
		this.contentW = w;
		this.contentH = h;
		PvSubTab mode = sub == null ? PvSubTab.EVENTS_BINGO : sub;
		this.scrollTop = y;
		this.scrollH = h;
		this.maxScroll = 0;
		if (mode == PvSubTab.EVENTS_CHOCOLATE) {
			renderChocolate(g, font, x, y, w, h, mouseX, mouseY);
		} else {
			renderBingo(g, font, x, y, w, h, mouseX, mouseY);
		}
		boolean mouseInGui = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		if (!mouseInGui) {
			return;
		}
		for (HoverZone zone : this.zones) {
			if (mouseX >= zone.x && mouseX < zone.x + zone.w && mouseY >= zone.y && mouseY < zone.y + zone.h) {
				PvTooltip.drawStyled(g, font, zone.lines, mouseX, mouseY, screenW, screenH);
				break;
			}
		}
	}

	private void renderBingo(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
		EventsSnapshot.Bingo bingo = this.snapshot.bingo();
		int rightW = Math.max(200, w * 58 / 100);
		int leftW = w - rightW - GAP;
		int lx = x;
		int rx = x + leftW + GAP;
		PvDraw.innerPanel(g, lx, y, leftW, h);
		PvDraw.innerPanel(g, rx, y, rightW, h);

		int bottom = y + h - PAD;
		int cx = lx + PAD;
		int cy = y + PAD;
		int cw = leftW - PAD * 2;

		cy += PvDraw.sectionHeader(g, font, "Bingo profile", cx, cy, cw);
		if (bingo.hasBingoProfile()) {
			cy = tipStat(g, font, "Profile", bingo.bingoProfileName(), PvDraw.COLOR_GOLD, cx, cy, cw, mx, my,
				tipTitle("Bingo profile", PvDraw.COLOR_GOLD,
					PvTooltip.Line.row("Name", PvDraw.COLOR_MUTED, bingo.bingoProfileName(), PvDraw.COLOR_GOLD),
					bingo.bingoFirstJoinMs() > 0L
						? PvTooltip.Line.meta("Joined " + formatAgo(bingo.bingoFirstJoinMs()))
						: null));
			if (bingo.bingoFirstJoinMs() > 0L && cy + STAT_ROW <= bottom) {
				cy = tipStat(g, font, "Joined", formatAgo(bingo.bingoFirstJoinMs()), PvDraw.COLOR_MUTED, cx, cy, cw, mx, my,
					tipTitle("First join", PvDraw.COLOR_TEXT,
						PvTooltip.Line.meta(formatAgo(bingo.bingoFirstJoinMs()))));
			}
		} else {
			cy = statLine(g, font, "Profile", "None", cx, cy, cw, PvDraw.COLOR_MUTED);
		}

		if (this.bingoState == BingoLoadState.LOADING) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "Loading…", cx, cy, PvDraw.COLOR_MUTED);
		} else if (this.bingoState == BingoLoadState.ERROR) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, trim(font, this.bingoError, cw), cx, cy, 0xFFFF8888);
		} else if (bingo.eventsPlayed() > 0 && cy + SEP_GAP + STAT_ROW * 2 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			cy = tipStat(g, font, "Events played", FormatUtil.commas(bingo.eventsPlayed()), PvDraw.COLOR_ACCENT,
				cx, cy, cw, mx, my,
				tipTitle("Bingo history", PvDraw.COLOR_ACCENT,
					PvTooltip.Line.row("Events", PvDraw.COLOR_MUTED, FormatUtil.commas(bingo.eventsPlayed()), PvDraw.COLOR_ACCENT),
					PvTooltip.Line.row("Total points", PvDraw.COLOR_MUTED, FormatUtil.commas(bingo.totalPoints()), PvDraw.COLOR_GOLD)));
			cy = tipStat(g, font, "Total points", FormatUtil.commas(bingo.totalPoints()), PvDraw.COLOR_GOLD,
				cx, cy, cw, mx, my,
				tipTitle("Total points", PvDraw.COLOR_GOLD,
					PvTooltip.Line.meta("Sum across all bingo events")));
		} else if (this.bingoState == BingoLoadState.READY && this.bingoHistoryMissing && cy + SEP_GAP + font.lineHeight <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "History unavailable", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 2;
			PvDraw.text(g, font, trim(font, "Retrying in background…", cw), cx, cy, PvDraw.COLOR_MUTED);
		}

		List<EventsSnapshot.BingoEvent> history = bingo.history();
		if (this.bingoState == BingoLoadState.READY && !history.isEmpty() && cy + SEP_GAP + font.lineHeight + STAT_ROW <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "Recent events", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 3;
			int histViewH = Math.max(0, bottom - cy);
			int histRows = Math.min(history.size(), Math.max(1, histViewH / STAT_ROW));
			for (int i = 0; i < histRows; i++) {
				EventsSnapshot.BingoEvent ev = history.get(i);
				String label = "Event #" + ev.key();
				String value = FormatUtil.commas(ev.points()) + " pts";
				int rowY = cy + i * STAT_ROW;
				statLine(g, font, label, value, cx, rowY, cw, PvDraw.COLOR_GOLD);
				addClippedHover(cx, rowY, cw, STAT_ROW, lx + PAD, y + PAD, leftW - PAD * 2, h - PAD * 2,
					tipTitle("Bingo event #" + ev.key(), PvDraw.COLOR_GOLD,
						PvTooltip.Line.row("Points", PvDraw.COLOR_MUTED, FormatUtil.commas(ev.points()), PvDraw.COLOR_GOLD),
						PvTooltip.Line.row("Goals done", PvDraw.COLOR_MUTED,
							FormatUtil.commas(ev.completedGoals().size()), PvDraw.COLOR_ACCENT)));
			}
		}

		int rcx = rx + PAD;
		int ry = y + PAD;
		int rcw = rightW - PAD * 2;
		boolean hasEvent = bingo.currentEventId() > 0 || !bingo.currentEventName().isBlank();
		if (this.bingoState == BingoLoadState.LOADING) {
			PvDraw.textCentered(g, font, "Loading bingo…", rx + rightW / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}
		if (this.bingoState == BingoLoadState.ERROR) {
			PvDraw.textCentered(g, font, this.bingoError, rx + rightW / 2, y + h / 2 - font.lineHeight / 2, 0xFFFF8888);
			return;
		}
		if (this.bingoState != BingoLoadState.READY && bingo.currentGoals().isEmpty()) {
			PvDraw.textCentered(g, font, "Open Events to load bingo", rx + rightW / 2,
				y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}

		if (hasEvent) {
			String title = bingo.currentEventName().isBlank()
				? ("Event #" + bingo.currentEventId())
				: bingo.currentEventName();
			ry += PvDraw.sectionHeader(g, font, "Current event", rcx, ry, rcw);
			PvDraw.text(g, font, trim(font, title, rcw), rcx, ry, PvDraw.COLOR_TEXT);
			ry += font.lineHeight + 2;
			if (!bingo.currentModifier().isBlank()) {
				PvDraw.text(g, font, trim(font, "Modifier · " + prettyModifier(bingo.currentModifier()), rcw),
					rcx, ry, PvDraw.COLOR_MUTED);
				ry += font.lineHeight + 2;
			}
			if (bingo.currentEndMs() > 0L) {
				long now = System.currentTimeMillis();
				String timing;
				int timingColor;
				if (now < bingo.currentStartMs()) {
					timing = "Starts in " + FormatUtil.prettySpan(bingo.currentStartMs() - now);
					timingColor = PvDraw.COLOR_ACCENT;
				} else if (now < bingo.currentEndMs()) {
					timing = "Ends in " + FormatUtil.prettySpan(bingo.currentEndMs() - now);
					timingColor = PvDraw.COLOR_GOLD;
				} else {
					timing = "Ended " + FormatUtil.prettySpan(now - bingo.currentEndMs()) + " ago";
					timingColor = PvDraw.COLOR_MUTED;
				}
				ry = tipStat(g, font, "Schedule", timing, timingColor, rcx, ry, rcw, mx, my,
					tipTitle(title, PvDraw.COLOR_TEXT,
						PvTooltip.Line.row("Status", PvDraw.COLOR_MUTED, timing, timingColor)));
			}
			ry = sectionSeparator(g, rx, ry, rightW);
		} else {
			ry += PvDraw.sectionHeader(g, font, "Current event", rcx, ry, rcw);
		}

		List<EventsSnapshot.BingoGoal> goals = bingo.currentGoals();
		if (goals.isEmpty()) {
			PvDraw.text(g, font, "No goal data", rcx, ry, PvDraw.COLOR_MUTED);
			return;
		}

		Set<String> completed = completedGoalIds(bingo);
		int gridTop = ry;
		int gridH = Math.max(24, bottom - gridTop);
		renderBingoGrid(g, font, goals, completed, rcx, gridTop, rcw, gridH, mx, my);
	}

	private void renderBingoGrid(
		GuiGraphicsExtractor g, Font font,
		List<EventsSnapshot.BingoGoal> goals, Set<String> completed,
		int x, int y, int w, int h, int mx, int my
	) {
		int cols = BINGO_COLS;
		int rows = BINGO_ROWS;
		int cellGap = 2;
		int cellByW = Math.max(1, (w - Math.max(0, cols - 1) * cellGap) / cols);
		int cellByH = Math.max(1, (h - Math.max(0, rows - 1) * cellGap) / rows);
		int cell = Math.min(22, Math.min(cellByW, cellByH));
		int gridW = cols * cell + (cols - 1) * cellGap;
		int gridH = rows * cell + (rows - 1) * cellGap;
		int gridX = x + Math.max(0, (w - gridW) / 2);
		int gridY = y + Math.max(0, (h - gridH) / 2);
		int icon = Math.max(1, Math.min(16, cell - 4));

		int count = Math.min(goals.size(), cols * rows);
		for (int i = 0; i < count; i++) {
			EventsSnapshot.BingoGoal goal = goals.get(i);
			int col = i % cols;
			int row = i / cols;
			int cx = gridX + col * (cell + cellGap);
			int cy = gridY + row * (cell + cellGap);
			boolean done = isGoalComplete(goal, completed);
			boolean community = goal.community();

			int bg = done
				? (community ? 0xFF1A2A3A : 0xFF1A2A1A)
				: SLOT_BG;
			int border = done
				? (community ? COLOR_COMMUNITY : COLOR_COMPLETE)
				: (community ? 0xFF4A4A2A : SLOT_BORDER);
			PvDraw.fill(g, cx, cy, cell, cell, bg);
			g.outline(cx, cy, cell, cell, border);

			ItemStack stack = bingoIcon(goal, done);
			int ix = cx + (cell - icon) / 2;
			int iy = cy + (cell - icon) / 2;
			float scale = icon / 16f;
			if (scale != 1f) {
				g.pose().pushMatrix();
				g.pose().translate(ix, iy);
				g.pose().scale(scale, scale);
				g.item(stack, 0, 0);
				g.pose().popMatrix();
			} else {
				g.item(stack, ix, iy);
			}

			if (community && !done && goal.required() > 0L) {
				float fill = (float) Math.min(1.0, (double) goal.progress() / (double) goal.required());
				int barH = 2;
				int barY = cy + cell - barH - 1;
				PvDraw.progressBar(g, cx + 2, barY, cell - 4, barH, fill, COLOR_COMMUNITY, false);
			}

			String name = goal.name().isBlank() ? prettyGoalId(goal.id()) : goal.name();
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.title(name, done ? COLOR_COMPLETE : PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.divider());
			tip.add(PvTooltip.Line.row("Type", PvDraw.COLOR_MUTED,
				community ? "Community" : "Personal",
				community ? COLOR_COMMUNITY : PvDraw.COLOR_ACCENT));
			tip.add(PvTooltip.Line.row("Status", PvDraw.COLOR_MUTED,
				done ? "Complete" : "Incomplete",
				done ? COLOR_COMPLETE : PvDraw.COLOR_MUTED));
			if (community && goal.required() > 0L) {
				tip.add(PvTooltip.Line.row("Progress", PvDraw.COLOR_MUTED,
					FormatUtil.commas(goal.progress()) + " / " + FormatUtil.commas(goal.required()),
					done ? COLOR_COMPLETE : PvDraw.COLOR_GOLD));
			} else if (!community && goal.required() > 0L) {
				tip.add(PvTooltip.Line.row("Required", PvDraw.COLOR_MUTED,
					FormatUtil.commas(goal.required()), PvDraw.COLOR_TEXT));
			}
			if (!goal.lore().isBlank()) {
				tip.add(PvTooltip.Line.blank());
				for (String line : wrapLore(goal.lore(), 42)) {
					tip.add(PvTooltip.Line.meta(line));
				}
			}
			addClippedHover(cx, cy, cell, cell, this.contentX, this.contentY, this.contentW, this.contentH, tip);
		}
	}

	private static ItemStack bingoIcon(EventsSnapshot.BingoGoal goal, boolean done) {
		if (goal.community()) {
			return new ItemStack(done ? Items.DIAMOND : Items.GOLD_INGOT);
		}
		return new ItemStack(done ? Items.FILLED_MAP : Items.PAPER);
	}

	private static Set<String> completedGoalIds(EventsSnapshot.Bingo bingo) {
		Set<String> out = new HashSet<>();
		if (bingo == null) {
			return out;
		}
		int current = bingo.currentEventId();
		for (EventsSnapshot.BingoEvent ev : bingo.history()) {
			if (current > 0 && ev.key() == current) {
				out.addAll(ev.completedGoals());
			}
		}
		for (EventsSnapshot.BingoGoal goal : bingo.currentGoals()) {
			if (goal.community() && goal.required() > 0L && goal.progress() >= goal.required()) {
				out.add(goal.id());
			}
		}
		return out;
	}

	private static boolean isGoalComplete(EventsSnapshot.BingoGoal goal, Set<String> completed) {
		if (goal == null) {
			return false;
		}
		if (completed.contains(goal.id())) {
			return true;
		}
		return goal.community() && goal.required() > 0L && goal.progress() >= goal.required();
	}

	private void renderChocolate(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
		EventsSnapshot.Chocolate choc = this.snapshot.chocolate();
		int rightW = Math.max(200, w * 52 / 100);
		int leftW = w - rightW - GAP;
		int lx = x;
		int rx = x + leftW + GAP;
		PvDraw.innerPanel(g, lx, y, leftW, h);
		PvDraw.innerPanel(g, rx, y, rightW, h);

		int bottom = y + h - PAD;
		int cx = lx + PAD;
		int cy = y + PAD;
		int cw = leftW - PAD * 2;

		cy += PvDraw.sectionHeader(g, font, "Chocolate", cx, cy, cw);
		if (!choc.present()) {
			PvDraw.text(g, font, "No chocolate data", cx, cy, PvDraw.COLOR_MUTED);
			this.maxScroll = 0;
			return;
		}

		cy = tipStat(g, font, "Current", FormatUtil.shortCoins(choc.chocolate()), COLOR_CHOCOLATE, cx, cy, cw, mx, my,
			tipTitle("Chocolate", COLOR_CHOCOLATE,
				PvTooltip.Line.row("Purse", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.chocolate()), COLOR_CHOCOLATE),
				PvTooltip.Line.row("All-time", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.totalChocolate()), PvDraw.COLOR_GOLD)));
		cy = tipStat(g, font, "All-time", FormatUtil.shortCoins(choc.totalChocolate()), PvDraw.COLOR_GOLD, cx, cy, cw, mx, my,
			tipTitle("All-time chocolate", PvDraw.COLOR_GOLD,
				PvTooltip.Line.meta(FormatUtil.commas(choc.totalChocolate()) + " chocolate")));
		cy = tipStat(g, font, "This prestige", FormatUtil.shortCoins(choc.chocolateSincePrestige()), PvDraw.COLOR_TEXT,
			cx, cy, cw, mx, my,
			tipTitle("This prestige", PvDraw.COLOR_TEXT,
				PvTooltip.Line.row("Earned", PvDraw.COLOR_MUTED,
					FormatUtil.commas(choc.chocolateSincePrestige()), PvDraw.COLOR_TEXT)));

		if (cy + SEP_GAP + STAT_ROW * 3 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "Factory", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 3;
			cy = tipStat(g, font, "Level", FormatUtil.commas(choc.chocolateLevel()), PvDraw.COLOR_ACCENT, cx, cy, cw, mx, my,
				tipTitle("Factory level", PvDraw.COLOR_ACCENT,
					PvTooltip.Line.row("Level", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.chocolateLevel()), PvDraw.COLOR_ACCENT)));
			cy = tipStat(g, font, "Click upgrades", FormatUtil.commas(choc.clickUpgrades()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			cy = tipStat(g, font, "Multiplier", FormatUtil.commas(choc.multiplierUpgrades()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			if (cy + STAT_ROW <= bottom) {
				cy = tipStat(g, font, "Rabbit rarity", FormatUtil.commas(choc.rabbitRarityUpgrades()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				cy = tipStat(g, font, "Barn capacity", FormatUtil.commas(choc.barnCapacityLevel()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			}
		}

		if (cy + SEP_GAP + font.lineHeight + STAT_ROW * 2 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "Time Tower", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 3;
			cy = tipStat(g, font, "Level", FormatUtil.commas(choc.timeTowerLevel()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			if (cy + STAT_ROW <= bottom) {
				int chargeColor = choc.timeTowerCharges() >= 3 ? COLOR_COMPLETE : PvDraw.COLOR_ACCENT;
				cy = tipStat(g, font, "Charges", choc.timeTowerCharges() + " / 3", chargeColor, cx, cy, cw, mx, my,
					tipTitle("Time Tower charges", PvDraw.COLOR_ACCENT,
						PvTooltip.Line.row("Charges", PvDraw.COLOR_MUTED, choc.timeTowerCharges() + " / 3", chargeColor)));
			}
			if (choc.timeTowerActivationMs() > 0L && cy + STAT_ROW <= bottom) {
				cy = tipStat(g, font, "Last active", formatAgo(choc.timeTowerActivationMs()), PvDraw.COLOR_MUTED,
					cx, cy, cw, mx, my,
					tipTitle("Time Tower", PvDraw.COLOR_ACCENT,
						PvTooltip.Line.meta("Activated " + formatAgo(choc.timeTowerActivationMs()))));
			}
		}

		if (cy + SEP_GAP + STAT_ROW * 2 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "Shop", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 3;
			cy = tipStat(g, font, "Cocoa fortune", FormatUtil.commas(choc.cocoaFortuneUpgrades()), PvDraw.COLOR_TEXT,
				cx, cy, cw, mx, my, null);
			if (cy + STAT_ROW <= bottom) {
				cy = tipStat(g, font, "Spent", FormatUtil.shortCoins(choc.chocolateSpent()), COLOR_CHOCOLATE,
					cx, cy, cw, mx, my,
					tipTitle("Chocolate shop", COLOR_CHOCOLATE,
						PvTooltip.Line.row("Spent", PvDraw.COLOR_MUTED,
							FormatUtil.commas(choc.chocolateSpent()), COLOR_CHOCOLATE)));
			}
		}

		if (cy + SEP_GAP + STAT_ROW * 3 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "Rabbits & eggs", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 3;
			cy = tipStat(g, font, "Unique", FormatUtil.commas(choc.uniqueRabbits()), PvDraw.COLOR_ACCENT, cx, cy, cw, mx, my,
				tipTitle("Rabbits", PvDraw.COLOR_ACCENT,
					PvTooltip.Line.row("Unique", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.uniqueRabbits()), PvDraw.COLOR_ACCENT),
					PvTooltip.Line.row("Copies", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.totalRabbitDuplicates()), PvDraw.COLOR_TEXT)));
			if (cy + STAT_ROW <= bottom) {
				cy = tipStat(g, font, "Copies", FormatUtil.commas(choc.totalRabbitDuplicates()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				cy = tipStat(g, font, "Breakfast eggs", FormatUtil.shortCoins(choc.breakfastEggs()), COLOR_CHOCOLATE,
					cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				cy = tipStat(g, font, "Lunch eggs", FormatUtil.shortCoins(choc.lunchEggs()), COLOR_CHOCOLATE, cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				cy = tipStat(g, font, "Dinner eggs", FormatUtil.shortCoins(choc.dinnerEggs()), COLOR_CHOCOLATE, cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				cy = tipStat(g, font, "Hitmen slots", FormatUtil.commas(choc.hitmenSlots()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				tipStat(g, font, "Missed eggs", FormatUtil.commas(choc.missedEggs()),
					choc.missedEggs() > 0 ? 0xFFFF8888 : PvDraw.COLOR_MUTED, cx, cy, cw, mx, my,
					choc.missedEggs() > 0
						? tipTitle("Missed eggs", 0xFFFF8888,
						PvTooltip.Line.meta("Uncollected eggs from rabbit hitmen"))
						: null);
			}
		}

		int rcx = rx + PAD;
		int ry = y + PAD;
		int rcw = rightW - PAD * 2;
		ry += PvDraw.sectionHeader(g, font, "Employees", rcx, ry, rcw);
		List<EventsSnapshot.Employee> employees = choc.employees();
		if (employees.isEmpty()) {
			PvDraw.text(g, font, "None", rcx, ry, PvDraw.COLOR_MUTED);
			ry += STAT_ROW + 2;
		} else {
			int empRow = RABBIT_ROW;
			for (EventsSnapshot.Employee emp : employees) {
				if (ry + empRow > bottom) {
					break;
				}
				drawEmployeeRow(g, font, emp, rcx, ry, rcw);
				ry += empRow;
			}
		}

		if (ry + SEP_GAP + font.lineHeight + STAT_ROW <= bottom) {
			ry = sectionSeparator(g, rx, ry, rightW);
			PvDraw.text(g, font, "Top rabbits", rcx, ry, PvDraw.COLOR_MUTED);
			ry += font.lineHeight + 3;
			List<EventsSnapshot.Rabbit> rabbits = choc.topRabbits();
			if (rabbits.isEmpty()) {
				PvDraw.text(g, font, "None", rcx, ry, PvDraw.COLOR_MUTED);
				this.maxScroll = 0;
				return;
			}
			int viewH = Math.max(0, bottom - ry);
			int contentH = rabbits.size() * STAT_ROW;
			this.scrollTop = ry;
			this.scrollH = viewH;
			this.maxScroll = Math.max(0, contentH - viewH);
			this.scroll = Math.min(this.scroll, this.maxScroll);
			g.enableScissor(rcx, ry, rcx + rcw, ry + viewH);
			int gy = ry - this.scroll;
			for (EventsSnapshot.Rabbit rabbit : rabbits) {
				String rarity = rabbit.rarity() == null || rabbit.rarity().isBlank()
					? HoppityRabbitsData.rarityOf(rabbit.id())
					: rabbit.rarity();
				int rarityColor = SkyBlockItemFactory.tierArgb(rarity);
				statLine(g, font, trim(font, rabbit.name(), Math.max(24, rcw - font.width("×" + rabbit.count()) - 8)),
					"×" + rabbit.count(), rcx, gy, rcw, rarityColor);
				addClippedHover(rcx, gy, rcw, STAT_ROW, rcx, ry, rcw, viewH, tipTitle(rabbit.name(), rarityColor,
					PvTooltip.Line.row("Rarity", PvDraw.COLOR_MUTED, prettyModifier(rarity), rarityColor),
					PvTooltip.Line.row("Copies", PvDraw.COLOR_MUTED, FormatUtil.commas(rabbit.count()), PvDraw.COLOR_GOLD)));
				gy += STAT_ROW;
			}
			g.disableScissor();
		} else {
			this.maxScroll = 0;
		}
	}

	private void drawEmployeeRow(
		GuiGraphicsExtractor g, Font font, EventsSnapshot.Employee emp, int x, int y, int w
	) {
		String rarity = ChocolateEmployees.rarityOf(emp.id());
		int rarityColor = SkyBlockItemFactory.tierArgb(rarity);
		int slotBg = raritySlotBackground(rarityColor);
		PvDraw.fill(g, x, y + 1, RABBIT_SLOT, RABBIT_SLOT, slotBg);
		g.outline(x, y + 1, RABBIT_SLOT, RABBIT_SLOT, SLOT_BORDER);

		ItemStack icon = employeeIcon(emp.id());
		g.item(icon, x, y + 1);

		String name = ChocolateEmployees.displayName(emp.id(), emp.name());
		String level = "Lvl " + emp.level();
		int textX = x + RABBIT_SLOT + 4;
		int textW = Math.max(8, w - RABBIT_SLOT - 4);
		int leftMax = Math.max(8, textW - font.width(level) - 6);
		PvDraw.text(g, font, trim(font, name, leftMax), textX, y + 4, rarityColor);
		PvDraw.textRight(g, font, level, x + w, y + 4, PvDraw.COLOR_TEXT);

		addClippedHover(x, y, w, RABBIT_ROW, this.contentX, this.contentY, this.contentW, this.contentH,
			tipTitle(name, rarityColor,
				PvTooltip.Line.row("Rarity", PvDraw.COLOR_MUTED, prettyModifier(rarity), rarityColor),
				PvTooltip.Line.row("Level", PvDraw.COLOR_MUTED, FormatUtil.commas(emp.level()), PvDraw.COLOR_TEXT)));
	}

	private static ItemStack employeeIcon(String employeeId) {
		String value = ChocolateEmployees.skullValue(employeeId);
		if (value != null && !value.isBlank()) {
			ItemStack head = SkyBlockItemFactory.texturedHead(value);
			if (head != null && !head.isEmpty()) {
				return head;
			}
		}
		ItemStack fallback = SkyBlockItemFactory.iconStack("CHOCO_RABBIT_PERSONALITY");
		if (fallback != null && !fallback.isEmpty()) {
			return fallback;
		}
		return new ItemStack(Items.RABBIT_FOOT);
	}

	/** Soft rarity tint like pets/museum slots. */
	private static int raritySlotBackground(int rarityArgb) {
		int r = (rarityArgb >> 16) & 0xFF;
		int g = (rarityArgb >> 8) & 0xFF;
		int b = rarityArgb & 0xFF;
		int mixR = (r * 70 + 16 * 186) / 256;
		int mixG = (g * 70 + 16 * 186) / 256;
		int mixB = (b * 70 + 24 * 186) / 256;
		return 0xFF000000 | (mixR << 16) | (mixG << 8) | mixB;
	}

	private int tipStat(
		GuiGraphicsExtractor g, Font font, String label, String value, int valueColor,
		int x, int y, int w, int mx, int my, List<PvTooltip.Line> tip
	) {
		int next = statLine(g, font, label, value, x, y, w, valueColor);
		if (tip != null && !tip.isEmpty()) {
			addClippedHover(x, y, w, STAT_ROW, this.contentX, this.contentY, this.contentW, this.contentH, tip);
		}
		return next;
	}

	private void addClippedHover(
		int bx, int by, int bw, int bh,
		int clipX, int clipY, int clipW, int clipH,
		List<PvTooltip.Line> tip
	) {
		if (tip == null || tip.isEmpty()) {
			return;
		}
		int x0 = Math.max(bx, Math.max(clipX, this.contentX));
		int y0 = Math.max(by, Math.max(clipY, this.contentY));
		int x1 = Math.min(bx + bw, Math.min(clipX + clipW, this.contentX + this.contentW));
		int y1 = Math.min(by + bh, Math.min(clipY + clipH, this.contentY + this.contentH));
		if (x1 > x0 && y1 > y0) {
			this.zones.add(new HoverZone(x0, y0, x1 - x0, y1 - y0, tip));
		}
	}

	private static int statLine(
		GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int valueColor
	) {
		String safe = value == null || value.isBlank() ? "-" : value;
		int leftMax = Math.max(8, w - font.width(safe) - 6);
		PvDraw.text(g, font, trim(font, label, leftMax), x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, safe, x + w, y, valueColor);
		return y + STAT_ROW;
	}

	private static int sectionSeparator(GuiGraphicsExtractor g, int panelX, int y, int panelW) {
		int lineInset = PAD + 4;
		int lineW = Math.max(0, panelW - lineInset * 2);
		int lineY = y + (SEP_GAP - 1) / 2;
		if (lineW > 0) {
			PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, 0x33FFFFFF);
		}
		return y + SEP_GAP;
	}

	private static List<PvTooltip.Line> tipTitle(String title, int titleColor, PvTooltip.Line... rows) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title(title, titleColor));
		tip.add(PvTooltip.Line.divider());
		for (PvTooltip.Line row : rows) {
			if (row != null) {
				tip.add(row);
			}
		}
		return tip;
	}

	private static String formatAgo(long epochMs) {
		if (epochMs <= 0L) {
			return "-";
		}
		long age = Math.max(0L, System.currentTimeMillis() - epochMs);
		return FormatUtil.prettySpan(age) + " ago";
	}

	private static String prettyModifier(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String[] parts = raw.toLowerCase(Locale.ROOT).split("[_\\-\\s]+");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				out.append(part.substring(1));
			}
		}
		return out.toString();
	}

	private static String prettyGoalId(String id) {
		return prettyModifier(id == null ? "" : id);
	}

	private static List<String> wrapLore(String lore, int maxChars) {
		List<String> lines = new ArrayList<>();
		if (lore == null || lore.isBlank()) {
			return lines;
		}
		String cleaned = lore.replace('\n', ' ').trim();
		while (!cleaned.isEmpty()) {
			if (cleaned.length() <= maxChars) {
				lines.add(cleaned);
				break;
			}
			int breakAt = cleaned.lastIndexOf(' ', maxChars);
			if (breakAt <= 0) {
				breakAt = maxChars;
			}
			lines.add(cleaned.substring(0, breakAt).trim());
			cleaned = cleaned.substring(breakAt).trim();
			if (lines.size() >= 8) {
				if (!cleaned.isEmpty()) {
					lines.add("…");
				}
				break;
			}
		}
		return lines;
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

	private record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
	}
}
