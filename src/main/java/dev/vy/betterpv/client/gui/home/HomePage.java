package dev.vy.betterpv.client.gui.home;

import com.mojang.authlib.GameProfile;
import dev.vy.betterpv.BetterPV;
import dev.vy.betterpv.client.cosmetics.NameStyler;
import dev.vy.betterpv.client.cosmetics.BetterPvCosmetics;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.ProfileSnapshot;
import dev.vy.betterpv.client.gui.PlayerModelRenderer;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.SkyBlockLevelColors;
import dev.vy.betterpv.client.networth.NetworthBreakdown;
import dev.vy.betterpv.client.weight.WeightBreakdown;
import dev.vy.betterpv.client.weight.WeightSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

public final class HomePage {
	private static final Identifier SKYBLOCK_XP_ICON = Identifier.fromNamespaceAndPath(BetterPV.MOD_ID, "textures/gui/skyblock_xp.png");
	private static final int SKYBLOCK_XP_TEX_SIZE = 64;
	private static final int ICON_SIZE = 16;
	private static final int BAR_H = 6;
	private static final int BAR_LABEL_GAP = 2;
	private static final int BAR_AFTER_GAP = 6;
	private static final int SECTION_GAP = 8;
	private static final int PAD = 6;
	private static final int SKILL_ROWS = 5;
	private static final int SLAYER_ROWS = 3;

	private ProfileSnapshot snapshot;
	private WeightBreakdown senither = WeightBreakdown.empty(WeightSystem.SENITHER);
	private WeightBreakdown lily = WeightBreakdown.empty(WeightSystem.LILY);
	private NetworthBreakdown networth = NetworthBreakdown.empty("");
	private WeightSystem weightSystem = WeightSystem.SENITHER;
	private String loadError;

	private int weightHitX;
	private int weightHitY;
	private int weightHitW;
	private int weightHitH;
	private int networthHitX;
	private int networthHitY;
	private int networthHitW;
	private int networthHitH;
	private final PlayerModelRenderer playerModel = new PlayerModelRenderer();
	private ItemStack[] armor = new ItemStack[] {
		ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
	};
	private final List<HoverZone> zones = new ArrayList<>();
	private int layoutCacheW = Integer.MIN_VALUE;
	private int layoutCacheH = -1;

	public HomePage(ProfileSnapshot snapshot) {
		this.snapshot = snapshot;
	}

	public void applyLoaded(
		ProfileSnapshot snapshot,
		WeightBreakdown senither,
		WeightBreakdown lily,
		NetworthBreakdown networth,
		ItemStack[] armor,
		String error
	) {
		this.snapshot = snapshot;
		this.senither = senither == null ? WeightBreakdown.empty(WeightSystem.SENITHER) : senither;
		this.lily = lily == null ? WeightBreakdown.empty(WeightSystem.LILY) : lily;
		this.networth = networth == null ? NetworthBreakdown.empty("") : networth;
		this.armor = armor == null
			? new ItemStack[] { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY }
			: armor;
		this.loadError = error;
		invalidateLayoutCache();
	}

	public boolean clickWeight(double mouseX, double mouseY) {
		if (mouseX >= this.weightHitX && mouseX < this.weightHitX + this.weightHitW
			&& mouseY >= this.weightHitY && mouseY < this.weightHitY + this.weightHitH) {
			this.weightSystem = this.weightSystem.other();
			return true;
		}
		return false;
	}

	public int preferredHeight(Font font, int width) {
		if (width == this.layoutCacheW && this.layoutCacheH >= 0) {
			return this.layoutCacheH;
		}
		this.layoutCacheW = width;
		this.layoutCacheH = measure(font, width).contentH;
		return this.layoutCacheH;
	}

