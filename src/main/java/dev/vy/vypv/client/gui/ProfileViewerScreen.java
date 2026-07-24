package dev.vy.vypv.client.gui;

import dev.vy.vypv.client.api.ProfileFetcher;
import dev.vy.vypv.client.data.ProfileSnapshot;
import dev.vy.vypv.client.gui.home.HomePage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ProfileViewerScreen extends Screen {
	private final String requestedName;
	private final HomePage homePage;
	private boolean fetchStarted;

	public ProfileViewerScreen(String playerName) {
		super(Component.translatable("vypv.screen.title"));
		this.requestedName = playerName == null || playerName.isBlank() ? "?" : playerName.trim();
		this.homePage = new HomePage(ProfileSnapshot.loading(this.requestedName));
	}

	@Override
	protected void init() {
		super.init();
		if (this.fetchStarted) {
			return;
		}
		this.fetchStarted = true;
		ProfileFetcher.fetch(this.requestedName).thenAccept(loaded -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.screen != this) {
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
			});
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		int panelW = Math.min(520, Math.max(380, this.width - 40));
		int contentW = panelW - 16;
		int contentH = this.homePage.preferredHeight(this.font, contentW);
		int titleH = this.font.lineHeight + 10;
		int panelH = titleH + contentH + 8;
		int panelX = (this.width - panelW) / 2;
		int panelY = (this.height - panelH) / 2;

		PvDraw.fill(graphics, 0, 0, this.width, this.height, 0x99000000);
		PvDraw.panel(graphics, panelX, panelY, panelW, panelH);

		String title = Component.translatable("vypv.screen.title").getString();
		PvDraw.text(graphics, this.font, title, panelX + 8, panelY + 6, PvDraw.COLOR_MUTED);

		int contentX = panelX + 8;
		int contentY = panelY + titleH;
		this.homePage.render(graphics, this.font, contentX, contentY, contentW, contentH, mouseX, mouseY, this.width, this.height);
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
		if (click != null && this.homePage.clickWeight(click.x(), click.y())) {
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean isPauseScreen() {
		// Keep the world from ticking/rendering behind PV — major CPU/GPU cost.
		return true;
	}
}
