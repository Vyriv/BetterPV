package dev.vy.betterpv.client.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class InFlightRequestRegistryTest {
	@Test
	public void sharesWorkAndIsolatesCallerCancellation() {
		InFlightRequestRegistry<String, String> registry = new InFlightRequestRegistry<>();
		CompletableFuture<String> source = new CompletableFuture<>();
		AtomicInteger starts = new AtomicInteger();

		InFlightRequestRegistry.Shared<String> first = registry.share("player", "first", () -> {
			starts.incrementAndGet();
			return source;
		});
		InFlightRequestRegistry.Shared<String> second = registry.share("player", "second", () -> {
			starts.incrementAndGet();
			return CompletableFuture.completedFuture("wrong");
		});

		assertEquals(1, starts.get());
		assertFalse(first.reused());
		assertTrue(second.reused());
		assertEquals("first", second.correlationId());
		assertTrue(second.future().cancel(false));
		assertFalse(source.isCancelled());

		source.complete("done");
		assertEquals("done", first.future().join());
		assertEquals(0, registry.size());
	}

	@Test
	public void removesFailedWorkSoTheNextCallCanRetry() {
		InFlightRequestRegistry<String, String> registry = new InFlightRequestRegistry<>();
		CompletableFuture<String> failed = new CompletableFuture<>();
		registry.share("player", "first", () -> failed);
		failed.completeExceptionally(new IllegalStateException("failed"));

		InFlightRequestRegistry.Shared<String> retry = registry.share(
			"player",
			"retry",
			() -> CompletableFuture.completedFuture("recovered")
		);
		assertFalse(retry.reused());
		assertEquals("recovered", retry.future().join());
		assertEquals(0, registry.size());
	}
}
