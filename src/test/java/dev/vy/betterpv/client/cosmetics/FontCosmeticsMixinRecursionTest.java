package dev.vy.betterpv.client.cosmetics;

import static org.junit.Assert.assertEquals;

import dev.vy.betterpv.mixin.FontCosmeticsMixin;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import org.junit.Test;

public class FontCosmeticsMixinRecursionTest {
	@Test
	public void nestedDecorationRestoresThreadLocalDepth() throws Exception {
		Method decorateSafely = FontCosmeticsMixin.class.getDeclaredMethod("vypv$decorateSafely", Supplier.class);
		decorateSafely.setAccessible(true);
		Field depthField = FontCosmeticsMixin.class.getDeclaredField("vypv$decorationDepth");
		depthField.setAccessible(true);
		@SuppressWarnings("unchecked")
		ThreadLocal<Integer> depth = (ThreadLocal<Integer>) depthField.get(null);
		Supplier<Integer> nested = () -> {
			assertEquals(1, (int) depth.get());
			try {
				return (Integer) decorateSafely.invoke(null, (Supplier<Integer>) () -> {
					assertEquals(2, (int) depth.get());
					return 42;
				});
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError(exception);
			}
		};
		assertEquals(42, ((Integer) decorateSafely.invoke(null, nested)).intValue());
		assertEquals(0, (int) depth.get());
		depth.remove();
	}
}
