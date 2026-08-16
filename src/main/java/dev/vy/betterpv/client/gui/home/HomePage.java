package dev.vy.betterpv.client.gui.home;

import com.mojang.authlib.GameProfile;
import com.google.gson.JsonObject;
import dev.vy.betterpv.BetterPV;
import dev.vy.betterpv.client.cosmetics.NameStyler;
import dev.vy.betterpv.client.cosmetics.BetterPvCosmetics;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.HypixelRanks;
import dev.vy.betterpv.client.data.PlayerStatsSnapshot;
import dev.vy.betterpv.client.data.PlayerStatus;
import dev.vy.betterpv.client.data.ProfileSnapshot;
import dev.vy.betterpv.client.data.UsernameHistory;
import dev.vy.betterpv.client.gui.PlayerModelRenderer;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.SkyBlockLevelColors;
import dev.vy.betterpv.client.gui.SkyBlockStats;
import dev.vy.betterpv.client.networth.NetworthBreakdown;
import dev.vy.betterpv.client.networth.NetworthMode;
import dev.vy.betterpv.client.weight.WeightBreakdown;
import dev.vy.betterpv.client.weight.WeightSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
	private static final int FLIP_MS = 480;
	private static final int PANEL_HOVER = 0x0AFFFFFF;

	private ProfileSnapshot snapshot;
	private PlayerStatsSnapshot playerStats = PlayerStatsSnapshot.empty();
	private WeightBreakdown senither = WeightBreakdown.empty(WeightSystem.SENITHER);
	private WeightBreakdown lily = WeightBreakdown.empty(WeightSystem.LILY);
	private NetworthBreakdown networthNormal = NetworthBreakdown.empty("");
	private NetworthBreakdown networthNonCosmetic = NetworthBreakdown.empty("");
	private NetworthBreakdown networthUnsoulbound = NetworthBreakdown.empty("");
	private NetworthBreakdown networthUnsoulboundNonCosmetic = NetworthBreakdown.empty("");
	private WeightSystem weightSystem = WeightSystem.SENITHER;
	private NetworthMode networthMode = NetworthMode.NORMAL;
	private String loadError;

	private int weightHitX;
	private int weightHitY;
	private int weightHitW;
	private int weightHitH;
	private int networthHitX;
	private int networthHitY;
	private int networthHitW;
	private int networthHitH;
	private int nameHitX;
	private int nameHitY;
	private int nameHitW;
	private int nameHitH;
	private int statusHitX;
	private int statusHitY;
	private int statusHitW;
	private int statusHitH;
	private int leftHitX;
	private int leftHitY;
	private int leftHitW;
	private int leftHitH;
	private int bankHitX;
	private int bankHitY;
	private int bankHitW;
	private int bankHitH;
	/** False = profile face, true = stats face. */
	private boolean leftStatsFace;
	private long leftFlipStartMs;
	private boolean leftFlipTarget;
	private UsernameHistory usernameHistory = UsernameHistory.idle();
	private int historyScroll;
	private int historyMaxScroll;
	private PlayerStatus playerStatus = PlayerStatus.idle();
	private JsonObject playerRank;
	private final PlayerModelRenderer playerModel = new PlayerModelRenderer();
	private ItemStack[] armor = new ItemStack[] {
		ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
	};
	private final List<HoverZone> zones = new ArrayList<>();
	private Layout cachedLayout;
	private int layoutCacheW = Integer.MIN_VALUE;
	private int layoutCacheLineH = -1;
	private float openScale = 1.0F;
	private float openPivotX;
	private float openPivotY;
	private final int[] modelBoxScratch = new int[4];
	private Component cachedNwLine;
	private Component cachedWeightLine;
	private Component cachedSocialLine;
	private int cachedSocialBarW = -1;

	public HomePage(ProfileSnapshot snapshot) {
		this.snapshot = snapshot;
	}

	public void applyLoaded(
		ProfileSnapshot snapshot,
		WeightBreakdown senither,
		WeightBreakdown lily,
		NetworthBreakdown networthNormal,
		NetworthBreakdown networthNonCosmetic,
		NetworthBreakdown networthUnsoulbound,
		NetworthBreakdown networthUnsoulboundNonCosmetic,
		ItemStack[] armor,
		PlayerStatsSnapshot playerStats,
		String error
	) {
		UUID prevUuid = this.snapshot == null ? null : this.snapshot.playerUuid();
		UUID nextUuid = snapshot == null ? null : snapshot.playerUuid();
		this.snapshot = snapshot;
		this.cachedStyledName = null;
		this.cachedStyledNameKey = "";
		this.cachedNameWidth = -1;
		this.cachedNwValue = "";
		this.cachedWeightValue = "";
		this.cachedNwTotal = Double.NaN;
		this.cachedWeightTotal = Double.NaN;
		this.cachedStatusKey = "";
		this.cachedBarColW = -1;
		this.cachedSkillBars = List.of();
		this.cachedSlayerBars = List.of();
		this.cachedNwLine = null;
		this.cachedWeightLine = null;
		this.cachedSocialLine = null;
		this.cachedSocialBarW = -1;
		this.senither = senither == null ? WeightBreakdown.empty(WeightSystem.SENITHER) : senither;
		this.lily = lily == null ? WeightBreakdown.empty(WeightSystem.LILY) : lily;
		this.networthNormal = networthNormal == null ? NetworthBreakdown.empty("") : networthNormal;
		this.networthNonCosmetic = networthNonCosmetic == null ? NetworthBreakdown.empty("") : networthNonCosmetic;
		this.networthUnsoulbound = networthUnsoulbound == null ? NetworthBreakdown.empty("") : networthUnsoulbound;
		this.networthUnsoulboundNonCosmetic = networthUnsoulboundNonCosmetic == null ? NetworthBreakdown.empty("") : networthUnsoulboundNonCosmetic;
		this.armor = armor == null
			? new ItemStack[] { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY }
			: armor;
		this.playerStats = playerStats == null ? PlayerStatsSnapshot.empty() : playerStats;
		this.loadError = error;
		this.usernameHistory = UsernameHistory.idle();
		this.playerStatus = PlayerStatus.idle();
		if (nextUuid == null || prevUuid == null || !nextUuid.equals(prevUuid)) {
			this.playerRank = null;
		}
		invalidateLayoutCache();
	}

	public void applyPlayerRank(JsonObject player) {
		this.playerRank = player;
		this.cachedStyledName = null;
		this.cachedStyledNameKey = "";
		this.cachedNameWidth = -1;
	}

	public void applyUsernameHistory(UsernameHistory history) {
		this.usernameHistory = history == null ? UsernameHistory.idle() : history;
		this.historyScroll = 0;
		this.historyMaxScroll = 0;
	}

	public void applyPlayerStatus(PlayerStatus status) {
		this.playerStatus = status == null ? PlayerStatus.idle() : status;
	}

	public UsernameHistory usernameHistory() {
		return this.usernameHistory;
	}

	public PlayerStatus playerStatus() {
		return this.playerStatus;
	}

	public String profileName() {
		return this.snapshot == null ? "" : this.snapshot.profileName();
	}

	public String playerName() {
		return this.snapshot == null ? "" : this.snapshot.playerName();
	}

	/** Applies newly available networth data without replacing the already-rendered profile. */
	public void applyNetworth(
		NetworthBreakdown normal,
		NetworthBreakdown nonCosmetic,
		NetworthBreakdown unsoulbound,
		NetworthBreakdown unsoulboundNonCosmetic
	) {
		if (normal != null) {
			this.networthNormal = normal;
		}
		if (nonCosmetic != null) {
			this.networthNonCosmetic = nonCosmetic;
		}
		if (unsoulbound != null) {
			this.networthUnsoulbound = unsoulbound;
		}
		if (unsoulboundNonCosmetic != null) {
			this.networthUnsoulboundNonCosmetic = unsoulboundNonCosmetic;
		}
		invalidateLayoutCache();
	}

	public boolean clickWeight(double mouseX, double mouseY) {
		if (showingLeftStatsFace()) {
			return false;
		}
		if (mouseX >= this.weightHitX && mouseX < this.weightHitX + this.weightHitW
			&& mouseY >= this.weightHitY && mouseY < this.weightHitY + this.weightHitH) {
			this.weightSystem = this.weightSystem.other();
			return true;
		}
		return false;
	}

	/** Flip the left profile / stats panel (ignores name / status / footer hits). */
	public boolean clickLeftPanel(double mouseX, double mouseY) {
		if (mouseX < this.leftHitX || mouseX >= this.leftHitX + this.leftHitW
			|| mouseY < this.leftHitY || mouseY >= this.leftHitY + this.leftHitH) {
			return false;
		}
		if (hitName(mouseX, mouseY) || hitStatus(mouseX, mouseY)) {
			return false;
		}
		if (this.leftFlipStartMs != 0L) {
			return true;
		}
		this.leftFlipTarget = !this.leftStatsFace;
		this.leftFlipStartMs = System.currentTimeMillis();
		return true;
	}

	public boolean hitName(double mouseX, double mouseY) {
		return !showingLeftStatsFace()
			&& mouseX >= this.nameHitX && mouseX < this.nameHitX + this.nameHitW
			&& mouseY >= this.nameHitY && mouseY < this.nameHitY + this.nameHitH
			&& this.nameHitW > 0;
	}

	public boolean hitStatus(double mouseX, double mouseY) {
		return !showingLeftStatsFace()
			&& mouseX >= this.statusHitX && mouseX < this.statusHitX + this.statusHitW
			&& mouseY >= this.statusHitY && mouseY < this.statusHitY + this.statusHitH
			&& this.statusHitW > 0;
	}

	/** @param button GLFW mouse button (0 = left / next, 1 = right / prev) */
	public boolean clickNetworth(double mouseX, double mouseY, int button) {
		if (showingLeftStatsFace()) {
			return false;
		}
		if (mouseX < this.networthHitX || mouseX >= this.networthHitX + this.networthHitW
			|| mouseY < this.networthHitY || mouseY >= this.networthHitY + this.networthHitH) {
			return false;
		}
		if (button == 0) {
			this.networthMode = this.networthMode.next();
			return true;
		}
		if (button == 1) {
			this.networthMode = this.networthMode.prev();
			return true;
		}
		return false;
	}

	private boolean showingLeftStatsFace() {
		if (this.leftFlipStartMs != 0L) {
			float progress = Math.min(1F, (System.currentTimeMillis() - this.leftFlipStartMs) / (float) FLIP_MS);
			float angle = easeInOutCubic(progress) * (float) Math.PI;
			return Math.cos(angle) < 0.0 ? this.leftFlipTarget : this.leftStatsFace;
		}
		return this.leftStatsFace;
	}

	private NetworthBreakdown activeNetworth() {
		return switch (this.networthMode) {
			case NORMAL -> this.networthNormal;
			case NON_COSMETIC -> this.networthNonCosmetic;
			case UNSOULBOUND -> this.networthUnsoulbound;
			case UNSOULBOUND_NON_COSMETIC -> this.networthUnsoulboundNonCosmetic;
		};
	}

	public int preferredHeight(Font font, int width) {
		return layoutFor(font, width).contentH;
	}

	private void invalidateLayoutCache() {
		this.layoutCacheW = Integer.MIN_VALUE;
		this.layoutCacheLineH = -1;
		this.cachedLayout = null;
	}

	private Layout layoutFor(Font font, int width) {
		if (this.cachedLayout != null
			&& width == this.layoutCacheW
			&& font.lineHeight == this.layoutCacheLineH) {
			return this.cachedLayout;
		}
		this.layoutCacheW = width;
		this.layoutCacheLineH = font.lineHeight;
		this.cachedLayout = measure(font, width);
		return this.cachedLayout;
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
		int screenH,
		float openScale,
		float openPivotX,
		float openPivotY
	) {
		this.openScale = openScale;
		this.openPivotX = openPivotX;
		this.openPivotY = openPivotY;
		this.zones.clear();
		Layout layout = layoutFor(font, w);
		int contentH = Math.min(h, layout.contentH);
		int leftX = x;
		int levelX = x + layout.leftW + layout.gap;
		int barsX = levelX + layout.levelW + layout.gap;

		drawLeftColumn(g, font, leftX, y, layout.leftW, contentH, layout, mouseX, mouseY);
		drawSkyBlockLevel(g, font, levelX, y, layout.levelW, contentH, layout);
		drawBarsColumn(g, font, barsX, y, layout.barsW, contentH, layout);

		List<PvTooltip.Line> styledTip = null;
		boolean onProfileFace = !showingLeftStatsFace();
		if (onProfileFace && hitName(mouseX, mouseY)) {
			drawUsernameHistoryTooltip(g, font, mouseX, mouseY, screenW, screenH);
		} else if (onProfileFace && hitStatus(mouseX, mouseY)) {
			styledTip = statusTooltip();
		} else if (onProfileFace && mouseX >= this.weightHitX && mouseX < this.weightHitX + this.weightHitW
			&& mouseY >= this.weightHitY && mouseY < this.weightHitY + this.weightHitH) {
			if (this.loadError != null && !this.loadError.isBlank()) {
				styledTip = List.of(
					PvTooltip.Line.of("Weight unavailable", PvDraw.COLOR_TEXT),
					PvTooltip.Line.of(this.loadError, PvDraw.COLOR_MUTED)
				);
			} else {
				styledTip = activeWeight().tooltipStyledLines();
			}
		} else if (onProfileFace && mouseX >= this.networthHitX && mouseX < this.networthHitX + this.networthHitW
			&& mouseY >= this.networthHitY && mouseY < this.networthHitY + this.networthHitH) {
			styledTip = activeNetworth().tooltipStyledLines(this.networthMode);
		} else if (onProfileFace && mouseX >= this.bankHitX && mouseX < this.bankHitX + this.bankHitW
			&& mouseY >= this.bankHitY && mouseY < this.bankHitY + this.bankHitH) {
			styledTip = bankTooltip();
		} else {
			for (HoverZone zone : this.zones) {
				if (mouseX >= zone.x && mouseX < zone.x + zone.w && mouseY >= zone.y && mouseY < zone.y + zone.h) {
					styledTip = zone.lines;
					break;
				}
			}
		}
		if (styledTip != null) {
			PvTooltip.drawStyled(g, font, styledTip, mouseX, mouseY, screenW, screenH);
		}
	}

	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH) {
		render(g, font, x, y, w, h, mouseX, mouseY, screenW, screenH, 1.0F, x + w / 2F, y + h / 2F);
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
		layout.statsH = PAD + layout.line * 4 + 6;
		layout.profileLineH = font.lineHeight;
		layout.nameLineH = font.lineHeight;
		layout.nameGap = 2;
		layout.boxToSocialGap = 6;
		layout.socialToFooterGap = 6;
		layout.statusH = font.lineHeight + 4;
		layout.footerH = layout.statusH;
		// Status sits under the social bar; Profile floats outside the main menu panel.
		layout.footerY = 0;
		layout.socialBarY = 0;
		layout.socialLabelY = 0;
		layout.boxBottom = 0;
		layout.nameY = layout.statsH;
		layout.boxTop = layout.nameY + layout.nameLineH + layout.nameGap;
		// Keep overall PV height on the bars column - do not grow for the status footer.
		layout.contentH = layout.barsH;
		layout.levelH = layout.contentH;
		layout.leftH = layout.contentH;
		// Stack status + social from the bottom; leftover height is the player preview.
		layout.footerY = layout.leftH - PAD - layout.statusH;
		layout.socialBarY = layout.footerY - layout.socialToFooterGap - BAR_H;
		layout.socialLabelY = layout.socialBarY - BAR_LABEL_GAP - layout.nameLineH;
		layout.boxBottom = layout.socialLabelY - layout.boxToSocialGap;
		layout.boxH = Math.max(48, layout.boxBottom - layout.boxTop);
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
		this.leftHitX = x;
		this.leftHitY = y;
		this.leftHitW = w;
		this.leftHitH = h;

		boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		float flipProgress = 0F;
		boolean animating = this.leftFlipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.leftFlipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.leftStatsFace = this.leftFlipTarget;
				this.leftFlipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		// Ease the timeline, then cosine-scale for a soft card flip (1 → 0 → 1).
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showStats = animating
			? (Math.cos(angle) < 0.0 ? this.leftFlipTarget : this.leftStatsFace)
			: this.leftStatsFace;
		float scaleX = 1F;
		float scaleY = 1F;
		if (animating) {
			scaleX = Math.max(0.04F, Math.abs((float) Math.cos(angle)));
			// Slight vertical squash near edge-on for a paper/card feel.
			scaleY = 1F - (1F - scaleX) * 0.06F;
		}

		float cxFlip = x + w / 2F;
		float cyFlip = y + h / 2F;
		g.pose().pushMatrix();
		g.pose().translate(cxFlip, cyFlip);
		g.pose().scale(scaleX, scaleY);
		g.pose().translate(-cxFlip, -cyFlip);

		PvDraw.innerPanel(g, x, y, w, h);
		if (hovered && !animating) {
			PvDraw.fill(g, x + 1, y + 1, w - 2, h - 2, PANEL_HOVER);
		}

		boolean drawModelAfter = false;
		int modelX0 = 0;
		int modelY0 = 0;
		int modelX1 = 0;
		int modelY1 = 0;
		if (showStats) {
			this.networthHitW = 0;
			this.weightHitW = 0;
			this.nameHitW = 0;
			this.statusHitW = 0;
			drawStatsFace(g, font, x, y, w, h);
		} else {
			int[] modelBox = drawProfileFace(g, font, x, y, w, h, layout, mouseX, mouseY);
			if (modelBox != null) {
				drawModelAfter = true;
				modelX0 = modelBox[0];
				modelY0 = modelBox[1];
				modelX1 = modelBox[2];
				modelY1 = modelBox[3];
			}
		}

		g.pose().popMatrix();

		// Entity rendering ignores the GUI pose matrix - apply the same flip in screen space after pop.
		if (drawModelAfter && this.snapshot.playerUuid() != null) {
			this.playerModel.draw(
				g,
				this.snapshot.playerUuid(),
				this.snapshot.playerName(),
				modelX0,
				modelY0,
				modelX1,
				modelY1,
				mouseX,
				mouseY,
				this.armor.length > 3 ? this.armor[3] : ItemStack.EMPTY,
				this.armor.length > 2 ? this.armor[2] : ItemStack.EMPTY,
				this.armor.length > 1 ? this.armor[1] : ItemStack.EMPTY,
				this.armor.length > 0 ? this.armor[0] : ItemStack.EMPTY,
				scaleX,
				scaleY,
				cxFlip,
				cyFlip,
				this.openScale,
				this.openPivotX,
				this.openPivotY
			);
		}
	}

	/** Smooth ease-in-out for flip timing (slow start / finish, faster mid). */
	private static float easeInOutCubic(float t) {
		t = Math.max(0F, Math.min(1F, t));
		return t < 0.5F
			? 4F * t * t * t
			: 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
	}

	/** @return mannequin box {@code [x0,y0,x1,y1]} if the model should be drawn, else null */
	private int[] drawProfileFace(
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
		ensureStaticLabels(font);
		int ty = y + PAD;
		NetworthBreakdown activeNw = activeNetworth();
		double nwTotal = activeNw.total();
		String nwValue;
		if (Double.doubleToLongBits(nwTotal) != Double.doubleToLongBits(this.cachedNwTotal)
			|| this.cachedNwValue.isEmpty()) {
			this.cachedNwTotal = nwTotal;
			if (nwTotal > 0) {
				nwValue = FormatUtil.shortCoins(nwTotal);
			} else if (activeNw.note() != null && activeNw.note().toLowerCase(java.util.Locale.ROOT).contains("loading")) {
				nwValue = "…";
			} else {
				nwValue = "-";
			}
			if (this.loadError != null && !this.loadError.isBlank() && nwTotal <= 0
				&& (activeNw.note() == null || !activeNw.note().toLowerCase(java.util.Locale.ROOT).contains("loading"))) {
				nwValue = "-";
			}
			this.cachedNwValue = nwValue;
			this.cachedNwValueW = PvDraw.widthBold(font, nwValue);
			this.cachedNwLine = Component.empty()
				.append(PvDraw.styled(NW_LABEL, 0xFF55FF55, false))
				.append(PvDraw.styled(nwValue, PvDraw.COLOR_GOLD, true));
		} else {
			nwValue = this.cachedNwValue;
			if (this.cachedNwLine == null) {
				this.cachedNwLine = Component.empty()
					.append(PvDraw.styled(NW_LABEL, 0xFF55FF55, false))
					.append(PvDraw.styled(nwValue, PvDraw.COLOR_GOLD, true));
			}
		}

		double weightTotal = this.weightSystem == WeightSystem.SENITHER ? this.senither.total() : this.lily.total();
		String weightValue;
		if (this.cachedWeightFormatSystem != this.weightSystem
			|| Double.doubleToLongBits(weightTotal) != Double.doubleToLongBits(this.cachedWeightTotal)
			|| this.cachedWeightValue.isEmpty()) {
			this.cachedWeightFormatSystem = this.weightSystem;
			this.cachedWeightTotal = weightTotal;
			weightValue = FormatUtil.weight(weightTotal);
			if (this.snapshot.weightText().equals("…") && (this.loadError == null || this.loadError.isBlank())) {
				weightValue = "…";
			}
			if (this.loadError != null && !this.loadError.isBlank() && this.senither.total() <= 0 && this.lily.total() <= 0) {
				weightValue = "-";
			}
			this.cachedWeightValue = weightValue;
			this.cachedWeightValueW = PvDraw.widthBold(font, weightValue);
			this.cachedWeightLine = Component.empty()
				.append(PvDraw.styled(WEIGHT_LABEL, 0xFF55FF55, false))
				.append(PvDraw.styled(weightValue, PvDraw.COLOR_GOLD, true));
		} else {
			weightValue = this.cachedWeightValue;
			if (this.cachedWeightLine == null) {
				this.cachedWeightLine = Component.empty()
					.append(PvDraw.styled(WEIGHT_LABEL, 0xFF55FF55, false))
					.append(PvDraw.styled(weightValue, PvDraw.COLOR_GOLD, true));
			}
		}

		int nwLineW = NW_LABEL_W + this.cachedNwValueW;
		int nwX = x + (w - nwLineW) / 2;
		g.text(font, this.cachedNwLine, nwX, ty, PvDraw.COLOR_WHITE, false);
		this.networthHitX = nwX;
		this.networthHitY = ty;
		this.networthHitW = nwLineW;
		this.networthHitH = font.lineHeight;

		ty += layout.line;
		int weightLineW = WEIGHT_LABEL_W + this.cachedWeightValueW;
		int weightX = x + (w - weightLineW) / 2;
		g.text(font, this.cachedWeightLine, weightX, ty, PvDraw.COLOR_WHITE, false);
		this.weightHitX = weightX;
		this.weightHitY = ty;
		this.weightHitW = weightLineW;
		this.weightHitH = font.lineHeight;

		// Spacer under Weight, then Bank (purse stays inside Networth breakdown).
		ty += layout.line * 2;
		String bankValue = FormatUtil.shortCoins(this.snapshot.bankCoins());
		Component bankLine = Component.empty()
			.append(PvDraw.styled(BANK_LABEL, 0xFF55FF55, false))
			.append(PvDraw.styled(bankValue, PvDraw.COLOR_GOLD, true));
		int bankW = BANK_LABEL_W + font.width(PvDraw.styled(bankValue, PvDraw.COLOR_GOLD, true));
		int bankX = x + (w - bankW) / 2;
		g.text(font, bankLine, bankX, ty, PvDraw.COLOR_WHITE, false);
		this.bankHitX = bankX;
		this.bankHitY = ty;
		this.bankHitW = bankW;
		this.bankHitH = font.lineHeight;

		int cx = x + w / 2;
		int boxW = Math.min(w - PAD * 2, Math.max(64, w - 20));
		int boxX = cx - boxW / 2;
		int boxTop = y + layout.boxTop;
		int boxH = layout.boxH;

		Component nameComp = styledPlayerName();
		int nameW = this.cachedNameWidth;
		if (nameW < 0) {
			nameW = font.width(nameComp);
			this.cachedNameWidth = nameW;
		}
		g.text(font, nameComp, cx - nameW / 2, y + layout.nameY, PvDraw.COLOR_WHITE, false);
		this.nameHitX = cx - nameW / 2;
		this.nameHitY = y + layout.nameY;
		this.nameHitW = nameW;
		this.nameHitH = font.lineHeight;

		PvDraw.fill(g, boxX, boxTop, boxW, boxH, 0xFF15151E);
		g.outline(boxX, boxTop, boxW, boxH, PvDraw.COLOR_BORDER);

		int[] modelBox = null;
		if (this.snapshot.playerUuid() != null) {
			this.modelBoxScratch[0] = boxX + 2;
			this.modelBoxScratch[1] = boxTop + 2;
			this.modelBoxScratch[2] = boxX + boxW - 2;
			this.modelBoxScratch[3] = boxTop + boxH - 2;
			modelBox = this.modelBoxScratch;
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
		int socialBarW = Math.min(boxW, Math.max(48, boxW - 8));
		int socialBarX = cx - socialBarW / 2;
		if (this.cachedSocialLine == null || this.cachedSocialBarW != socialBarW) {
			this.cachedSocialBarW = socialBarW;
			this.cachedSocialLine = PvDraw.paddedLabelValue(
				font, social.name(), String.valueOf(social.level()), socialBarW
			);
		}
		PvDraw.labeledBar(
			g, font,
			this.cachedSocialLine, social.progress(),
			socialBarX, socialLabelY, socialBarW,
			PvDraw.COLOR_BAR_FILL, social.maxed()
		);
		this.zones.add(new HoverZone(
			socialBarX,
			socialLabelY,
			socialBarW,
			font.lineHeight + BAR_LABEL_GAP + BAR_H + 2,
			social.hoverLines()
		));

		// Status stretched to social-bar width under the social skill.
		int footerY = y + layout.footerY;
		String statusLabel = this.playerStatus.buttonLabel();
		int statusColor = this.playerStatus.buttonColor(
			PvDraw.COLOR_ACCENT, ENABLED_GREEN, OFFLINE_RED, PvDraw.COLOR_MUTED);
		int statusW = socialBarW;
		int statusH = layout.statusH;
		int statusX = socialBarX;
		int statusY = footerY;
		this.statusHitX = statusX;
		this.statusHitY = statusY;
		this.statusHitW = statusW;
		this.statusHitH = statusH;
		boolean statusHover = mouseX >= statusX && mouseX < statusX + statusW
			&& mouseY >= statusY && mouseY < statusY + statusH;
		PvDraw.fill(g, statusX, statusY, statusW, statusH, 0xFF101018);
		g.outline(statusX, statusY, statusW, statusH,
			statusHover ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER);
		int statusMaxW = Math.max(8, statusW - 8);
		String statusKey = statusLabel + "|" + statusMaxW;
		if (!statusKey.equals(this.cachedStatusKey)) {
			this.cachedStatusKey = statusKey;
			this.cachedStatusDrawn = trimToWidth(font, statusLabel, statusMaxW);
		}
		PvDraw.textCentered(g, font, this.cachedStatusDrawn, statusX + statusW / 2,
			statusY + (statusH - font.lineHeight) / 2, statusColor);
		return modelBox;
	}

	private static void ensureStaticLabels(Font font) {
		if (NW_LABEL == null) {
			NW_LABEL = Component.translatable("betterpv.home.networth_label").getString();
			WEIGHT_LABEL = Component.translatable("betterpv.home.weight_label").getString();
			PURSE_LABEL = Component.translatable("betterpv.home.purse_label").getString();
			BANK_LABEL = Component.translatable("betterpv.home.bank_label").getString();
		}
		if (NW_LABEL_W < 0) {
			NW_LABEL_W = font.width(NW_LABEL);
			WEIGHT_LABEL_W = font.width(WEIGHT_LABEL);
			PURSE_LABEL_W = font.width(PURSE_LABEL);
			BANK_LABEL_W = font.width(BANK_LABEL);
		}
	}

	/** Scroll username history tip while hovering the name. */
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (!showingLeftStatsFace() && hitName(mouseX, mouseY)
			&& this.usernameHistory.state() == UsernameHistory.State.READY
			&& this.historyMaxScroll > 0) {
			int step = 12;
			int delta = scrollY > 0 ? -step : step;
			int next = Math.max(0, Math.min(this.historyMaxScroll, this.historyScroll + delta));
			if (next != this.historyScroll) {
				this.historyScroll = next;
				return true;
			}
		}
		return false;
	}

	private void drawUsernameHistoryTooltip(
		GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH
	) {
		List<PvTooltip.Line> lines = usernameHistoryTooltip();
		if (lines.isEmpty()) {
			return;
		}
		int lineH = font.lineHeight + 3;
		int maxBodyH = Math.max(lineH * 4, (int) (screenH * 0.65) - 16 - lineH);
		int[] maxScroll = new int[1];
		PvTooltip.drawStyled(
			g, font, lines, mouseX, mouseY, screenW, screenH,
			this.historyScroll, maxBodyH, maxScroll
		);
		this.historyMaxScroll = maxScroll[0];
		this.historyScroll = Math.min(this.historyScroll, this.historyMaxScroll);
	}

	private List<PvTooltip.Line> usernameHistoryTooltip() {
		List<PvTooltip.Line> tip = new ArrayList<>();
		switch (this.usernameHistory.state()) {
			case IDLE -> {
				tip.add(PvTooltip.Line.title("Username history", PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.divider());
				tip.add(PvTooltip.Line.action("Click to load username history"));
			}
			case LOADING -> {
				tip.add(PvTooltip.Line.title("Username history", PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.divider());
				tip.add(PvTooltip.Line.meta("Loading…"));
			}
			case ERROR -> {
				tip.add(PvTooltip.Line.title("Username history", PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.divider());
				tip.add(PvTooltip.Line.of(
					this.usernameHistory.error().isBlank() ? "Unavailable" : this.usernameHistory.error(),
					OFFLINE_RED));
			}
			case READY -> {
				List<UsernameHistory.Entry> entries = this.usernameHistory.entries();
				tip.add(PvTooltip.Line.title(
					"Username history (" + entries.size() + ")", PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.divider());
				if (entries.isEmpty()) {
					tip.add(PvTooltip.Line.meta("No history"));
				} else {
					// Newest first.
					for (int i = entries.size() - 1; i >= 0; i--) {
						UsernameHistory.Entry entry = entries.get(i);
						boolean current = i == entries.size() - 1;
						boolean original = i == 0;
						List<PvTooltip.Span> nameSpans = List.of(
							current
								? PvTooltip.Span.bold(entry.username(), PvDraw.COLOR_ACCENT)
								: PvTooltip.Span.of(entry.username(), PvDraw.COLOR_TEXT)
						);
						List<PvTooltip.Span> dateSpans;
						if (!entry.changedAt().isBlank()) {
							dateSpans = List.of(PvTooltip.Span.of(shortDate(entry.changedAt()), PvDraw.COLOR_MUTED));
						} else if (original) {
							dateSpans = List.of(PvTooltip.Span.bold("original", PvDraw.COLOR_ACCENT));
						} else {
							dateSpans = List.of();
						}
						tip.add(PvTooltip.Line.row(nameSpans, dateSpans));
					}
				}
			}
		}
		return tip;
	}

	private List<PvTooltip.Line> bankTooltip() {
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(PvTooltip.Line.title("Bank", PvDraw.COLOR_TEXT));
		lines.add(PvTooltip.Line.divider());
		lines.add(PvTooltip.Line.row(
			"Balance", PvDraw.COLOR_MUTED,
			FormatUtil.shortCoins(this.snapshot.bankCoins()), PvDraw.COLOR_GOLD
		));
		List<ProfileSnapshot.BankTransaction> txs = this.snapshot.bankTransactions();
		if (txs == null || txs.isEmpty()) {
			lines.add(PvTooltip.Line.meta("No recent transactions (API off or empty)"));
			return lines;
		}
		lines.add(PvTooltip.Line.blank());
		lines.add(PvTooltip.Line.of("Recent", PvDraw.COLOR_MUTED));
		int shown = 0;
		for (ProfileSnapshot.BankTransaction tx : txs) {
			if (shown >= 6) {
				break;
			}
			String action = formatBankAction(tx.action());
			String amount = FormatUtil.shortCoins(tx.amount());
			String who = tx.initiatorName().isBlank() ? "" : tx.initiatorName();
			String when = tx.timestampMs() > 0L ? FormatUtil.ago(tx.timestampMs()) : "";
			String left = action + " " + amount;
			String right = when;
			if (!who.isBlank() && !when.isBlank()) {
				right = who + " · " + when;
			} else if (!who.isBlank()) {
				right = who;
			}
			lines.add(PvTooltip.Line.row(left, PvDraw.COLOR_TEXT, right, PvDraw.COLOR_MUTED));
			shown++;
		}
		return lines;
	}

	private static String formatBankAction(String action) {
		if (action == null || action.isBlank()) {
			return "Txn";
		}
		return switch (action.trim().toUpperCase(java.util.Locale.ROOT)) {
			case "DEPOSIT" -> "Deposit";
			case "WITHDRAW" -> "Withdraw";
			default -> {
				String lower = action.trim().toLowerCase(java.util.Locale.ROOT);
				yield Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
			}
		};
	}

	private List<PvTooltip.Line> statusTooltip() {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.of("Hypixel status", PvDraw.COLOR_TEXT));
		switch (this.playerStatus.state()) {
			case IDLE -> tip.add(PvTooltip.Line.of("Click to check online status", PvDraw.COLOR_MUTED));
			case LOADING -> tip.add(PvTooltip.Line.of("Loading…", PvDraw.COLOR_MUTED));
			case ONLINE -> {
				tip.add(PvTooltip.Line.of(this.playerStatus.buttonLabel(), ENABLED_GREEN));
				if (!this.playerStatus.gameType().isBlank()) {
					tip.add(PvTooltip.Line.of(
						PlayerStatus.prettyLocation(this.playerStatus.gameType()),
						PvDraw.COLOR_MUTED));
				}
			}
			case OFFLINE -> tip.add(PvTooltip.Line.of("Offline", OFFLINE_RED));
			case ERROR -> tip.add(PvTooltip.Line.of(
				this.playerStatus.error().isBlank() ? "Unavailable" : this.playerStatus.error(),
				OFFLINE_RED));
		}
		return tip;
	}

	private static String shortDate(String iso) {
		if (iso == null || iso.isBlank()) {
			return "";
		}
		// Ashcon: 2015-04-01T00:00:00.000Z → 2015-04-01
		int t = iso.indexOf('T');
		return t > 0 ? iso.substring(0, t) : iso;
	}

	private static final int ENABLED_GREEN = 0xFF55FF55;
	private static final int OFFLINE_RED = 0xFFFF5555;

	private static String trimToWidth(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int ellipsisW = font.width(ellipsis);
		if (maxW <= ellipsisW) {
			return ellipsis;
		}
		int lo = 0;
		int hi = text.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (font.width(text.substring(0, mid)) + ellipsisW <= maxW) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		return text.substring(0, lo) + ellipsis;
	}

	private Component cachedStyledName;
	private String cachedStyledNameKey = "";
	private int cachedNameWidth = -1;
	private String cachedNwValue = "";
	private String cachedWeightValue = "";
	private int cachedNwValueW;
	private int cachedWeightValueW;
	private double cachedNwTotal = Double.NaN;
	private double cachedWeightTotal = Double.NaN;
	private WeightSystem cachedWeightFormatSystem;
	private String cachedStatusKey = "";
	private String cachedStatusDrawn = "";
	private List<CachedBar> cachedSkillBars = List.of();
	private List<CachedBar> cachedSlayerBars = List.of();
	private int cachedBarColW = -1;

	private record CachedBar(Component line, float progress, boolean maxed, int fillColor, List<PvTooltip.Line> hover) {
	}

	private static String NW_LABEL;
	private static String WEIGHT_LABEL;
	private static String PURSE_LABEL;
	private static String BANK_LABEL;
	private static int NW_LABEL_W = -1;
	private static int WEIGHT_LABEL_W = -1;
	private static int PURSE_LABEL_W = -1;
	private static int BANK_LABEL_W = -1;

	private Component styledPlayerName() {
		String name = this.snapshot.playerName();
		UUID uuid = this.snapshot.playerUuid();
		String rankKey = this.playerRank == null ? "" : Integer.toHexString(System.identityHashCode(this.playerRank));
		String key = (uuid == null ? "" : uuid.toString()) + "|" + (name == null ? "" : name) + "|" + rankKey;
		if (key.equals(this.cachedStyledNameKey) && this.cachedStyledName != null) {
			return this.cachedStyledName;
		}
		Component base = Component.literal(name == null ? "" : name);
		Component namePart;
		if (uuid != null && NameStyler.hasDisplayProfile(new GameProfile(uuid, name == null ? "" : name))) {
			GameProfile profile = new GameProfile(uuid, name == null ? "" : name);
			namePart = BetterPvCosmetics.styleDisplayName(base, profile);
		} else if (uuid == null) {
			namePart = NameStyler.applyGradientToName(base);
		} else {
			GameProfile profile = new GameProfile(uuid, name == null ? "" : name);
			namePart = BetterPvCosmetics.styleDisplayName(base, profile);
			if (namePart == base) {
				namePart = NameStyler.applyGradientToName(base);
			}
		}
		MutableComponent styled = Component.empty();
		List<PvTooltip.Span> prefix = HypixelRanks.prefixSpans(this.playerRank);
		boolean nameAlreadyHasBracket = namePart.getString().startsWith("[");
		if (!prefix.isEmpty() && !nameAlreadyHasBracket) {
			for (PvTooltip.Span span : prefix) {
				styled.append(span.toComponent());
			}
		}
		if (prefix.isEmpty() && this.playerRank != null && !nameAlreadyHasBracket) {
			// No package rank — still use grey name from HypixelRanks when cosmetics did nothing special.
			styled.append(namePart);
		} else {
			styled.append(namePart);
		}
		this.cachedStyledNameKey = key;
		this.cachedStyledName = styled;
		this.cachedNameWidth = -1;
		return styled;
	}

	private static Component spansToComponent(List<PvTooltip.Span> spans) {
		MutableComponent out = Component.empty();
		if (spans == null) {
			return out;
		}
		for (PvTooltip.Span span : spans) {
			out.append(span.toComponent());
		}
		return out;
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
		List<PvTooltip.Line> xpHover = List.of(
			PvTooltip.Line.of("SkyBlock Level " + level, levelColor),
			PvTooltip.Line.of(xp + "/100 (" + Math.round(pct) + "%)", PvDraw.COLOR_GOLD)
		);
		this.zones.add(new HoverZone(barX, ty - font.lineHeight - 2, barW, font.lineHeight + 2 + BAR_H + 2, xpHover));
	}

	private void drawStatsFace(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		List<PlayerStatsSnapshot.Entry> entries = this.playerStats.entries();
		if (entries.isEmpty()) {
			PvDraw.textCentered(g, font, "No stats", x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}
		int cols = 2;
		int colGap = 8;
		int colW = (w - PAD * 2 - colGap) / cols;
		int symbolSlot = 0;
		for (PlayerStatsSnapshot.Entry entry : entries) {
			symbolSlot = Math.max(symbolSlot, font.width(SkyBlockStats.stat(entry.id()).symbol()));
		}
		symbolSlot = Math.max(symbolSlot, font.width("⚔"));
		int labelGap = 3;
		int rows = (entries.size() + cols - 1) / cols;
		int rowH = font.lineHeight + 1;
		int contentH = rows * rowH;
		int availH = h - PAD * 2;
		if (rows > 0 && contentH < availH) {
			rowH = Math.max(rowH, availH / rows);
			contentH = rows * rowH;
		}
		int ty = y + Math.max(PAD, (h - contentH) / 2);
		for (int i = 0; i < entries.size(); i++) {
			PlayerStatsSnapshot.Entry entry = entries.get(i);
			int col = i % cols;
			int row = i / cols;
			int bx = x + PAD + col * (colW + colGap);
			int by = ty + row * rowH;
			SkyBlockStats.StatStyle style = SkyBlockStats.stat(entry.id());
			String symbol = style.symbol();
			int symW = font.width(symbol);
			PvDraw.text(g, font, symbol, bx + (symbolSlot - symW) / 2, by, style.color());
			PvDraw.text(g, font, entry.label(), bx + symbolSlot + labelGap, by, style.color());
			String value = entry.present() ? formatStat(entry.value().getAsDouble()) : "-";
			PvDraw.textRight(g, font, value, bx + colW, by, PvDraw.COLOR_WHITE);
		}
	}

	/** Compact rounded stats: {@code 3908.7 → 3.9k}, {@code 469.5 → 470}, {@code 0.0 → 0}. */
	private static String formatStat(double value) {
		double abs = Math.abs(value);
		if (abs >= 1_000_000_000L) {
			return formatCompact(value / 1_000_000_000L, "b");
		}
		if (abs >= 1_000_000L) {
			return formatCompact(value / 1_000_000L, "m");
		}
		if (abs >= 1_000L) {
			return formatCompact(value / 1_000L, "k");
		}
		return String.valueOf(Math.round(value));
	}

	private static String formatCompact(double scaled, String suffix) {
		double one = Math.round(scaled * 10.0) / 10.0;
		if (Math.abs(one - Math.rint(one)) < 0.05) {
			return ((long) Math.rint(one)) + suffix;
		}
		return String.format(java.util.Locale.US, "%.1f%s", one, suffix);
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

	private void ensureBarCaches(Font font, int colW) {
		if (colW == this.cachedBarColW && !this.cachedSkillBars.isEmpty()) {
			return;
		}
		this.cachedBarColW = colW;
		List<ProfileSnapshot.SkillEntry> skills = this.snapshot.skills();
		int skillLimit = Math.min(skills.size(), SKILL_ROWS * 2);
		List<CachedBar> skillBars = new ArrayList<>(skillLimit);
		for (int i = 0; i < skillLimit; i++) {
			ProfileSnapshot.SkillEntry skill = skills.get(i);
			skillBars.add(new CachedBar(
				PvDraw.paddedLabelValue(font, skill.name(), String.valueOf(skill.level()), colW),
				skill.progress(),
				skill.maxed(),
				PvDraw.COLOR_BAR_FILL,
				skill.hoverLines()
			));
		}
		this.cachedSkillBars = skillBars;

		List<ProfileSnapshot.SlayerEntry> slayers = this.snapshot.slayers();
		int slayerLimit = Math.min(slayers.size(), SLAYER_ROWS * 2);
		List<CachedBar> slayerBars = new ArrayList<>(slayerLimit);
		for (int i = 0; i < slayerLimit; i++) {
			ProfileSnapshot.SlayerEntry slayer = slayers.get(i);
			slayerBars.add(new CachedBar(
				PvDraw.paddedLabelValue(font, slayer.name(), "T" + slayer.tier(), colW),
				slayer.progress(),
				slayer.maxed(),
				PvDraw.COLOR_BAR_FILL_SLAYER,
				slayer.hoverLines()
			));
		}
		this.cachedSlayerBars = slayerBars;
	}

	private void drawSkillGrid(GuiGraphicsExtractor g, Font font, int leftX, int rightX, int startY, int colW, int rowH) {
		ensureBarCaches(font, colW);
		List<CachedBar> bars = this.cachedSkillBars;
		for (int i = 0; i < bars.size(); i++) {
			CachedBar bar = bars.get(i);
			boolean left = (i % 2) == 0;
			int row = i / 2;
			int bx = left ? leftX : rightX;
			int by = startY + row * rowH;
			PvDraw.labeledBar(g, font, bar.line(), bar.progress(), bx, by, colW, bar.fillColor(), bar.maxed());
			this.zones.add(new HoverZone(bx, by, colW, rowH - 2, bar.hover()));
		}
	}

	private void drawSlayerGrid(GuiGraphicsExtractor g, Font font, int leftX, int rightX, int startY, int colW, int rowH) {
		ensureBarCaches(font, colW);
		List<CachedBar> bars = this.cachedSlayerBars;
		for (int i = 0; i < bars.size(); i++) {
			CachedBar bar = bars.get(i);
			boolean left = (i % 2) == 0;
			int row = i / 2;
			int bx = left ? leftX : rightX;
			int by = startY + row * rowH;
			PvDraw.labeledBar(g, font, bar.line(), bar.progress(), bx, by, colW, bar.fillColor(), bar.maxed());
			this.zones.add(new HoverZone(bx, by, colW, rowH - 2, bar.hover()));
		}
	}

	private record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
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
		int statusH;
		int nameLineH;
		int nameGap;
		int boxToSocialGap;
		int socialToFooterGap;
		int footerH;
		int footerY;
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
