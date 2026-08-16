package dev.vy.betterpv.client.gui.rift.page;

import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.InventorySnapshot;
import dev.vy.betterpv.client.data.RiftSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.gui.rift.RiftUi;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import static dev.vy.betterpv.client.gui.rift.RiftUi.*;

/** Rift overview: progress bars + timecharms / zone unlocks. */
public final class RiftOverviewPage {
	public void render(
		RiftSnapshot snapshot, RiftUi ui,
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int rightW = Math.max(200, w * 48 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);
		drawOverviewLeft(snapshot, ui, g, font, x, y, leftW, h, mx, my);
		drawOverviewRight(snapshot, ui, g, font, x + leftW + GAP, y, rightW, h, mx, my);
	}

	private void drawOverviewLeft(
		RiftSnapshot snapshot, RiftUi ui,
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;

		ly = RiftUi.statLine(g, font, "Motes", FormatUtil.commas(snapshot.motesPurse()),
			lx, ly, lw, MOTES_COLOR) + 2;
		ly = RiftUi.statLine(g, font, "Lifetime motes", FormatUtil.commas(snapshot.lifetimeMotes()),
			lx, ly, lw, PvDraw.COLOR_MUTED);
		if (snapshot.visits() > 0) {
			ly = RiftUi.statLine(g, font, "Visits", FormatUtil.commas(snapshot.visits()),
				lx, ly, lw, PvDraw.COLOR_MUTED);
		}
		if (snapshot.lastFreeAccessMs() > 0L) {
			ly = RiftUi.statLine(g, font, "Last free entry", FormatUtil.ago(snapshot.lastFreeAccessMs()),
				lx, ly, lw, PvDraw.COLOR_ACCENT);
		}

		ly = RiftUi.sectionSeparator(g, font, x, ly, w);

		RiftSnapshot.VampireProgress vamp = snapshot.vampire();
		ly = ui.drawLabeledBar(g, font, "Vampire", "T" + vamp.level(),
			vamp.fill(), vamp.maxed(), PvDraw.COLOR_BAR_FILL_SLAYER, vamp.hover(),
			lx, ly, lw, mx, my) + BAR_AFTER;

		ly = RiftUi.sectionSeparator(g, font, x, ly, w);

		String enigmaTip = "Found " + snapshot.enigmaFound() + " / " + RiftSnapshot.ENIGMA_MAX
			+ (snapshot.enigmaCloakBought() ? " · Cloak owned" : " · Cloak not bought");
		ly = ui.drawLabeledBar(g, font, "Enigma souls",
			snapshot.enigmaFound() + "/" + RiftSnapshot.ENIGMA_MAX,
			snapshot.enigmaFill(), snapshot.enigmaFound() >= RiftSnapshot.ENIGMA_MAX,
			ENIGMA_COLOR, enigmaTip, lx, ly, lw, mx, my) + BAR_AFTER;

		String charmTip = snapshot.timecharmsSecured() + " / " + RiftSnapshot.TIMECHARM_MAX
			+ " secured in the gallery";
		ly = ui.drawLabeledBar(g, font, "Timecharms",
			snapshot.timecharmsSecured() + "/" + RiftSnapshot.TIMECHARM_MAX,
			snapshot.timecharmFill(), snapshot.timecharmsSecured() >= RiftSnapshot.TIMECHARM_MAX,
			TIMECHARM_COLOR, charmTip, lx, ly, lw, mx, my) + BAR_AFTER;

		ly = ui.drawLabeledBar(g, font, "McGrubber's burgers",
			snapshot.burgers() + "/" + RiftSnapshot.BURGER_MAX,
			snapshot.burgerFill(), snapshot.burgers() >= RiftSnapshot.BURGER_MAX,
			BURGER_COLOR, "Grubber stacks from Castle burgers", lx, ly, lw, mx, my) + BAR_AFTER;

		String catTip = snapshot.montezumaUnlocked()
			? "Montezuma unlocked"
				+ (snapshot.montezumaTier().isBlank() ? "" : " · " + RiftUi.prettyTier(snapshot.montezumaTier()))
			: "Montezuma not unlocked";
		ly = ui.drawLabeledBar(g, font, "Montezuma's Souls",
			snapshot.catsFound() + "/" + RiftSnapshot.MONTEZUMA_CATS_MAX,
			snapshot.catsFill(), snapshot.catsFound() >= RiftSnapshot.MONTEZUMA_CATS_MAX,
			CAT_COLOR, catTip, lx, ly, lw, mx, my) + BAR_AFTER;

		ui.drawLabeledBar(g, font, "Porhtal eyes",
			snapshot.eyesKilled() + "/" + RiftSnapshot.EYES_MAX,
			snapshot.eyesFill(), snapshot.eyesKilled() >= RiftSnapshot.EYES_MAX,
			EYE_COLOR, "Rogue eyes calmed for Porhtal", lx, ly, lw, mx, my);
	}

