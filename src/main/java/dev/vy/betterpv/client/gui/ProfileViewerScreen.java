package dev.vy.betterpv.client.gui;

import dev.vy.betterpv.client.api.EliteBotApiClient;
import dev.vy.betterpv.client.api.HypixelApiClient;
import dev.vy.betterpv.client.api.ProfileFetcher;
import dev.vy.betterpv.client.data.GardenSnapshot;
import dev.vy.betterpv.client.data.PlayerStatsSnapshot;
import dev.vy.betterpv.client.data.ProfileSnapshot;
import dev.vy.betterpv.client.gui.auctions.AuctionPage;
import dev.vy.betterpv.client.gui.collections.CollectionsPage;
import dev.vy.betterpv.client.gui.dungeons.DungeonPage;
import dev.vy.betterpv.client.gui.garden.GardenPage;
import dev.vy.betterpv.client.gui.home.HomePage;
import dev.vy.betterpv.client.gui.inventories.InventoryPage;
import dev.vy.betterpv.client.gui.mining.MiningPage;
import dev.vy.betterpv.client.gui.nav.IconButtonBar;
import dev.vy.betterpv.client.gui.nav.InventoryPane;
import dev.vy.betterpv.client.gui.nav.MuseumSort;
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import dev.vy.betterpv.client.gui.nav.PvTab;
import dev.vy.betterpv.client.gui.pets.PetsPage;
import dev.vy.betterpv.BetterPV;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class ProfileViewerScreen extends Screen {
	private static final int PAD = 8;
	private static final int SEARCH_H = 16;
	private static final int SEARCH_GAP = 4;
	/** Open expand animation duration (ms). */
	private static final long OPEN_ANIM_MS = 260L;
	private static final float OPEN_SCALE_START = 0.12F;

	private final String requestedName;
	private final HomePage homePage;
	private final DungeonPage dungeonPage = new DungeonPage();
	private final InventoryPage inventoryPage = new InventoryPage();
	private final PetsPage petsPage = new PetsPage();
	private final AuctionPage auctionPage = new AuctionPage();
	private final CollectionsPage collectionsPage = new CollectionsPage();
	private final GardenPage gardenPage = new GardenPage();
	private final MiningPage miningPage = new MiningPage();
	private final IconButtonBar topBar = new IconButtonBar();
	private final IconButtonBar sideBar = new IconButtonBar();
	private final IconButtonBar inventoryBar = new IconButtonBar();
	private final Map<PvTab, PvSubTab> subSelection = new EnumMap<>(PvTab.class);
	private final long openAnimStartMs = System.currentTimeMillis();
	private float openPivotX;
	private float openPivotY;

	private PvTab tab = PvTab.HOME;
	private MuseumSort museumSort = MuseumSort.COMBAT;
	private boolean fetchStarted;
	private boolean dataReady;
	private boolean breakScheduled;
	private String profileId;
	private UUID playerUuid;
	private boolean gardenFetchStarted;
	private boolean gardenContestsFetchStarted;
	private boolean gardenWeightFetchStarted;
	private EditBox inventorySearch;
	private String inventorySearchQuery = "";
	private Component inventoryPaneTip;
	private int inventoryPaneTipX;
	private int inventoryPaneTipY;

	public ProfileViewerScreen(String playerName) {
		super(Component.translatable("betterpv.screen.title"));
		this.requestedName = playerName == null || playerName.isBlank() ? "?" : playerName.trim();
		this.homePage = new HomePage(ProfileSnapshot.loading(this.requestedName));
		for (PvTab t : PvTab.values()) {
			PvSubTab[] subs = t.subTabs();
			if (subs.length > 0) {
				this.subSelection.put(t, subs[0]);
			}
		}
	}

	@Override
	protected void init() {
		super.init();
		this.inventorySearch = new EditBox(
			this.font,
			0,
			0,
			100,
			SEARCH_H,
			Component.translatable("betterpv.inv.search")
		);
		this.inventorySearch.setMaxLength(64);
		this.inventorySearch.setBordered(false);
		// EditBox colours are ARGB - stripping alpha makes typed text invisible.
		this.inventorySearch.setTextColor(PvDraw.COLOR_TEXT);
		this.inventorySearch.setTextColorUneditable(PvDraw.COLOR_MUTED);
		this.inventorySearch.setValue(this.inventorySearchQuery);
		this.inventorySearch.setResponder(value -> {
			this.inventorySearchQuery = value;
			this.inventoryPage.setSearchQuery(value);
		});
		this.inventorySearch.setVisible(false);
		this.addWidget(this.inventorySearch);

		if (this.fetchStarted) {
			return;
		}
		this.fetchStarted = true;
		ProfileFetcher.fetch(this.requestedName, updated -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.screen != this) {
					return;
				}
				applyLoadedProfile(updated);
			});
		}).whenComplete((loaded, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.screen != this) {
					return;
				}
				if (error != null || loaded == null || !loaded.ok()) {
					BetterPV.LOGGER.warn(
						"Profile fetch failed for {} — staying on loading easter egg",
						this.requestedName,
						error
					);
					// Do not set dataReady; LoadingEgg escalates until finale or the user closes.
					return;
				}
				applyLoadedProfile(loaded);
				this.dataReady = true;
			});
		});
	}

	private void applyLoadedProfile(ProfileFetcher.LoadedProfile loaded) {
		if (loaded == null) {
			return;
		}
		this.homePage.applyLoaded(
			loaded.snapshot(),
			loaded.senither(),
			loaded.lily(),
			loaded.networthNormal(),
			loaded.networthNonCosmetic(),
			loaded.networthUnsoulbound(),
			loaded.networthUnsoulboundNonCosmetic(),
			loaded.armor(),
			loaded.playerStats(),
			loaded.error()
		);
		this.dungeonPage.apply(loaded.dungeons());
		this.inventoryPage.apply(loaded.inventories());
		this.petsPage.apply(loaded.pets());
		this.auctionPage.apply(loaded.auctions());
		this.collectionsPage.apply(loaded.collections());
		this.gardenPage.apply(loaded.garden());
		this.miningPage.apply(loaded.mining());
		this.profileId = loaded.profileId();
		this.playerUuid = loaded.snapshot() == null ? null : loaded.snapshot().playerUuid();
		this.gardenFetchStarted = false;
		this.gardenContestsFetchStarted = false;
		this.gardenWeightFetchStarted = false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		this.topBar.clearHits();
		this.sideBar.clearHits();
		this.inventoryBar.clearHits();

		// Always reserve tab gutters so the frame never jumps between tabs.
		int topRoom = IconButtonBar.TAB - IconButtonBar.SEAM + 4;
		int leftRoom = IconButtonBar.TAB - IconButtonBar.SEAM + 4;

		int panelW = Math.min(520, Math.max(420, this.width - 80 - leftRoom));
		// One height for every tab: fit Home content tightly (no empty border), never retarget per-tab.
		int contentH = this.homePage.preferredHeight(this.font, panelW - PAD * 2);
		int panelH = Math.min(this.height - topRoom - 24, Math.max(200, contentH + PAD * 2));

		int panelX = (this.width - panelW) / 2 + leftRoom / 2;
		int panelY = (this.height - panelH - topRoom) / 2 + topRoom;

		PvDraw.fill(graphics, 0, 0, this.width, this.height, 0x99000000);

		float scale = openScale();
		this.openPivotX = panelX + panelW / 2.0F;
		this.openPivotY = panelY + panelH / 2.0F;
		boolean scaled = scale < 0.999F;
		if (scaled) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(this.openPivotX, this.openPivotY);
			graphics.pose().scale(scale, scale);
			graphics.pose().translate(-this.openPivotX, -this.openPivotY);
		}

		PvDraw.panel(graphics, panelX, panelY, panelW, panelH);

		if (!this.dataReady) {
			hideInventorySearch();
			drawLoadingFace(graphics, panelX, panelY, panelW, panelH);
		} else {
			List<IconButtonBar.Entry> topEntries = new ArrayList<>();
			for (PvTab t : PvTab.values()) {
				topEntries.add(new IconButtonBar.Entry(t, t.icon(), t.label(), () -> {
					if (t != PvTab.DUNGEONS) {
						this.dungeonPage.blurField();
					}
					this.tab = t;
				}));
			}
			this.topBar.drawTopFrameTabs(graphics, this.font, panelX, panelY, mouseX, mouseY, topEntries, this.tab);

			Object[] left = this.tab.leftTabs();
			if (left.length > 0) {
				List<IconButtonBar.Entry> side = new ArrayList<>();
				Object selectedLeft = selectedLeftKey();
				for (Object entry : left) {
					ItemStack icon;
					Component label;
					Runnable click;
					if (entry instanceof PvSubTab sub) {
						icon = sub.icon();
						label = sub.label();
						click = () -> this.subSelection.put(this.tab, sub);
						side.add(new IconButtonBar.Entry(entry, icon, label, click, sub.textureIcon(), sub.textureSize()));
					} else if (entry instanceof MuseumSort sort) {
						icon = sort.icon();
						label = sort.label();
						click = () -> this.museumSort = sort;
						side.add(new IconButtonBar.Entry(entry, icon, label, click));
					} else {
						continue;
					}
				}
				this.sideBar.drawLeftFrameTabs(graphics, this.font, panelX, panelY, mouseX, mouseY, side, selectedLeft);
			}

			// Content uses the full panel - nothing reserved inside for subtabs.
			renderBody(graphics, panelX + PAD, panelY + PAD, panelW - PAD * 2, panelH - PAD * 2, mouseX, mouseY, delta);

			if (this.tab == PvTab.DUNGEONS) {
				this.dungeonPage.renderOverlay(graphics, this.font, this.width, this.height, mouseX, mouseY);
			}

			// Inventory tips last so they paint above pane buttons / frame tabs.
			if (this.tab.isInventorySplit()) {
				this.inventoryPage.renderTooltip(graphics, this.font, mouseX, mouseY, this.width, this.height);
				if (this.inventoryPaneTip != null) {
					this.inventoryBar.maybeTooltip(
						graphics,
						this.font,
						this.inventoryPaneTip,
						true,
						this.inventoryPaneTipX,
						this.inventoryPaneTipY
					);
				}
			}
		}

		if (scaled) {
			graphics.pose().popMatrix();
		}
	}

	private float openScale() {
		long elapsed = System.currentTimeMillis() - this.openAnimStartMs;
		if (elapsed >= OPEN_ANIM_MS) {
			return 1.0F;
		}
		float t = Math.max(0.0F, Math.min(1.0F, elapsed / (float) OPEN_ANIM_MS));
		// Cubic ease-out: snappy expand from center.
		float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
		return OPEN_SCALE_START + (1.0F - OPEN_SCALE_START) * eased;
	}

	private boolean uiInteractive() {
		return this.dataReady && openScale() >= 0.98F;
	}

	private void drawLoadingFace(GuiGraphicsExtractor g, int panelX, int panelY, int panelW, int panelH) {
		long elapsedMs = System.currentTimeMillis() - this.openAnimStartMs;
		List<LoadingEgg.Stage> stages = LoadingEgg.stagesUnlocked(elapsedMs);
		int cx = panelX + panelW / 2;
		int maxW = panelW - 24;
		int lineH = this.font.lineHeight + 3;

		long phase = (System.currentTimeMillis() / 400L) % 4L;
		String dots = switch ((int) phase) {
			case 1 -> ".";
			case 2 -> "..";
			case 3 -> "...";
			default -> "";
		};
		String loading = "Loading Data" + dots;

		if (elapsedMs >= LoadingEgg.BREAK_AT_MS) {
			scheduleBreakFinale();
			PvDraw.fill(g, panelX + 2, panelY + 2, panelW - 4, panelH - 4, 0xCC330000);
		} else if (!stages.isEmpty() && stages.get(stages.size() - 1).shout()) {
			PvDraw.fill(g, panelX + 4, panelY + 4, panelW - 8, panelH - 8, 0x33AA0000);
		}

		// Loading Data + every unlocked threat, stacked oldest → newest.
		int rows = 1 + stages.size();
		int blockH = rows * lineH - 3;
		int topY = panelY + Math.max(10, (panelH - blockH) / 2);
		PvDraw.textCentered(g, this.font, loading, cx, topY, PvDraw.COLOR_MUTED);
		int ty = topY + lineH;
		for (LoadingEgg.Stage stage : stages) {
			drawEggLine(g, stage.line(), cx, ty, maxW);
			ty += lineH;
		}
	}

	private void drawEggLine(GuiGraphicsExtractor g, Component line, int cx, int y, int maxW) {
		int tw = this.font.width(line);
		if (tw <= maxW || tw <= 0) {
			PvDraw.textCentered(g, this.font, line, cx, y);
			return;
		}
		float scale = maxW / (float) tw;
		g.pose().pushMatrix();
		g.pose().translate(cx, y);
		g.pose().scale(scale, scale);
		PvDraw.text(g, this.font, line, -tw / 2, 0);
		g.pose().popMatrix();
	}

	private void scheduleBreakFinale() {
		if (this.breakScheduled) {
			return;
		}
		this.breakScheduled = true;
		BetterPV.LOGGER.warn("Loading egg hit finale for {} — starting limbo + fake ban", this.requestedName);
		LoadingEggFinale.start();
	}

	private Object selectedLeftKey() {
		if (this.tab == PvTab.MUSEUM) {
			return this.museumSort;
		}
		return this.subSelection.get(this.tab);
	}

	private void renderBody(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, float delta) {
		if (this.tab == PvTab.HOME) {
			hideInventorySearch();
			this.dungeonPage.blurField();
			this.homePage.render(
				g, this.font, x, y, w, h, mouseX, mouseY, this.width, this.height,
				openScale(), this.openPivotX, this.openPivotY
			);
			return;
		}

		if (this.tab == PvTab.DUNGEONS) {
			hideInventorySearch();
			this.dungeonPage.render(g, this.font, x, y, w, h, mouseX, mouseY, this.width, this.height);
			return;
		}

		if (this.tab.isInventorySplit()) {
			this.dungeonPage.blurField();
			renderInventorySplit(g, x, y, w, h, mouseX, mouseY, delta);
			return;
		}

		if (this.tab == PvTab.PETS) {
			hideInventorySearch();
			this.dungeonPage.blurField();
			this.petsPage.render(g, this.font, x, y, w, h, mouseX, mouseY, this.width, this.height);
			return;
		}

		if (this.tab == PvTab.AUCTIONS) {
			hideInventorySearch();
			this.dungeonPage.blurField();
			PvSubTab sub = this.subSelection.getOrDefault(this.tab, PvSubTab.AUCTION_STATS);
			this.auctionPage.render(g, this.font, sub, x, y, w, h, mouseX, mouseY, this.width, this.height);
			return;
		}

		if (this.tab == PvTab.COLLECTIONS) {
			hideInventorySearch();
			this.dungeonPage.blurField();
			PvSubTab sub = this.subSelection.getOrDefault(this.tab, PvSubTab.COLLECTIONS_LIST);
			this.collectionsPage.render(g, this.font, sub, x, y, w, h, mouseX, mouseY, this.width, this.height);
			return;
		}

		if (this.tab == PvTab.GARDEN) {
			hideInventorySearch();
			this.dungeonPage.blurField();
			ensureGardenIsland();
			ensureGardenWeight();
			PvSubTab sub = this.subSelection.getOrDefault(this.tab, PvSubTab.GARDEN_OVERVIEW);
			if (sub == PvSubTab.GARDEN_JACOB) {
				ensureGardenContests();
			}
			this.gardenPage.render(g, this.font, sub, x, y, w, h, mouseX, mouseY, this.width, this.height);
			return;
		}

		if (this.tab == PvTab.MINING) {
			hideInventorySearch();
			this.dungeonPage.blurField();
			PvSubTab sub = this.subSelection.getOrDefault(this.tab, PvSubTab.MINING_OVERVIEW);
			this.miningPage.render(g, this.font, sub, x, y, w, h, mouseX, mouseY, this.width, this.height);
			return;
		}

		hideInventorySearch();
		this.dungeonPage.blurField();
		PvDraw.innerPanel(g, x, y, w, h);
		String label = this.tab.label().getString();
		PvSubTab sub = this.subSelection.get(this.tab);
		if (sub != null && this.tab.hasSubTabs()) {
			label = label + " / " + sub.label().getString();
		}
		if (this.tab == PvTab.MUSEUM) {
			label = label + " / " + this.museumSort.label().getString();
		}
		PvDraw.textCentered(g, this.font, label, x + w / 2, y + h / 2 - this.font.lineHeight / 2, PvDraw.COLOR_MUTED);
	}

	private void renderInventorySplit(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, float delta) {
		// Top strip is decorative only - empty header panel, not a search field.
		PvDraw.fill(g, x, y, w, SEARCH_H, 0xFF101018);
		g.outline(x, y, w, SEARCH_H, PvDraw.COLOR_BORDER);

		int bodyY = y + SEARCH_H + SEARCH_GAP;
		int bodyH = h - SEARCH_H - SEARCH_GAP;

		InventoryPane[] panes = InventoryPane.values();
		// Row widths top→bottom: 5, 5, 3 (13 panes).
		int[] rowWidths = {5, 5, 3};
		int btn = 22;
		int btnGap = 14;
		int pad = 10;
		int rows = rowWidths.length;
		int maxCols = 0;
		for (int width : rowWidths) {
			maxCols = Math.max(maxCols, width);
		}
		int gridW = maxCols * btn + (maxCols - 1) * btnGap;
		int gridH = rows * btn + (rows - 1) * btnGap;

		int gap = 8;
		int rightW = gridW + pad * 2;
		int leftW = w - rightW - gap;
		if (leftW < 150) {
			leftW = Math.max(130, (int) (w * 0.52));
			rightW = w - leftW - gap;
		}
		int rightX = x + leftW + gap;

		PvDraw.innerPanel(g, x, bodyY, leftW, bodyH);
		this.inventoryPage.setSearchQuery(this.inventorySearchQuery);
		this.inventoryPage.render(g, this.font, x, bodyY, leftW, bodyH, mouseX, mouseY, this.width, this.height);

		PvDraw.innerPanel(g, rightX, bodyY, rightW, bodyH);

		int startX = rightX + (rightW - gridW) / 2;
		// Buttons pinned near the top of the right panel.
		int startY = bodyY + pad;

		int index = 0;
		this.inventoryPaneTip = null;
		for (int row = 0; row < rows && index < panes.length; row++) {
			int width = rowWidths[row];
			int rowW = width * btn + (width - 1) * btnGap;
			int rowStartX = rightX + (rightW - rowW) / 2;
			for (int col = 0; col < width && index < panes.length; col++) {
				InventoryPane pane = panes[index++];
				int bx = rowStartX + col * (btn + btnGap);
				int by = startY + row * (btn + btnGap);
				boolean selected = pane == this.inventoryPage.pane();
				boolean hovered = mouseX >= bx && mouseX < bx + btn && mouseY >= by && mouseY < by + btn;
				boolean searchHit = this.inventoryPage.searchHighlightsPane(pane);
				int bg = selected ? 0xFF2A3A55 : hovered ? 0xFF222230 : 0xFF16161E;
				int border = searchHit ? 0xFF55FF55 : selected ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;
				PvDraw.fill(g, bx, by, btn, btn, bg);
				g.outline(bx, by, btn, btn, border);
				Identifier textureIcon = pane.textureIcon();
				if (textureIcon != null) {
					int ix = bx + (btn - 16) / 2;
					int iy = by + (btn - 16) / 2;
					g.blit(
						RenderPipelines.GUI_TEXTURED,
						textureIcon,
						ix, iy,
						0, 0,
						16, 16,
						16, 16,
						16, 16
					);
				} else {
					g.item(pane.icon(), bx + (btn - 16) / 2, by + (btn - 16) / 2);
				}
				this.inventoryBar.addHit(bx, by, btn, btn, () -> this.inventoryPage.setPane(pane));
				if (hovered) {
					// Defer tip until after all buttons / search so later icons don't cover it.
					this.inventoryPaneTip = pane.label();
					this.inventoryPaneTipX = bx - 4;
					this.inventoryPaneTipY = by + btn / 2;
				}
			}
		}

		// Real search field near the bottom of the right panel.
		int searchBoxY = bodyY + bodyH - pad - SEARCH_H;
		int searchBoxX = rightX + pad;
		int searchBoxW = rightW - pad * 2;
		PvDraw.fill(g, searchBoxX, searchBoxY, searchBoxW, SEARCH_H, 0xFF101018);
		g.outline(
			searchBoxX,
			searchBoxY,
			searchBoxW,
			SEARCH_H,
			this.inventorySearch != null && this.inventorySearch.isFocused() ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER
		);
		layoutInventorySearch(searchBoxX, searchBoxY, searchBoxW);
		this.inventorySearch.extractWidgetRenderState(g, mouseX, mouseY, delta);
		// EditBox only draws its hint when unfocused; keep placeholder visible while empty.
		if (this.inventorySearch.getValue().isEmpty()) {
			String hint = Component.translatable("betterpv.inv.search_hint").getString();
			PvDraw.text(
				g,
				this.font,
				hint,
				this.inventorySearch.getX(),
				this.inventorySearch.getY(),
				PvDraw.COLOR_MUTED
			);
		}

		// Item tips deferred to extractRenderState (after frame tabs) so they paint on top.
	}

	private void layoutInventorySearch(int x, int y, int w) {
		if (this.inventorySearch == null) {
			return;
		}
		int inset = 4;
		// Unbordered EditBox draws text at getY() (not vertically centred) - match the box.
		int textH = Math.max(8, this.font.lineHeight);
		int textY = y + Math.max(0, (SEARCH_H - textH) / 2);
		this.inventorySearch.setWidth(Math.max(20, w - inset * 2));
		this.inventorySearch.setHeight(textH);
		this.inventorySearch.setX(x + inset);
		this.inventorySearch.setY(textY);
		this.inventorySearch.setVisible(true);
	}

	private void hideInventorySearch() {
		if (this.inventorySearch == null) {
			return;
		}
		this.inventorySearch.setFocused(false);
		this.inventorySearch.setVisible(false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (click == null) {
			return super.mouseClicked(click, doubled);
		}
		if (!uiInteractive()) {
			return true;
		}
		double mx = click.x();
		double my = click.y();
		if (this.tab == PvTab.DUNGEONS && this.dungeonPage.mouseClicked(mx, my)) {
			return true;
		}
		if (this.tab == PvTab.MINING && this.miningPage.mouseClicked(mx, my)) {
			return true;
		}
		if (this.tab == PvTab.GARDEN && this.gardenPage.mouseClicked(mx, my)) {
			return true;
		}
		if (this.tab == PvTab.PETS && this.petsPage.mouseClicked(mx, my)) {
			return true;
		}
		if (this.tab == PvTab.AUCTIONS
			&& this.auctionPage.mouseClicked(mx, my, this.subSelection.getOrDefault(this.tab, PvSubTab.AUCTION_STATS))) {
			return true;
		}
		if (this.tab == PvTab.COLLECTIONS
			&& this.collectionsPage.mouseClicked(mx, my, this.subSelection.getOrDefault(this.tab, PvSubTab.COLLECTIONS_LIST))) {
			return true;
		}
		if (this.tab.isInventorySplit() && this.inventoryPage.mouseClicked(mx, my)) {
			return true;
		}
		if (this.topBar.click(mx, my) || this.sideBar.click(mx, my) || this.inventoryBar.click(mx, my)) {
			return true;
		}
		if (this.tab == PvTab.HOME && this.homePage.clickWeight(mx, my)) {
			return true;
		}
		if (this.tab == PvTab.HOME && this.homePage.clickNetworth(mx, my, click.button())) {
			return true;
		}
		if (this.tab == PvTab.HOME && this.homePage.clickLeftPanel(mx, my)) {
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!uiInteractive()) {
			return true;
		}
		if (this.tab == PvTab.DUNGEONS && this.dungeonPage.mouseScrolled(scrollY)) {
			return true;
		}
		if (this.tab == PvTab.PETS && this.petsPage.mouseScrolled(scrollY)) {
			return true;
		}
		if (this.tab == PvTab.AUCTIONS && this.auctionPage.mouseScrolled(mouseX, mouseY, scrollY)) {
			return true;
		}
		if (this.tab == PvTab.COLLECTIONS
			&& this.collectionsPage.mouseScrolled(
				mouseX,
				mouseY,
				scrollY,
				this.subSelection.getOrDefault(this.tab, PvSubTab.COLLECTIONS_LIST)
			)) {
			return true;
		}
		if (this.tab == PvTab.GARDEN
			&& this.gardenPage.mouseScrolled(
				mouseX,
				mouseY,
				scrollY,
				this.subSelection.getOrDefault(this.tab, PvSubTab.GARDEN_OVERVIEW)
			)) {
			return true;
		}
		if (this.tab == PvTab.MINING
			&& this.miningPage.mouseScrolled(
				mouseX,
				mouseY,
				scrollY,
				this.subSelection.getOrDefault(this.tab, PvSubTab.MINING_OVERVIEW)
			)) {
			return true;
		}
		if (this.tab.isInventorySplit() && this.inventoryPage.mouseScrolled(scrollY)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private void ensureGardenIsland() {
		if (this.gardenFetchStarted) {
			return;
		}
		GardenSnapshot current = this.gardenPage.snapshot();
		if (current.islandLoaded()) {
			return;
		}
		String id = this.profileId;
		if (id == null || id.isBlank()) {
			this.gardenPage.apply(current.withIslandError("Missing profile id"));
			this.gardenFetchStarted = true;
			return;
		}
		this.gardenFetchStarted = true;
		this.gardenPage.apply(current.withIslandLoading());
		HypixelApiClient.skyblockGarden(id).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.screen != this) {
					return;
				}
				GardenSnapshot base = this.gardenPage.snapshot();
				if (error != null) {
					BetterPV.LOGGER.warn("Garden fetch failed for profile {}", id, error);
					this.gardenPage.apply(base.withIslandError(
						error.getMessage() == null ? "Garden fetch failed" : error.getMessage()
					));
					return;
				}
				if (opt == null || opt.isEmpty()) {
					this.gardenPage.apply(base.withIslandError("Garden unavailable"));
					return;
				}
				try {
					this.gardenPage.apply(base.withIsland(opt.get()));
				} catch (Exception exception) {
					BetterPV.LOGGER.warn("Garden parse failed for profile {}", id, exception);
					this.gardenPage.apply(base.withIslandError("Garden parse failed"));
				}
			});
		});
	}

	private void ensureGardenContests() {
		if (this.gardenContestsFetchStarted) {
			return;
		}
		GardenSnapshot current = this.gardenPage.snapshot();
		UUID uuid = this.playerUuid;
		String id = this.profileId;
		if (uuid == null || id == null || id.isBlank()) {
			this.gardenPage.apply(current.withContestsError("Missing profile id"));
			this.gardenContestsFetchStarted = true;
			return;
		}
		this.gardenContestsFetchStarted = true;
		this.gardenPage.apply(current.withContestsLoading());
		EliteBotApiClient.contests(uuid, id).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.screen != this) {
					return;
				}
				GardenSnapshot base = this.gardenPage.snapshot();
				if (error != null) {
					BetterPV.LOGGER.warn("Elite contests failed for {}", id, error);
					if (!base.contests().isEmpty()) {
						this.gardenPage.patch(base.withContestsReady());
					} else {
						this.gardenPage.patch(base.withContestsError(
							error.getMessage() == null ? "Contests unavailable" : error.getMessage()
						));
					}
					return;
				}
				if (opt == null || opt.isEmpty()) {
					// Keep Hypixel contest list if Elite miss.
					if (!base.contests().isEmpty()) {
						this.gardenPage.patch(base.withContestsReady());
					} else {
						this.gardenPage.patch(base.withContestsError("Contests unavailable"));
					}
					return;
				}
				try {
					this.gardenPage.patch(base.withEliteContests(opt.get()));
				} catch (Exception exception) {
					BetterPV.LOGGER.warn("Elite contests parse failed for {}", id, exception);
					this.gardenPage.patch(base.withContestsError("Contest parse failed"));
				}
			});
		});
	}

	private void ensureGardenWeight() {
		if (this.gardenWeightFetchStarted) {
			return;
		}
		GardenSnapshot current = this.gardenPage.snapshot();
		UUID uuid = this.playerUuid;
		String id = this.profileId;
		if (uuid == null || id == null || id.isBlank()) {
			this.gardenPage.apply(current.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.failed("Missing profile id")));
			this.gardenWeightFetchStarted = true;
			return;
		}
		this.gardenWeightFetchStarted = true;
		this.gardenPage.patch(current.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.pending()));
		EliteBotApiClient.weight(uuid, id).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.screen != this) {
					return;
				}
				GardenSnapshot base = this.gardenPage.snapshot();
				if (error != null) {
					BetterPV.LOGGER.warn("Elite weight failed for {}", id, error);
					this.gardenPage.patch(base.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.failed(
						error.getMessage() == null ? "Weight unavailable" : error.getMessage()
					)));
					return;
				}
				if (opt == null || opt.isEmpty()) {
					this.gardenPage.patch(base.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.failed("Weight unavailable")));
					return;
				}
				try {
					this.gardenPage.patch(base.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.fromElite(opt.get())));
				} catch (Exception exception) {
					BetterPV.LOGGER.warn("Elite weight parse failed for {}", id, exception);
					this.gardenPage.patch(base.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.failed("Weight parse failed")));
				}
			});
		});
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event != null) {
			boolean typingSearch = this.inventorySearch != null
				&& this.inventorySearch.isVisible()
				&& this.inventorySearch.isFocused();
			// Always feed arrows / BA / Enter into Konami unless typing in search.
			if (!typingSearch && MoulberryMode.keyPressed(event.key())) {
				return true;
			}
			if (uiInteractive() && this.tab == PvTab.DUNGEONS && this.dungeonPage.keyPressed(event.key())) {
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (event != null) {
			boolean typingSearch = this.inventorySearch != null
				&& this.inventorySearch.isVisible()
				&& this.inventorySearch.isFocused();
			if (!typingSearch) {
				String text = event.codepointAsString();
				if (text != null && !text.isEmpty() && MoulberryMode.charTyped(text.charAt(0))) {
					return true;
				}
			}
			if (uiInteractive() && this.tab == PvTab.DUNGEONS) {
				String text = event.codepointAsString();
				if (text != null && !text.isEmpty() && this.dungeonPage.charTyped(text.charAt(0))) {
					return true;
				}
			}
		}
		return super.charTyped(event);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
