package dev.vy.betterpv.client.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.junit.After;
import org.junit.Test;

public class NameStylerIntegrationTest {
	@After
	public void resetRegistry() {
		PlayerCustomizationRegistry.initialize(List.of());
		NameStyler.clearCaches();
	}

	@Test
	public void longNoMatchTextPassesThroughRepeatedly() {
		PlayerCustomizationRegistry.initialize(List.of(PlayerCustomizationRegistryTest.loaded("Vyriv", false)));
		String raw = "meow".repeat(50_000);
		Component component = Component.literal(raw);
		assertSame(component, NameStyler.decorateFontText(component));
		assertSame(component, NameStyler.decorateFontText(component));
		assertSame(raw, NameStyler.decorateFontText(raw));
	}

	@Test
	public void longTextPreservesExactVisibleCasingAcrossSeveralMatches() {
		PlayerCustomizationRegistry.initialize(List.of(PlayerCustomizationRegistryTest.loaded("Vyriv", false)));
		String raw = "meow".repeat(50_000) + " Vyriv and VYRIV";
		Component transformed = (Component) NameStyler.decorateFontText(Component.literal(raw));
		assertEquals(raw, transformed.getString());
	}

	@Test
	public void dynamicallyGeneratedFormattedAndOrderedTextRetainsContent() {
		PlayerCustomizationRegistry.initialize(List.of(PlayerCustomizationRegistryTest.loaded("Vyriv", false)));
		for (int iteration = 0; iteration < 100; iteration++) {
			String raw = "prefix-" + iteration + " VYRIV suffix";
			FormattedText formatted = NameStyler.decorateFontText(FormattedText.of(raw));
			assertEquals(raw, formatted.getString());
			FormattedCharSequence ordered = NameStyler.decorateFontText(Component.literal(raw).getVisualOrderText());
			assertEquals(raw, plain(ordered));
		}
	}

	@Test
	public void animatedGradientAdvancesWithoutStaticIdentityCaching() throws Exception {
		PlayerCustomizationRegistry.initialize(List.of(PlayerCustomizationRegistryTest.loaded("Vyriv", true)));
		Component source = Component.literal("Vyriv");
		Component first = (Component) NameStyler.decorateFontText(source);
		Thread.sleep(20L);
		Component second = (Component) NameStyler.decorateFontText(source);
		assertNotSame(first, second);
		assertEquals("Vyriv", first.getString());
		assertNotEquals(colors(first), colors(second));
	}

	@Test
	public void registryUpdatesInvalidatePreviousResults() {
		PlayerCustomizationRegistry.initialize(List.of(PlayerCustomizationRegistryTest.loaded("FirstOwner", false)));
		Component first = Component.literal("FirstOwner");
		assertNotSame(first, NameStyler.decorateFontText(first));
		PlayerCustomizationRegistry.initialize(List.of(PlayerCustomizationRegistryTest.loaded("SecondOwner", false)));
		Component oldName = Component.literal("FirstOwner");
		Component newName = Component.literal("SecondOwner");
		assertSame(oldName, NameStyler.decorateFontText(oldName));
		assertNotSame(newName, NameStyler.decorateFontText(newName));
	}

	private static String plain(FormattedCharSequence sequence) {
		StringBuilder output = new StringBuilder();
		sequence.accept((index, style, codePoint) -> { output.appendCodePoint(codePoint); return true; });
		return output.toString();
	}

	private static List<Integer> colors(Component component) {
		List<Integer> output = new ArrayList<>();
		component.visit((style, segment) -> {
			if (style.getColor() != null) output.add(style.getColor().getValue());
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		return output;
	}
}