	private void invalidateLayoutCache() {
		this.layoutCacheW = Integer.MIN_VALUE;
		this.layoutCacheH = -1;
	}

	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH) {
		this.zones.clear();
		Layout layout = measure(font, w);
		int contentH = Math.min(h, layout.contentH);
		int leftX = x;
		int levelX = x + layout.leftW + layout.gap;
		int barsX = levelX + layout.levelW + layout.gap;

		drawLeftColumn(g, font, leftX, y, layout.leftW, contentH, layout, mouseX, mouseY);
		drawSkyBlockLevel(g, font, levelX, y, layout.levelW, contentH, layout);
		drawBarsColumn(g, font, barsX, y, layout.barsW, contentH, layout);

		List<String> tip = null;
		List<PvTooltip.Line> styledTip = null;
		if (mouseX >= this.weightHitX && mouseX < this.weightHitX + this.weightHitW
			&& mouseY >= this.weightHitY && mouseY < this.weightHitY + this.weightHitH) {
			if (this.loadError != null && !this.loadError.isBlank()) {
				tip = List.of("Weight unavailable", this.loadError);
			} else {
				tip = activeWeight().tooltipLines();
			}
		} else if (mouseX >= this.networthHitX && mouseX < this.networthHitX + this.networthHitW
			&& mouseY >= this.networthHitY && mouseY < this.networthHitY + this.networthHitH) {
			styledTip = this.networth.tooltipStyledLines();
		} else {
			for (HoverZone zone : this.zones) {
				if (mouseX >= zone.x && mouseX < zone.x + zone.w && mouseY >= zone.y && mouseY < zone.y + zone.h) {
					tip = List.of(zone.text);
					break;
				}
			}
		}
		if (styledTip != null) {
			PvTooltip.drawStyled(g, font, styledTip, mouseX, mouseY, screenW, screenH);
		} else if (tip != null) {
			PvTooltip.draw(g, font, tip, mouseX, mouseY, screenW, screenH);
		}
	}

	private WeightBreakdown activeWeight() {
		return this.weightSystem == WeightSystem.SENITHER ? this.senither : this.lily;
	}

	private Layout measure(Font font, int w) {
		Layout layout = new Layout();
		layout.gap = 6;
		// Slim SB-level column: short XP bar, width only needs the level label.
		layout.levelW = 84;
		layout.leftW = Math.max(100, (w - layout.levelW - layout.gap * 2) * 30 / 100);
		layout.barsW = w - layout.leftW - layout.levelW - layout.gap * 2;
		layout.line = font.lineHeight + 2;
		layout.rowH = font.lineHeight + BAR_LABEL_GAP + BAR_H + BAR_AFTER_GAP;
		layout.barsInnerH = SKILL_ROWS * layout.rowH + SECTION_GAP + SLAYER_ROWS * layout.rowH - BAR_AFTER_GAP;
		layout.barsH = PAD * 2 + layout.barsInnerH;
		layout.lastSlayerNameY = PAD + SKILL_ROWS * layout.rowH + SECTION_GAP + (SLAYER_ROWS - 1) * layout.rowH;
		layout.statsH = PAD + layout.line * 2 + 6;
		layout.profileLineH = font.lineHeight;
		layout.nameLineH = font.lineHeight;
		layout.nameGap = 2;
		layout.boxToSocialGap = 6;
		layout.socialToProfileGap = 6;
		layout.profileY = layout.lastSlayerNameY;
		layout.socialBarY = layout.profileY - layout.socialToProfileGap - BAR_H;
		layout.socialLabelY = layout.socialBarY - BAR_LABEL_GAP - layout.nameLineH;
		layout.boxBottom = layout.socialLabelY - layout.boxToSocialGap;
		layout.nameY = layout.statsH;
		layout.boxTop = layout.nameY + layout.nameLineH + layout.nameGap;
		layout.boxH = Math.max(48, layout.boxBottom - layout.boxTop);
		layout.leftH = layout.profileY + layout.profileLineH + PAD;
		layout.contentH = Math.max(layout.leftH, layout.barsH);
		layout.levelH = layout.contentH;
		return layout;
	}

	private void drawLeftColumn(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		Layout layout,
		int mouseX,
		int mouseY
	) {
		PvDraw.innerPanel(g, x, y, w, h);

		int ty = y + PAD;
		String nwLabel = Component.translatable("betterpv.home.networth_label").getString();
		String weightLabel = Component.translatable("betterpv.home.weight_label").getString();
		String nwValue = this.snapshot.networthText();
		String weightValue = this.weightSystem == WeightSystem.SENITHER
			? FormatUtil.weight(this.senither.total())
			: FormatUtil.weight(this.lily.total());
		if (this.snapshot.weightText().equals("…") && (this.loadError == null || this.loadError.isBlank())) {
			weightValue = "…";
		}
		if (this.loadError != null && !this.loadError.isBlank() && this.senither.total() <= 0 && this.lily.total() <= 0) {
			weightValue = "—";
		}

		int nwLabelW = font.width(nwLabel);
		int nwValueW = PvDraw.widthBold(font, nwValue);
		int nwLineW = nwLabelW + nwValueW;
		int nwX = x + (w - nwLineW) / 2;
		PvDraw.text(g, font, nwLabel, nwX, ty, PvDraw.COLOR_TEXT);
		PvDraw.textBold(g, font, nwValue, nwX + nwLabelW, ty, PvDraw.COLOR_GOLD);
		this.networthHitX = nwX;
		this.networthHitY = ty;
		this.networthHitW = nwLineW;
		this.networthHitH = font.lineHeight;

		ty += layout.line;
		int weightLabelW = font.width(weightLabel);
		int weightValueW = PvDraw.widthBold(font, weightValue);
		int weightLineW = weightLabelW + weightValueW;
		int weightX = x + (w - weightLineW) / 2;
		PvDraw.text(g, font, weightLabel, weightX, ty, PvDraw.COLOR_TEXT);
		PvDraw.textBold(g, font, weightValue, weightX + weightLabelW, ty, PvDraw.COLOR_GOLD);
		this.weightHitX = weightX;
		this.weightHitY = ty;
		this.weightHitW = weightLineW;
		this.weightHitH = font.lineHeight;

		int cx = x + w / 2;
		int boxW = Math.min(w - PAD * 2, Math.max(64, w - 20));
		int boxX = cx - boxW / 2;
		int boxTop = y + layout.boxTop;
		int boxH = layout.boxH;

		PvDraw.textCentered(g, font, styledPlayerName(), cx, y + layout.nameY);

		PvDraw.fill(g, boxX, boxTop, boxW, boxH, 0xFF15151E);
		g.outline(boxX, boxTop, boxW, boxH, PvDraw.COLOR_BORDER);
		if (this.snapshot.playerUuid() != null) {
			this.playerModel.draw(
				g,
				this.snapshot.playerUuid(),
				this.snapshot.playerName(),
				boxX + 2,
				boxTop + 2,
				boxX + boxW - 2,
				boxTop + boxH - 2,
				mouseX,
				mouseY,
				this.armor.length > 3 ? this.armor[3] : ItemStack.EMPTY,
				this.armor.length > 2 ? this.armor[2] : ItemStack.EMPTY,
				this.armor.length > 1 ? this.armor[1] : ItemStack.EMPTY,
				this.armor.length > 0 ? this.armor[0] : ItemStack.EMPTY
			);
		} else {
			PvDraw.textCentered(
				g, font,
				Component.translatable("betterpv.home.player").getString(),
				cx, boxTop + boxH / 2 - font.lineHeight / 2,
				PvDraw.COLOR_MUTED
			);
		}

		ProfileSnapshot.SkillEntry social = this.snapshot.social();
		int socialLabelY = y + layout.socialLabelY;
		int socialBarY = y + layout.socialBarY;
		int socialBarW = Math.min(boxW, Math.max(48, boxW - 8));
		int socialBarX = cx - socialBarW / 2;
		PvDraw.textCentered(g, font, social.name(), cx, socialLabelY, PvDraw.COLOR_TEXT);
		PvDraw.progressBar(
			g, socialBarX, socialBarY, socialBarW, BAR_H,
			social.progress(), PvDraw.COLOR_BAR_FILL, social.maxed()
		);
		this.zones.add(new HoverZone(socialBarX, socialLabelY, socialBarW, socialBarY + BAR_H - socialLabelY + 2, social.xpHover()));

		PvDraw.textCentered(
			g, font,
			Component.translatable("betterpv.home.profile", this.snapshot.profileName()).getString(),
			cx, y + layout.profileY,
			PvDraw.COLOR_MUTED
		);
	}

	private Component styledPlayerName() {
		String name = this.snapshot.playerName();
		Component base = Component.literal(name == null ? "" : name);
		if (this.snapshot.playerUuid() == null) {
			return NameStyler.applyGradientToName(base);
		}
		GameProfile profile = new GameProfile(this.snapshot.playerUuid(), name == null ? "" : name);
		Component styled = BetterPvCosmetics.styleDisplayName(base, profile);
		if (styled != base) {
			return styled;
		}
		// Fallback: Font-mixin gradient path (username match) when display-profile override is absent.
		return NameStyler.applyGradientToName(base);
	}

	private void drawSkyBlockLevel(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Layout layout) {
		PvDraw.innerPanel(g, x, y, w, h);
		int level = this.snapshot.skyBlockLevel();
		int xp = this.snapshot.skyBlockXpIntoLevel();
		String levelText = Component.translatable("betterpv.home.sb_level", level).getString();
		int levelColor = SkyBlockLevelColors.colorFor(level);
		int barW = Math.min(w - 16, 48);
		int cx = x + w / 2;

		int blockH = font.lineHeight + 4 + ICON_SIZE + 4 + font.lineHeight + 2 + BAR_H;
		int ty = y + Math.max(PAD, (h - blockH) / 2);

		PvDraw.textCentered(g, font, levelText, cx, ty, levelColor);
		ty += font.lineHeight + 4;
		g.blit(
			RenderPipelines.GUI_TEXTURED,
			SKYBLOCK_XP_ICON,
			cx - ICON_SIZE / 2, ty,
			0, 0,
			ICON_SIZE, ICON_SIZE,
			SKYBLOCK_XP_TEX_SIZE, SKYBLOCK_XP_TEX_SIZE,
			SKYBLOCK_XP_TEX_SIZE, SKYBLOCK_XP_TEX_SIZE
		);
		ty += ICON_SIZE + 4;

		Component xpLine = Component.empty()
			.append(PvDraw.styled(String.valueOf(xp), PvDraw.COLOR_WHITE, false))
			.append(PvDraw.styled("/", PvDraw.COLOR_MUTED, false))
			.append(PvDraw.styled("100", levelColor, false));
		PvDraw.textCentered(g, font, xpLine, cx, ty);
		ty += font.lineHeight + 2;

		int barX = cx - barW / 2;
		PvDraw.progressBar(g, barX, ty, barW, BAR_H, this.snapshot.skyBlockProgress(), levelColor);
		double pct = this.snapshot.skyBlockProgress() * 100.0;
		String xpHover = xp + "/100 (" + Math.round(pct) + "%)";
		this.zones.add(new HoverZone(barX, ty - font.lineHeight - 2, barW, font.lineHeight + 2 + BAR_H + 2, xpHover));
	}

	private void drawBarsColumn(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Layout layout) {
		PvDraw.innerPanel(g, x, y, w, h);
		int colGap = 8;
		int colW = (w - PAD * 2 - colGap) / 2;
		int leftX = x + PAD;
		int rightX = leftX + colW + colGap;

		int ty = y + PAD;
		drawSkillGrid(g, font, leftX, rightX, ty, colW, layout.rowH);
		// Visual midpoint between last skill bars and first slayer labels (not mid-SECTION_GAP alone).
		int skillsBarsBottom = ty + (SKILL_ROWS - 1) * layout.rowH + font.lineHeight + BAR_LABEL_GAP + BAR_H;
		int slayersTop = ty + SKILL_ROWS * layout.rowH + SECTION_GAP;
		int lineInset = PAD + 6;
		int lineY = (skillsBarsBottom + slayersTop) / 2;
		int lineW = Math.max(0, w - lineInset * 2);
		if (lineW > 0) {
			PvDraw.fill(g, x + lineInset, lineY, lineW, 1, PvDraw.COLOR_BORDER);
		}
		drawSlayerGrid(g, font, leftX, rightX, slayersTop, colW, layout.rowH);
	}

	private void drawSkillGrid(GuiGraphicsExtractor g, Font font, int leftX, int rightX, int startY, int colW, int rowH) {
		List<ProfileSnapshot.SkillEntry> skills = this.snapshot.skills();
		int limit = Math.min(skills.size(), SKILL_ROWS * 2);
		for (int i = 0; i < limit; i++) {
			ProfileSnapshot.SkillEntry skill = skills.get(i);
			boolean left = (i % 2) == 0;
			int row = i / 2;
			int bx = left ? leftX : rightX;
			int by = startY + row * rowH;
			PvDraw.labeledBar(
				g, font,
				skill.name(), String.valueOf(skill.level()), skill.progress(),
				bx, by, colW, PvDraw.COLOR_BAR_FILL, skill.maxed()
			);
			this.zones.add(new HoverZone(bx, by, colW, rowH - 2, skill.xpHover()));
		}
	}

	private void drawSlayerGrid(GuiGraphicsExtractor g, Font font, int leftX, int rightX, int startY, int colW, int rowH) {
		List<ProfileSnapshot.SlayerEntry> slayers = this.snapshot.slayers();
		int limit = Math.min(slayers.size(), SLAYER_ROWS * 2);
		for (int i = 0; i < limit; i++) {
			ProfileSnapshot.SlayerEntry slayer = slayers.get(i);
			boolean left = (i % 2) == 0;
			int row = i / 2;
			int bx = left ? leftX : rightX;
			int by = startY + row * rowH;
			PvDraw.labeledBar(
				g, font,
				slayer.name(), "T" + slayer.tier(), slayer.progress(),
				bx, by, colW, PvDraw.COLOR_BAR_FILL_SLAYER, slayer.maxed()
			);
			this.zones.add(new HoverZone(bx, by, colW, rowH - 2, slayer.xpHover()));
		}
	}

	private record HoverZone(int x, int y, int w, int h, String text) {
	}

	private static final class Layout {
		int gap;
		int leftW;
		int levelW;
		int barsW;
		int line;
		int rowH;
		int barsInnerH;
		int barsH;
		int lastSlayerNameY;
		int statsH;
		int profileLineH;
		int nameLineH;
		int nameGap;
		int boxToSocialGap;
		int socialToProfileGap;
		int profileY;
		int nameY;
		int socialLabelY;
		int socialBarY;
		int boxTop;
		int boxBottom;
		int boxH;
		int leftH;
		int levelH;
		int contentH;
	}
}
