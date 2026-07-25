package dev.vy.vypv.client.gui;

import dev.vy.vypv.client.api.ProfileFetcher;
import dev.vy.vypv.client.data.ProfileSnapshot;
import dev.vy.vypv.client.gui.dungeons.DungeonPage;
import dev.vy.vypv.client.gui.home.HomePage;
import dev.vy.vypv.client.gui.inventories.InventoryPage;
import dev.vy.vypv.client.gui.nav.IconButtonBar;
import dev.vy.vypv.client.gui.nav.InventoryPane;
import dev.vy.vypv.client.gui.nav.MuseumSort;
import dev.vy.vypv.client.gui.nav.PvSubTab;
import dev.vy.vypv.client.gui.nav.PvTab;
import dev.vy.vypv.client.gui.pets.PetsPage;
import dev.vy.vypv.client.networth.NetworthBreakdown;
import dev.vy.vypv.client.weight.WeightBreakdown;
import dev.vy.vypv.client.weight.WeightSystem;
import dev.vy.vypv.VyPV;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

	private final String requestedName;
	private final HomePage homePage;
	private final DungeonPage dungeonPage = new DungeonPage();
	private final InventoryPage inventoryPage = new InventoryPage();
	private final PetsPage petsPage = new PetsPage();
	private final IconButtonBar topBar = new IconButtonBar();
	private final IconButtonBar sideBar = new IconButtonBar();
	private final IconButtonBar inventoryBar = new IconButtonBar();
	private final Map<PvTab, PvSubTab> subSelection = new EnumMap<>(PvTab.class);

	private PvTab tab = PvTab.HOME;
	private MuseumSort museumSort = MuseumSort.COMBAT;
	private boolean fetchStarted;
	private EditBox inventorySearch;
	private String inventorySearchQuery = "";

	public ProfileViewerScreen(String playerName) {
		super(Component.translatable("vypv.screen.title"));
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
			Component.translatable("vypv.inv.search")
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
		ProfileFetcher.fetch(this.requestedName).whenComplete((loaded, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.screen != this) {
					return;
				}
				if (error != null || loaded == null) {
					VyPV.LOGGER.warn("Profile fetch failed for {}", this.requestedName, error);
					this.homePage.applyLoaded(
						ProfileSnapshot.loading(this.requestedName),
						WeightBreakdown.empty(WeightSystem.SENITHER),
						WeightBreakdown.empty(WeightSystem.LILY),
						NetworthBreakdown.empty("Fetch failed"),
						new ItemStack[] { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY },
						error == null ? "Fetch failed" : (error.getMessage() == null ? error.toString() : error.getMessage())
					);
					return;
				}
				this.homePage.applyLoaded(
					loaded.snapshot(),
					loaded.senither(),
					loaded.lily(),
					loaded.networth(),
					loaded.armor(),
					loaded.error()
				);
				this.dungeonPage.apply(loaded.dungeons());
				this.inventoryPage.apply(loaded.inventories());
				this.petsPage.apply(loaded.pets());
			});
		});
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
		PvDraw.panel(graphics, panelX, panelY, panelW, panelH);

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
				} else if (entry instanceof MuseumSort sort) {
					icon = sort.icon();
					label = sort.label();
					click = () -> this.museumSort = sort;
				} else {
					continue;
				}
				side.add(new IconButtonBar.Entry(entry, icon, label, click));
			}
			this.sideBar.drawLeftFrameTabs(graphics, this.font, panelX, panelY, mouseX, mouseY, side, selectedLeft);
		}

		// Content uses the full panel - nothing reserved inside for subtabs.
		renderBody(graphics, panelX + PAD, panelY + PAD, panelW - PAD * 2, panelH - PAD * 2, mouseX, mouseY, delta);

		if (this.tab == PvTab.DUNGEONS) {
			this.dungeonPage.renderOverlay(graphics, this.font, this.width, this.height, mouseX, mouseY);
		}
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
			this.homePage.render(g, this.font, x, y, w, h, mouseX, mouseY, this.width, this.height);
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
				this.inventoryBar.maybeTooltip(g, this.font, pane.label(), hovered, bx - 4, by + btn / 2);
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
			String hint = Component.translatable("vypv.inv.search_hint").getString();
			PvDraw.text(
				g,
				this.font,
				hint,
				this.inventorySearch.getX(),
				this.inventorySearch.getY(),
				PvDraw.COLOR_MUTED
			);
		}

		// Item tips last so they paint above the button panel.
		this.inventoryPage.renderTooltip(g, this.font, mouseX, mouseY, this.width, this.height);
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
		double mx = click.x();
		double my = click.y();
		if (this.tab == PvTab.DUNGEONS && this.dungeonPage.mouseClicked(mx, my)) {
			return true;
		}
		if (this.tab == PvTab.PETS && this.petsPage.mouseClicked(mx, my)) {
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
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.tab == PvTab.DUNGEONS && this.dungeonPage.mouseScrolled(scrollY)) {
			return true;
		}
		if (this.tab == PvTab.PETS && this.petsPage.mouseScrolled(scrollY)) {
			return true;
		}
		if (this.tab.isInventorySplit() && this.inventoryPage.mouseScrolled(scrollY)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event != null && this.tab == PvTab.DUNGEONS && this.dungeonPage.keyPressed(event.key())) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (event != null && this.tab == PvTab.DUNGEONS) {
			String text = event.codepointAsString();
			if (text != null && !text.isEmpty() && this.dungeonPage.charTyped(text.charAt(0))) {
				return true;
			}
		}
		return super.charTyped(event);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
