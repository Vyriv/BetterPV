package dev.vy.betterpv.client.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ProfileViewerScaleTest {
	private static final float EPSILON = 0.0001F;

	@Test
	public void keepsTheScaleTwoBaselineAcrossMinecraftGuiScales() {
		ProfileViewerScale.Viewport guiOne = viewport(1920, 1080, 1920, 1080, 1, 100);
		ProfileViewerScale.Viewport guiTwo = viewport(960, 540, 1920, 1080, 2, 100);
		ProfileViewerScale.Viewport guiThree = viewport(640, 360, 1920, 1080, 3, 100);

		assertEquals(2.0F, guiOne.renderScale(), EPSILON);
		assertEquals(1.0F, guiTwo.renderScale(), EPSILON);
		assertEquals(2.0F / 3.0F, guiThree.renderScale(), EPSILON);
		assertEquals(960, guiOne.virtualWidth());
		assertEquals(960, guiTwo.virtualWidth());
		assertEquals(960, guiThree.virtualWidth());
	}

	@Test
	public void appliesAllSupportedUserScaleStops() {
		assertEquals(0.75F, viewport(960, 540, 1920, 1080, 2, 75).renderScale(), EPSILON);
		assertEquals(1.00F, viewport(960, 540, 1920, 1080, 2, 100).renderScale(), EPSILON);
		assertEquals(1.25F, viewport(960, 540, 1920, 1080, 2, 125).renderScale(), EPSILON);
		assertEquals(1.50F, viewport(960, 540, 1920, 1080, 2, 150).renderScale(), EPSILON);
	}

	@Test
	public void appliesScaleStopsIndependentlyOfMinecraftGuiScale() {
		int[] guiScales = {1, 2, 3, 4};
		int[] percentages = {75, 100, 125, 150};
		for (int guiScale : guiScales) {
			for (int percentage : percentages) {
				ProfileViewerScale.Viewport viewport = ProfileViewerScale.viewport(
					3840 / guiScale, 2160 / guiScale, 3840, 2160, guiScale, percentage,
					new ProfileViewerScale.Bounds(730, 420, 1190, 660)
				);
				assertEquals(
					2.0F / guiScale * percentage / 100.0F,
					viewport.renderScale(),
					EPSILON
				);
			}
		}
	}

	@Test
	public void clampsTheEffectiveScaleWhenControlsWouldClip() {
		ProfileViewerScale.Bounds bounds = new ProfileViewerScale.Bounds(-20, 5, 440, 260);
		int[][] windows = {{400, 225}, {640, 360}, {800, 450}};
		for (int[] window : windows) {
			ProfileViewerScale.Viewport viewport = ProfileViewerScale.viewport(
				window[0], window[1], window[0] * 2, window[1] * 2, 2, 150, bounds
			);

			assertEquals(1.5F, viewport.selectedScale(), EPSILON);
			assertTrue(viewport.effectiveScale() <= viewport.selectedScale());
			assertTrue(viewport.effectiveScale() >= 0.25F);
			assertTrue(viewport.toScreenX(bounds.left()) >= 0.0F);
			assertTrue(viewport.toScreenY(bounds.top()) >= 0.0F);
			assertTrue(viewport.toScreenX(bounds.right()) <= window[0]);
			assertTrue(viewport.toScreenY(bounds.bottom()) <= window[1]);
		}
	}

	@Test
	public void inverseMouseCoordinatesRoundTrip() {
		ProfileViewerScale.Viewport viewport = viewport(640, 360, 1920, 1080, 3, 125);
		float virtualX = 357.5F;
		float virtualY = 201.25F;

		assertEquals(virtualX, viewport.toVirtualX(viewport.toScreenX(virtualX)), EPSILON);
		assertEquals(virtualY, viewport.toVirtualY(viewport.toScreenY(virtualY)), EPSILON);
	}

	@Test
	public void sliderUsesFivePercentStops() {
		assertEquals(75, ProfileScaleSlider.percentAt(-100, 20));
		assertEquals(75, ProfileScaleSlider.percentAt(20, 20));
		assertEquals(100, ProfileScaleSlider.percentAt(20 + ProfileScaleSlider.WIDTH / 3.0, 20));
		assertEquals(150, ProfileScaleSlider.percentAt(20 + ProfileScaleSlider.WIDTH, 20));
		assertEquals(150, ProfileScaleSlider.percentAt(500, 20));
	}

	@Test
	public void sliderPositionStaysInsideTheViewport() {
		int[][] windows = {{80, 30}, {400, 225}, {960, 540}, {1920, 1080}};
		for (int[] window : windows) {
			ProfileScaleSlider.Position position = ProfileScaleSlider.position(window[0], window[1]);
			assertTrue(position.x() - 2 >= 0);
			assertTrue(position.y() - 1 >= 0);
			assertTrue(position.x() + ProfileScaleSlider.WIDTH + 2 <= window[0]);
			assertTrue(position.y() + ProfileScaleSlider.HEIGHT + 1 <= window[1]);
		}
	}

	@Test
	public void sliderScreenPositionDoesNotChangeWithPvScale() {
		int[] percentages = {75, 100, 125, 150};
		for (int guiScale = 1; guiScale <= 4; guiScale++) {
			int screenWidth = 1920 / guiScale;
			int screenHeight = 1080 / guiScale;
			ProfileViewerScale.Viewport sliderViewport = ProfileViewerScale.viewport(
				screenWidth, screenHeight, 1920, 1080, guiScale, 100, null
			);
			ProfileScaleSlider.Position position = ProfileScaleSlider.position(
				sliderViewport.virtualWidth(), sliderViewport.virtualHeight()
			);
			float expectedX = sliderViewport.toScreenX(position.x());
			float expectedY = sliderViewport.toScreenY(position.y());
			float previousPvX = Float.NaN;
			for (int percentage : percentages) {
				ProfileViewerScale.Viewport pvViewport = ProfileViewerScale.viewport(
					screenWidth, screenHeight, 1920, 1080, guiScale, percentage,
					new ProfileViewerScale.Bounds(250, 150, 710, 390)
				);
				float pvX = pvViewport.toScreenX(250);
				if (!Float.isNaN(previousPvX)) {
					assertTrue(Math.abs(pvX - previousPvX) > EPSILON);
				}
				previousPvX = pvX;
				assertEquals(expectedX, sliderViewport.toScreenX(position.x()), EPSILON);
				assertEquals(expectedY, sliderViewport.toScreenY(position.y()), EPSILON);
			}
			assertEquals(position.x(), sliderViewport.toVirtualX(expectedX), EPSILON);
			assertEquals(position.y(), sliderViewport.toVirtualY(expectedY), EPSILON);
			assertEquals(
				ProfileScaleSlider.SCREEN_MARGIN * 2.0F,
				(screenWidth - sliderViewport.toScreenX(position.x() + ProfileScaleSlider.WIDTH)) * guiScale,
				EPSILON
			);
			assertEquals(
				ProfileScaleSlider.SCREEN_MARGIN * 2.0F,
				(screenHeight - sliderViewport.toScreenY(position.y() + ProfileScaleSlider.HEIGHT)) * guiScale,
				EPSILON
			);
		}
	}

	@Test
	public void tipUsesTheIndependentBottomLeftOverlayPosition() {
		int tipWidth = 250;
		int tipHeight = 9;
		int[] percentages = {75, 100, 125, 150};
		for (int guiScale = 1; guiScale <= 4; guiScale++) {
			int screenWidth = 1920 / guiScale;
			int screenHeight = 1080 / guiScale;
			ProfileViewerScale.Viewport overlay = ProfileViewerScale.viewport(
				screenWidth, screenHeight, 1920, 1080, guiScale, 100, null
			);
			ProfileViewerScale.Point position = ProfileViewerScale.bottomLeftPosition(
				overlay.virtualWidth(), overlay.virtualHeight(), tipWidth, tipHeight,
				ProfileScaleSlider.SCREEN_MARGIN
			);
			float expectedX = overlay.toScreenX(position.x());
			float expectedY = overlay.toScreenY(position.y());
			for (int percentage : percentages) {
				ProfileViewerScale.viewport(
					screenWidth, screenHeight, 1920, 1080, guiScale, percentage,
					new ProfileViewerScale.Bounds(250, 150, 710, 390)
				);
				assertEquals(expectedX, overlay.toScreenX(position.x()), EPSILON);
				assertEquals(expectedY, overlay.toScreenY(position.y()), EPSILON);
			}
			assertEquals(
				ProfileScaleSlider.SCREEN_MARGIN * 2.0F,
				overlay.toScreenX(position.x()) * guiScale,
				EPSILON
			);
			assertEquals(
				ProfileScaleSlider.SCREEN_MARGIN * 2.0F,
				(screenHeight - overlay.toScreenY(position.y() + tipHeight)) * guiScale,
				EPSILON
			);
		}

		ProfileViewerScale.Point tiny = ProfileViewerScale.bottomLeftPosition(100, 8, 250, tipHeight, 14);
		assertEquals(0, tiny.x());
		assertEquals(0, tiny.y());
	}

	private static ProfileViewerScale.Viewport viewport(
		int screenWidth,
		int screenHeight,
		int framebufferWidth,
		int framebufferHeight,
		int guiScale,
		int percent
	) {
		return ProfileViewerScale.viewport(
			screenWidth, screenHeight, framebufferWidth, framebufferHeight, guiScale, percent,
			new ProfileViewerScale.Bounds(250, 150, 710, 390)
		);
	}
}
