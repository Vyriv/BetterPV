package dev.vy.betterpv.client.gui.dungeons;

import dev.vy.betterpv.client.data.DungeonSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.dungeons.CataXpCalculator;
import dev.vy.betterpv.client.dungeons.ClassLevelQuery;
import dev.vy.betterpv.client.dungeons.ClassXpCalculator;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class DungeonPage {
	private enum FieldFocus {
		NONE,
		CATA,
		CLASS
	}

	private static final int PAD = 6;
	private static final int BAR_AFTER = 4;
	private static final int GAP = 6;
	private static final int CALC_H = 70;
	private static final int FIELD_H = 14;
	private static final int BTN_W = 36;
	private static final int CATA_MAX_LEN = 3;
	private static final int CLASS_MAX_LEN = 32;
	private static final int HINT_COLOR = 0xFF5A5A68;

	private DungeonSnapshot data = DungeonSnapshot.empty();
	private final List<HoverZone> zones = new ArrayList<>();
	private final DungeonCalcOverlay overlay = new DungeonCalcOverlay();

	private final CalcRow cataRow = new CalcRow();
	private final CalcRow classRow = new CalcRow();

	private String cataLevelText = "";
	private String classLevelText = "";
	private FieldFocus focus = FieldFocus.NONE;
	private boolean replaceOnType;

	public void apply(DungeonSnapshot data) {
		this.data = data == null ? DungeonSnapshot.empty() : data;
	}

	public DungeonCalcOverlay overlay() {
		return this.overlay;
	}

	public void blurField() {
		this.focus = FieldFocus.NONE;
		this.replaceOnType = false;
	}

	public void runCataCalc() {
		blurField();
		int target = parseTarget(this.cataLevelText, this.data.cataLevel() + 1);
		CataXpCalculator.Result result = CataXpCalculator.calculate(this.data, target);
		this.overlay.open(toView(result));
	}

	public void runClassCalc() {
		blurField();
		ClassLevelQuery.Parsed query = ClassLevelQuery.parse(this.classLevelText);
		if (query == null) {
			this.overlay.open(DungeonCalcOverlay.View.error(
				"betterpv.dungeons.calc.class",
				Component.translatable("betterpv.dungeons.calc.class_invalid").getString()
			));
			return;
		}
		if (query.classAverage()) {
			ClassXpCalculator.AverageResult result = ClassXpCalculator.calculateAverage(this.data, query.targetLevel());
			this.overlay.open(toView(result));
			return;
		}
		ClassXpCalculator.Result result = ClassXpCalculator.calculate(this.data, query);
		this.overlay.open(toView(result));
	}

	public boolean mouseScrolled(double delta) {
		return this.overlay.mouseScrolled(delta);
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.overlay.isOpen()) {
			return this.overlay.mouseClicked(mx, my);
		}
		if (hitButton(this.cataRow, mx, my)) {
			runCataCalc();
			return true;
		}
		if (hitButton(this.classRow, mx, my)) {
			runClassCalc();
			return true;
		}
		if (hitField(this.cataRow, mx, my)) {
			this.focus = FieldFocus.CATA;
			this.replaceOnType = !this.cataLevelText.isEmpty();
			return true;
		}
		if (hitField(this.classRow, mx, my)) {
			this.focus = FieldFocus.CLASS;
			this.replaceOnType = !this.classLevelText.isEmpty();
			return true;
		}
		if (this.focus != FieldFocus.NONE) {
			blurField();
			return true;
		}
		return false;
	}

	public boolean isCalcButton(double mx, double my) {
		return !this.overlay.isOpen()
			&& (hitButton(this.cataRow, mx, my) || hitButton(this.classRow, mx, my));
	}

	public boolean charTyped(char codepoint) {
		if (this.focus == FieldFocus.NONE || this.overlay.isOpen()) {
			return false;
		}
		if (this.focus == FieldFocus.CATA) {
			if (codepoint < '0' || codepoint > '9') {
				return false;
			}
			if (this.replaceOnType) {
				this.cataLevelText = "";
				this.replaceOnType = false;
			}
			if (this.cataLevelText.length() >= CATA_MAX_LEN) {
				return true;
			}
			this.cataLevelText += codepoint;
			return true;
		}
		if (!isClassChar(codepoint)) {
			return false;
		}
		if (this.replaceOnType) {
			this.classLevelText = "";
			this.replaceOnType = false;
		}
		if (this.classLevelText.length() >= CLASS_MAX_LEN) {
			return true;
		}
		this.classLevelText += codepoint;
		return true;
	}

	public boolean keyPressed(int key) {
		if (this.overlay.isOpen()) {
			if (key == 256) {
				this.overlay.close();
				return true;
			}
			return false;
		}
		if (this.focus == FieldFocus.NONE) {
			return false;
		}
		if (key == 256) { // Esc
			blurField();
			return true;
		}
		if (key == 257 || key == 335) { // Enter
			if (this.focus == FieldFocus.CLASS) {
				runClassCalc();
			} else {
				runCataCalc();
			}
			return true;
		}
		if (key == 259) { // Backspace
			if (this.replaceOnType) {
				if (this.focus == FieldFocus.CLASS) {
					this.classLevelText = "";
				} else {
					this.cataLevelText = "";
				}
				this.replaceOnType = false;
				return true;
			}
			if (this.focus == FieldFocus.CLASS) {
				if (!this.classLevelText.isEmpty()) {
					this.classLevelText = this.classLevelText.substring(0, this.classLevelText.length() - 1);
				}
			} else if (!this.cataLevelText.isEmpty()) {
				this.cataLevelText = this.cataLevelText.substring(0, this.cataLevelText.length() - 1);
			}
			return true;
		}
		return false;
	}

	private static boolean isClassChar(char c) {
		return (c >= '0' && c <= '9')
			|| (c >= 'a' && c <= 'z')
			|| (c >= 'A' && c <= 'Z')
			|| c == ' ';
	}

	private static boolean hitButton(CalcRow row, double mx, double my) {
		return mx >= row.btnX && mx < row.btnX + row.btnW
			&& my >= row.btnY && my < row.btnY + row.btnH;
	}

	private static boolean hitField(CalcRow row, double mx, double my) {
		return mx >= row.fieldX && mx < row.fieldX + row.fieldW
			&& my >= row.fieldY && my < row.fieldY + row.fieldH;
	}

	private static DungeonCalcOverlay.View toView(CataXpCalculator.Result result) {
		List<DungeonCalcOverlay.FloorLine> floors = new ArrayList<>();
		for (CataXpCalculator.FloorEstimate floor : result.floors()) {
			floors.add(new DungeonCalcOverlay.FloorLine(floor.label(), floor.runsNeeded(), floor.xpPerRun()));
		}
		return DungeonCalcOverlay.View.single(
			"betterpv.dungeons.calc.result_title",
			result.targetLevel(),
			result.xpNeeded(),
			result.mayorLabel() == null || result.mayorLabel().isBlank()
				? ""
				: "Mayor: " + result.mayorLabel(),
			floors
		);
	}

	private static DungeonCalcOverlay.View toView(ClassXpCalculator.Result result) {
		List<DungeonCalcOverlay.FloorLine> floors = new ArrayList<>();
		for (ClassXpCalculator.FloorEstimate floor : result.floors()) {
			floors.add(new DungeonCalcOverlay.FloorLine(floor.label(), floor.runsNeeded(), floor.xpPerRun()));
		}
		return DungeonCalcOverlay.View.single(
			"betterpv.dungeons.calc.class_result_title",
			result.className() + " " + result.targetLevel(),
			result.xpNeeded(),
			result.modsLabel() == null ? "" : result.modsLabel(),
			floors
		);
	}

	private static DungeonCalcOverlay.View toView(ClassXpCalculator.AverageResult result) {
		List<DungeonCalcOverlay.ClassBlock> blocks = new ArrayList<>();
		for (ClassXpCalculator.ClassRuns clazz : result.classes()) {
			blocks.add(new DungeonCalcOverlay.ClassBlock(
				clazz.className(),
				clazz.currentLevel(),
				clazz.selectedRuns()
			));
		}
		String titleArg = result.targetLevel() + " · " + result.floorLabel();
		return DungeonCalcOverlay.View.average(
			"betterpv.dungeons.calc.ca_result_title",
			titleArg,
			result.modsLabel() == null ? "" : result.modsLabel(),
			blocks,
			result.totalRuns(),
			result.floorLabel(),
			result.timedOut()
		);
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
		this.zones.clear();

		int rightW = Math.max(120, w * 38 / 100);
		int leftW = w - rightW - GAP;
		int leftX = x;
		int rightX = x + leftW + GAP;

		int calcH = Math.min(CALC_H, Math.max(64, h * 24 / 100));
		int gap = 4;
		int headerH = font.lineHeight + 4;
		int lineH = font.lineHeight;
		int totalGap = lineH;
		int runsY = y + calcH + GAP;
		int runsH = h - calcH - GAP;

		drawCalcPanel(g, font, leftX, y, leftW, calcH, mouseX, mouseY);
		int firstFloorY = runsY + PAD + headerH;
		int totalY = firstFloorY + 7 * lineH + 6 * gap + totalGap;
		FloorLayout floors = new FloorLayout(firstFloorY, lineH, gap, totalY);
		drawRunsPanel(g, font, leftX, runsY, leftW, runsH, floors);
		drawRightColumn(g, font, rightX, y, rightW, h, floors.totalY());

		List<PvTooltip.Line> tip = null;
		for (HoverZone zone : this.zones) {
			if (mouseX >= zone.x && mouseX < zone.x + zone.w && mouseY >= zone.y && mouseY < zone.y + zone.h) {
				tip = zone.lines;
				break;
			}
		}
		if (tip != null && !tip.isEmpty()) {
			PvTooltip.drawStyled(g, font, tip, mouseX, mouseY, screenW, screenH);
		}
	}

	public void renderOverlay(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
		this.overlay.render(g, font, screenW, screenH, mouseX, mouseY);
	}

	private void drawCalcPanel(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY
	) {
		PvDraw.innerPanel(g, x, y, w, h);
		int cx = x + PAD;
		int cy = y + PAD;

		String header = Component.translatable("betterpv.dungeons.calc.header").getString();
		PvDraw.text(g, font, header, cx, cy, PvDraw.COLOR_MUTED);

		String mods = buildModsHint();
		if (!mods.isBlank()) {
			PvDraw.textRight(g, font, mods, x + w - PAD, cy, PvDraw.COLOR_BORDER);
		}

		cy += font.lineHeight + 6;
		String cataLabel = Component.translatable("betterpv.dungeons.calc.level_wanted").getString();
		String classLabel = Component.translatable("betterpv.dungeons.calc.class_wanted").getString();
		int labelW = Math.max(font.width(cataLabel), font.width(classLabel)) + 4;

		layoutRow(this.cataRow, x, w, cx, cy, labelW);
		drawCalcRow(
			g, font, this.cataRow, cx, cataLabel, this.cataLevelText,
			"betterpv.dungeons.calc.level_hint",
			this.focus == FieldFocus.CATA,
			mouseX, mouseY
		);

		cy += FIELD_H + 6;
		layoutRow(this.classRow, x, w, cx, cy, labelW);
		drawCalcRow(
			g, font, this.classRow, cx, classLabel, this.classLevelText,
			"betterpv.dungeons.calc.class_hint",
			this.focus == FieldFocus.CLASS,
			mouseX, mouseY
		);
	}

	private static void layoutRow(CalcRow row, int panelX, int panelW, int cx, int cy, int labelW) {
		row.fieldH = FIELD_H;
		row.fieldX = cx + labelW;
		row.fieldY = cy;
		row.btnW = BTN_W;
		row.btnH = FIELD_H;
		row.btnX = panelX + panelW - PAD - row.btnW;
		row.btnY = cy;
		row.fieldW = Math.max(28, row.btnX - 4 - row.fieldX);
	}

	private void drawCalcRow(
		GuiGraphicsExtractor g,
		Font font,
		CalcRow row,
		int labelX,
		String label,
		String text,
		String hintKey,
		boolean focused,
		int mouseX,
		int mouseY
	) {
		PvDraw.text(g, font, label, labelX, row.fieldY + 2, PvDraw.COLOR_TEXT);

		boolean fieldHover = hitField(row, mouseX, mouseY);
		int fieldBorder = focused ? PvDraw.COLOR_ACCENT : fieldHover ? 0xFF4A4A5A : PvDraw.COLOR_BORDER;
		PvDraw.fill(g, row.fieldX, row.fieldY, row.fieldW, row.fieldH, 0xFF101018);
		g.outline(row.fieldX, row.fieldY, row.fieldW, row.fieldH, fieldBorder);

		int textX = row.fieldX + 3;
		int textY = row.fieldY + Math.max(0, (row.fieldH - font.lineHeight) / 2);
		if (text.isEmpty()) {
			String hint = Component.translatable(hintKey).getString();
			PvDraw.text(g, font, trimToWidth(font, hint, row.fieldW - 6), textX, textY, HINT_COLOR);
		} else {
			int textColor = this.replaceOnType && focused ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_TEXT;
			PvDraw.text(g, font, trimToWidth(font, text, row.fieldW - 6), textX, textY, textColor);
			if (focused && !this.replaceOnType && (System.currentTimeMillis() / 500L) % 2L == 0L) {
				int cursorX = textX + font.width(trimToWidth(font, text, row.fieldW - 6));
				PvDraw.fill(g, cursorX, textY, 1, font.lineHeight, PvDraw.COLOR_TEXT);
			}
		}

		boolean btnHover = hitButton(row, mouseX, mouseY);
		PvDraw.fill(g, row.btnX, row.btnY, row.btnW, row.btnH, btnHover ? 0xFF2A3A55 : 0xFF16161E);
		g.outline(row.btnX, row.btnY, row.btnW, row.btnH, btnHover ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER);
		String calc = Component.translatable("betterpv.dungeons.calc.button").getString();
		PvDraw.textCentered(
			g, font, calc,
			row.btnX + row.btnW / 2,
			row.btnY + (row.btnH - font.lineHeight) / 2,
			PvDraw.COLOR_TEXT
		);
	}

	private static String trimToWidth(Font font, String text, int maxW) {
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int budget = maxW - font.width(ellipsis);
		if (budget <= 0) {
			return ellipsis;
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (font.width(out.toString() + c) > budget) {
				break;
			}
			out.append(c);
		}
		return out + ellipsis;
	}

	private String buildModsHint() {
		List<String> bits = new ArrayList<>();
		if (this.data.expertRing()) {
			bits.add("Ring");
		}
		if (this.data.hecatombLevel() > 0) {
			bits.add("Hec " + roman(this.data.hecatombLevel()));
		}
		if (this.data.scarfBonus() > 0) {
			bits.add("Scarf +" + Math.round(this.data.scarfBonus() * 100) + "%");
		}
		if (this.data.mayorFactor() > 1.0 && this.data.mayorName() != null && !this.data.mayorName().isBlank()) {
			bits.add(this.data.mayorName());
		}
		return String.join(" · ", bits);
	}

	private static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			case 7 -> "VII";
			case 8 -> "VIII";
			case 9 -> "IX";
			case 10 -> "X";
			default -> String.valueOf(n);
		};
	}

	private static int parseTarget(String text, int fallback) {
		if (text == null || text.isBlank()) {
			return Math.max(1, Math.min(50, fallback));
		}
		try {
			return Math.max(1, Math.min(50, Integer.parseInt(text.trim())));
		} catch (NumberFormatException exception) {
			return Math.max(1, Math.min(50, fallback));
		}
	}

	private void drawRightColumn(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int totalY) {
		PvDraw.innerPanel(g, x, y, w, h);
		int cx = x + PAD;
		int barW = w - PAD * 2;
		int labeledH = font.lineHeight + 2 + PvDraw.BAR_HEIGHT;
		int defaultRowH = labeledH + BAR_AFTER;

		int cataY = y + PAD + 4;
		PvDraw.labeledBar(
			g, font,
			"Catacombs", String.valueOf(this.data.cataLevel()),
			this.data.cataProgress(),
			cx, cataY, barW,
			PvDraw.COLOR_BAR_FILL,
			this.data.cataMaxed()
		);
		this.zones.add(new HoverZone(cx, cataY, barW, labeledH, plainTip(this.data.cataHover())));

		int secretsY = cataY + labeledH + 6;
		PvDraw.text(g, font, "Secrets", cx, secretsY, PvDraw.COLOR_MUTED);
		String secretsValue = FormatUtil.commas(this.data.secrets())
			+ " ("
			+ FormatUtil.oneDecimal(this.data.secretsPerRun())
			+ "/pr)";
		PvDraw.textRight(g, font, secretsValue, cx + barW, secretsY, PvDraw.COLOR_TEXT);

		List<DungeonSnapshot.ClassEntry> classes = this.data.classes();
		if (classes.isEmpty()) {
			return;
		}
		int n = classes.size();

		int tankY = totalY + font.lineHeight - labeledH;
		int rowH = defaultRowH;
		int firstY = tankY - (n - 1) * rowH;

		int avgGap = 8;
		int avgY = firstY - avgGap - labeledH;
		int secretsEnd = secretsY + font.lineHeight;
		if (avgY < secretsEnd + 8 && n > 1) {
			avgY = secretsEnd + 8;
			firstY = avgY + labeledH + avgGap;
			rowH = Math.max(labeledH + 2, (tankY - firstY) / (n - 1));
			firstY = tankY - (n - 1) * rowH;
			avgY = firstY - avgGap - labeledH;
		}

		PvDraw.labeledBar(
			g, font,
			"Class Average", FormatUtil.oneDecimal(this.data.classAverage()),
			this.data.classAverageProgress(),
			cx, avgY, barW,
			PvDraw.COLOR_BAR_FILL,
			this.data.classAverageMaxed()
		);
		this.zones.add(new HoverZone(cx, avgY, barW, labeledH, plainTip(this.data.classAverageHover())));

		for (int i = 0; i < n; i++) {
			DungeonSnapshot.ClassEntry clazz = classes.get(i);
			int cy = i == n - 1 ? tankY : firstY + i * rowH;
			String label = clazz.selected() ? "> " + clazz.name() : clazz.name();
			int labelColor = clazz.selected() ? 0xFF6DFF8A : PvDraw.COLOR_TEXT;
			PvDraw.text(g, font, label, cx, cy, labelColor);
			PvDraw.textRight(g, font, String.valueOf(clazz.level()), cx + barW, cy, PvDraw.COLOR_MUTED);
			int barY = cy + font.lineHeight + 2;
			PvDraw.progressBar(g, cx, barY, barW, PvDraw.BAR_HEIGHT, clazz.progress(), PvDraw.COLOR_BAR_FILL, clazz.maxed());
			this.zones.add(new HoverZone(cx, cy, barW, labeledH, plainTip(clazz.xpHover())));
		}
	}

	private void drawRunsPanel(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, FloorLayout floors) {
		PvDraw.innerPanel(g, x, y, w, h);

		int cx = x + PAD;
		int innerW = w - PAD * 2;
		int colGap = 20;
		int colW = (innerW - colGap) / 2;
		int leftCol = cx;
		int rightCol = cx + colW + colGap;

		final int normalColor = 0xFF7CB87C;
		final int masterColor = 0xFFB85A5A;

		String normalHeader = Component.translatable("betterpv.dungeons.normal").getString();
		String masterHeader = Component.translatable("betterpv.dungeons.master").getString();
		PvDraw.text(g, font, normalHeader, leftCol, y + PAD, normalColor);
		PvDraw.text(g, font, masterHeader, rightCol, y + PAD, masterColor);

		int cy = floors.firstFloorY();
		long normalTotal = 0L;
		long masterTotal = 0L;
		for (int floor = 1; floor <= 7; floor++) {
			DungeonSnapshot.FloorEntry normal = floorById(this.data.normal(), floor);
			DungeonSnapshot.FloorEntry master = floorById(this.data.master(), floor);
			normalTotal += normal.completions();
			masterTotal += master.completions();

			String floorLabel = Component.translatable("betterpv.dungeons.floor", floor).getString();

			drawFloorCell(g, font, leftCol, cy, colW, floors.lineH(), floors.gap(), floorLabel, normal);
			drawFloorCell(g, font, rightCol, cy, colW, floors.lineH(), floors.gap(), floorLabel, master);
			cy += floors.lineH() + floors.gap();
		}

		drawTotalLine(g, font, leftCol, floors.totalY(), colW, normalTotal, normalColor);
		drawTotalLine(g, font, rightCol, floors.totalY(), colW, masterTotal, masterColor);
	}

	private void drawTotalLine(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		long total,
		int labelColor
	) {
		String label = Component.translatable("betterpv.dungeons.total").getString();
		PvDraw.text(g, font, label, x, y, labelColor);
		PvDraw.textRight(g, font, FormatUtil.commas(total), x + w, y, PvDraw.COLOR_TEXT);
	}

	private void drawFloorCell(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int lineH,
		int gap,
		String label,
		DungeonSnapshot.FloorEntry floor
	) {
		PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, FormatUtil.commas(floor.completions()), x + w, y, PvDraw.COLOR_TEXT);
		this.zones.add(new HoverZone(
			x, y - 1, w, lineH + Math.max(2, gap / 2),
			floorStatsTip(label, floor)
		));
	}

	private static List<PvTooltip.Line> plainTip(String text) {
		return List.of(PvTooltip.Line.plain(text));
	}

	private static final int COLOR_STAT = 0xFFE8E8F0;
	private static final int COLOR_COMPLETIONS = 0xFF7CFF9A;
	private static final int COLOR_MOBS = 0xFFFFB86C;
	private static final int COLOR_SCORE_S_PLUS = 0xFF6DFF8A;
	private static final int COLOR_SCORE = 0xFFFFD36A;
	private static final int COLOR_TIME = 0xFF7EC8FF;
	private static final int COLOR_TIME_S = 0xFFFFCC66;
	private static final int COLOR_TIME_S_PLUS = 0xFF6DFF8A;
	private static final int COLOR_HEALING = 0xFFFF8EC8;
	private static final int COLOR_MAGE = 0xFF6EB5FF;
	private static final int COLOR_BERSERK = 0xFFFF6B6B;
	private static final int COLOR_ARCHER = 0xFF7CFF9A;
	private static final int COLOR_HEALER = 0xFFFF8EC8;
	private static final int COLOR_TANK = 0xFFC0C0D0;

	private static List<PvTooltip.Line> floorStatsTip(String title, DungeonSnapshot.FloorEntry floor) {
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(PvTooltip.Line.bold(title, PvDraw.COLOR_GOLD));
		lines.add(statLine("Tier Completions", FormatUtil.commas(floor.completions()), COLOR_COMPLETIONS));
		lines.add(statLine("Milestone Completions", FormatUtil.commas(floor.milestoneCompletions()), COLOR_COMPLETIONS));
		lines.add(statLine("Mobs Killed", FormatUtil.shortXp(floor.mobsKilled()), COLOR_MOBS));
		lines.add(statLine(
			"Best Score",
			FormatUtil.commas(floor.bestScore()),
			floor.bestScore() >= 300L ? COLOR_SCORE_S_PLUS : COLOR_SCORE
		));
		lines.add(statLine("Most Mobs Killed", FormatUtil.commas(floor.mostMobsKilled()), COLOR_MOBS));
		lines.add(timeLine("Fastest Time", floor.fastestMs(), COLOR_TIME));
		lines.add(timeLine("Fastest Time S", floor.fastestSMs(), COLOR_TIME_S));
		if (floor.fastestSPlusMs() > 0L) {
			lines.add(timeLine("Fastest Time S Plus", floor.fastestSPlusMs(), COLOR_TIME_S_PLUS));
		}
		if (floor.mostHealing() > 0D) {
			lines.add(statLine("Most Healing", FormatUtil.shortXp(floor.mostHealing()), COLOR_HEALING));
		}
		if (floor.mostDamage() > 0D && floor.mostDamageClass() != null) {
			String className = ClassLevelQuery.displayName(floor.mostDamageClass());
			int classColor = classColor(floor.mostDamageClass());
			lines.add(new PvTooltip.Line(List.of(
				PvTooltip.Span.of("Most Damage: ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.of(FormatUtil.shortXp(floor.mostDamage()), COLOR_STAT),
				PvTooltip.Span.of(" (", PvDraw.COLOR_MUTED),
				PvTooltip.Span.of(className, classColor),
				PvTooltip.Span.of(")", PvDraw.COLOR_MUTED)
			)));
		}
		return lines;
	}

	private static PvTooltip.Line statLine(String label, String value, int valueColor) {
		return new PvTooltip.Line(List.of(
			PvTooltip.Span.of(label + ": ", PvDraw.COLOR_MUTED),
			PvTooltip.Span.of(value, valueColor)
		));
	}

	private static PvTooltip.Line timeLine(String label, long ms, int color) {
		return new PvTooltip.Line(List.of(
			PvTooltip.Span.bold(label + ": ", color),
			PvTooltip.Span.bold(FormatUtil.prettyTime(ms), color)
		));
	}

	private static int classColor(String classId) {
		if (classId == null) {
			return COLOR_STAT;
		}
		return switch (classId.toLowerCase()) {
			case "mage" -> COLOR_MAGE;
			case "berserk" -> COLOR_BERSERK;
			case "archer" -> COLOR_ARCHER;
			case "healer" -> COLOR_HEALER;
			case "tank" -> COLOR_TANK;
			default -> COLOR_STAT;
		};
	}

	private static DungeonSnapshot.FloorEntry floorById(DungeonSnapshot.ModeStats mode, int floor) {
		String key = String.valueOf(floor);
		for (DungeonSnapshot.FloorEntry entry : mode.floors()) {
			if (key.equals(entry.id())) {
				return entry;
			}
		}
		return DungeonSnapshot.FloorEntry.empty(key, "F" + floor);
	}

	private record FloorLayout(int firstFloorY, int lineH, int gap, int totalY) {
	}

	private record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
	}

	private static final class CalcRow {
		int fieldX;
		int fieldY;
		int fieldW;
		int fieldH;
		int btnX;
		int btnY;
		int btnW;
		int btnH;
	}
}
