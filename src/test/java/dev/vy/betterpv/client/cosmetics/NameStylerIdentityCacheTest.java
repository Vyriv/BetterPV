package dev.vy.betterpv.client.cosmetics;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.ref.WeakReference;
import org.junit.Test;

public class NameStylerIdentityCacheTest {
	@Test
	public void usesIdentityInsteadOfEquality() {
		NameStylerIdentityCache<String, Object> cache = new NameStylerIdentityCache<>(8);
		String key = new String("same");
		Object value = new Object();
		cache.put(key, value);
		assertSame(value, cache.get(key));
		assertNull(cache.get(new String("same")));
	}

	@Test
	public void doesNotStronglyRetainKeysOrValues() {
		NameStylerIdentityCache<Object, Object> cache = new NameStylerIdentityCache<>(8);
		WeakReference<Object>[] references = addTemporaryEntry(cache);
		assertTrue("identity cache retained a key or value strongly", waitForCollection(references));
	}

	@Test
	public void clearRemovesEntries() {
		NameStylerIdentityCache<Object, Object> cache = new NameStylerIdentityCache<>(8);
		Object key = new Object();
		cache.put(key, new Object());
		cache.clear();
		assertNull(cache.get(key));
	}

	@SuppressWarnings("unchecked")
	private static WeakReference<Object>[] addTemporaryEntry(NameStylerIdentityCache<Object, Object> cache) {
		Object key = new Object();
		Object value = new Object();
		cache.put(key, value);
		return new WeakReference[]{new WeakReference<>(key), new WeakReference<>(value)};
	}

	private static boolean waitForCollection(WeakReference<Object>[] references) {
		long deadline = System.nanoTime() + 5_000_000_000L;
		while (System.nanoTime() < deadline) {
			System.gc();
			if (references[0].get() == null && references[1].get() == null) return true;
			byte[] pressure = new byte[64 * 1024];
			pressure[0] = 1;
			Thread.yield();
		}
		return false;
	}
}