	private void drawOverviewRight(
		RiftSnapshot snapshot, RiftUi ui,
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Timecharms", lx, ly, TIMECHARM_COLOR);
		ly += STAT_ROW + 2;

		for (RiftSnapshot.Timecharm charm : snapshot.timecharms()) {
			if (ly + CHARM_ROW > bottom) {
				break;
			}
			ly = drawTimecharmRow(ui, g, font, charm, lx, ly, lw, mx, my);
		}

		ly = RiftUi.sectionSeparator(g, font, x, ly, w);
		if (ly + STAT_ROW > bottom) {
			return;
		}

		PvDraw.text(g, font, "Zone unlocks", lx, ly, ZONE_COLOR);
		ly += STAT_ROW;
		List<String> zones = snapshot.purchasedBoundaries();
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
			PvDraw.text(g, font, RiftUi.trim(font, label, lw), lx, ly, PvDraw.COLOR_TEXT);
			ui.zones.add(new RiftUi.HoverZone(lx, ly, lw, STAT_ROW, List.of(
				PvTooltip.Line.title(label, PvDraw.COLOR_TEXT),
				PvTooltip.Line.divider(),
				PvTooltip.Line.meta(zone)
			)));
			ly += STAT_ROW;
		}

		if (!snapshot.foundCats().isEmpty() && ly + SEP_GAP + STAT_ROW * 2 < bottom) {
			ly = RiftUi.sectionSeparator(g, font, x, ly, w);
			PvDraw.text(g, font, "Montezuma's Souls", lx, ly, CAT_COLOR);
			ly += STAT_ROW;
			for (String cat : snapshot.foundCats()) {
				if (ly + STAT_ROW > bottom) {
					break;
				}
				String label = InventoryDecoder.prettyWords(cat);
				PvDraw.text(g, font, RiftUi.trim(font, label, lw), lx, ly, PvDraw.COLOR_TEXT);
				ly += STAT_ROW;
			}
		}

		if (!snapshot.killedEyes().isEmpty() && ly + SEP_GAP + STAT_ROW * 2 < bottom) {
			ly = RiftUi.sectionSeparator(g, font, x, ly, w);
			PvDraw.text(g, font, "Killed eyes", lx, ly, EYE_COLOR);
			ly += STAT_ROW;
			for (String eye : snapshot.killedEyes()) {
				if (ly + STAT_ROW > bottom) {
					break;
				}
				String label = InventoryDecoder.prettyWords(eye);
				PvDraw.text(g, font, RiftUi.trim(font, label, lw), lx, ly, PvDraw.COLOR_TEXT);
				ly += STAT_ROW;
			}
		}
	}

	private int drawTimecharmRow(
		RiftUi ui, GuiGraphicsExtractor g, Font font, RiftSnapshot.Timecharm charm,
		int x, int y, int w, int mx, int my
	) {
		ItemStack icon = SkyBlockItemFactory.toStack(new InventorySnapshot.Slot(
			charm.itemId(), 1, List.of(), charm.name(), null, null, null
		));

		PvDraw.IconTextAlign rowAlign = PvDraw.IconTextAlign.of(y, CHARM_ROW, CHARM_ICON, font.lineHeight);
		RiftUi.drawItemIcon(g, icon, charm.itemId(), x, rowAlign.iconY(), CHARM_ICON);

		int textX = x + CHARM_ICON + 4;
		String status = charm.secured() ? "Obtained" : "Not obtained";
		int statusColor = charm.secured() ? OBTAINED : NOT_OBTAINED;
		int statusW = font.width(status);
		int nameMax = Math.max(20, w - CHARM_ICON - 4 - statusW - 6);
		String name = RiftUi.trim(font, charm.name(), nameMax);
		PvDraw.text(g, font, name, textX, rowAlign.textY(), charm.color());
		PvDraw.textRight(g, font, status, x + w, rowAlign.textY(), statusColor);

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
			tip.add(PvTooltip.Line.meta("Secured " + RiftUi.formatAgo(charm.securedAtMs())));
		}
		ui.zones.add(new RiftUi.HoverZone(x, y, w, CHARM_ROW, tip));
		return y + CHARM_ROW + 1;
	}
}
