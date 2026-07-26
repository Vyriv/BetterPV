package dev.vy.betterpv.client.gui.dungeons;

import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.gui.PvDraw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Compact results popup for dungeon XP calculators. */
public final class DungeonCalcOverlay {
	public record FloorLine(String label, long runsNeeded, long xpPerRun) {
	}

	public record ClassBlock(String name, int currentLevel, long selectedRuns) {
	}

	public record View(
		String titleKey,
		Object titleArg,
		float xpNeeded,
		String modsLabel,
		List<FloorLine> floors,
		List<ClassBlock> classBlocks,
		long totalRuns,
		String floorLabel,
		boolean timedOut
	) {
		public static View single(String titleKey, Object titleArg, float xpNeeded, String modsLabel, List<FloorLine> floors) {
			return new View(titleKey, titleArg, xpNeeded, modsLabel, floors, List.of(), 0L, "", false);
		}

		public static View average(
			String titleKey,
			Object titleArg,
			String modsLabel,
			List<ClassBlock> blocks,
			long totalRuns,
			String floorLabel,
			boolean timedOut
		) {
			return new View(titleKey, titleArg, 0F, modsLabel, List.of(), blocks, totalRuns, floorLabel, timedOut);
		}

		public static View error(String titleKey, String message) {
			return new View(titleKey, "", -1F, message, List.of(), List.of(), 0L, "", false);
		}

		public boolean isAverage() {
			return this.classBlocks != null && !this.classBlocks.isEmpty();
		}
	}

	private static final int PAD = 8;
	private static final int CLOSE_SIZE = 12;

	private boolean open;
	private View view;
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int closeX;
	private int closeY;
	private int scroll;

	public boolean isOpen() {
		return this.open;
	}

	public void open(View view) {
		this.view = view;
		this.open = view != null;
		this.scroll = 0;
	}

	public void close() {
		this.open = false;
		this.view = null;
		this.scroll = 0;
	}

	public boolean mouseScrolled(double delta) {
		if (!this.open || this.view == null || !this.view.isAverage()) {
			return false;
		}
		this.scroll = Math.max(0, this.scroll - (int) Math.round(delta * 12));
		return true;
	}

	public void render(
		GuiGraphicsExtractor g,
		Font font,
		int screenW,
		int screenH,
		int mouseX,
		int mouseY
	) {
		if (!this.open || this.view == null) {
			return;
		}

		PvDraw.fill(g, 0, 0, screenW, screenH, 0x88000000);

		int lineH = font.lineHeight + 2;
		List<String> lines = buildLines(this.view);
		boolean average = this.view.isAverage();
		int contentH = lines.size() * lineH + PAD * 2 + font.lineHeight + 6;
		this.panelW = average
			? Math.min(360, Math.max(260, screenW * 2 / 5))
			: Math.min(280, Math.max(200, screenW / 3));
		this.panelH = Math.min(contentH, screenH - 40);
		this.panelX = (screenW - this.panelW) / 2;
		this.panelY = (screenH - this.panelH) / 2;

		int maxScroll = Math.max(0, contentH - this.panelH);
		this.scroll = Math.min(this.scroll, maxScroll);

		PvDraw.panel(g, this.panelX, this.panelY, this.panelW, this.panelH);

		String title = Component.translatable(this.view.titleKey(), this.view.titleArg()).getString();
		PvDraw.text(g, font, title, this.panelX + PAD, this.panelY + PAD, PvDraw.COLOR_TEXT);

		this.closeX = this.panelX + this.panelW - PAD - CLOSE_SIZE;
		this.closeY = this.panelY + PAD;
		boolean closeHover = mouseX >= this.closeX && mouseX < this.closeX + CLOSE_SIZE
			&& mouseY >= this.closeY && mouseY < this.closeY + CLOSE_SIZE;
		PvDraw.fill(g, this.closeX, this.closeY, CLOSE_SIZE, CLOSE_SIZE, closeHover ? 0xFF3A3A4A : 0xFF222230);
		g.outline(this.closeX, this.closeY, CLOSE_SIZE, CLOSE_SIZE, PvDraw.COLOR_BORDER);
		PvDraw.textCentered(g, font, "x", this.closeX + CLOSE_SIZE / 2, this.closeY + 1, PvDraw.COLOR_MUTED);

		int y = this.panelY + PAD + font.lineHeight + 6 - this.scroll;
		int maxY = this.panelY + this.panelH - PAD;
		int minY = this.panelY + PAD + font.lineHeight + 4;
		for (String line : lines) {
			if (y + font.lineHeight > maxY) {
				break;
			}
			if (y >= minY) {
				boolean header = line.startsWith("XP ")
					|| line.startsWith("Floor")
					|| line.startsWith("Best ")
					|| line.endsWith("XP")
					|| line.contains(" - ");
				boolean muted = header || line.isBlank();
				PvDraw.text(
					g, font, line,
					this.panelX + PAD, y,
					muted ? PvDraw.COLOR_MUTED : PvDraw.COLOR_TEXT
				);
			}
			y += lineH;
		}
	}

	/** @return true if the click was consumed (close or absorb inside panel). */
	public boolean mouseClicked(double mx, double my) {
		if (!this.open) {
			return false;
		}
		if (mx >= this.closeX && mx < this.closeX + CLOSE_SIZE
			&& my >= this.closeY && my < this.closeY + CLOSE_SIZE) {
			close();
			return true;
		}
		if (mx >= this.panelX && mx < this.panelX + this.panelW
			&& my >= this.panelY && my < this.panelY + this.panelH) {
			return true;
		}
		close();
		return true;
	}

	private static List<String> buildLines(View view) {
		List<String> lines = new ArrayList<>();
		if (view.xpNeeded() < 0F) {
			if (view.modsLabel() != null && !view.modsLabel().isBlank()) {
				lines.add(view.modsLabel());
			}
			return lines;
		}
		if (view.isAverage()) {
			if (view.modsLabel() != null && !view.modsLabel().isBlank()) {
				lines.add(view.modsLabel());
			}
			String floor = view.floorLabel() == null || view.floorLabel().isBlank() ? "-" : view.floorLabel();
			lines.add("Total: " + FormatUtil.commas(view.totalRuns()) + " " + floor + " runs (S+ est.)");
			if (view.timedOut()) {
				lines.add("Timed out (hit run cap).");
			}
			lines.add("Selected runs per class:");
			lines.add("");
			for (ClassBlock block : view.classBlocks()) {
				if (block.selectedRuns() <= 0L) {
					lines.add(block.name() + " - 0 (already covered)");
				} else {
					lines.add(block.name()
						+ " ("
						+ block.currentLevel()
						+ ")  "
						+ FormatUtil.commas(block.selectedRuns())
						+ " runs");
				}
			}
			return lines;
		}

		lines.add("XP needed: " + FormatUtil.commas(Math.round(view.xpNeeded())));
		if (view.modsLabel() != null && !view.modsLabel().isBlank()) {
			lines.add(view.modsLabel());
		}
		lines.add("");
		if (view.floors().isEmpty()) {
			if (view.xpNeeded() <= 0F) {
				lines.add("Already at or above target.");
			} else {
				lines.add("No runnable floors found.");
			}
			return lines;
		}
		lines.add("Best floors (S+ est.):");
		for (FloorLine floor : view.floors()) {
			lines.add(floor.label()
				+ "  "
				+ FormatUtil.commas(floor.runsNeeded())
				+ " runs  ("
				+ FormatUtil.shortXp(floor.xpPerRun())
				+ "/run)");
		}
		return lines;
	}
}
