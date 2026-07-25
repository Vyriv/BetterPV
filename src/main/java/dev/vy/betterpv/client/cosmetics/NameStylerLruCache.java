package dev.vy.betterpv.client.cosmetics;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded concurrent cache. Prefer this over a synchronized LinkedHashMap on the render hot path.
 */
final class NameStylerLruCache<K, V> {
	private final ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();
	private final int maxEntries;

	NameStylerLruCache(int maxEntries) {
		this.maxEntries = Math.max(16, maxEntries);
	}

	V getCached(K key) {
		return this.map.get(key);
	}

	void putCached(K key, V value) {
		this.map.put(key, value);
		if (this.map.size() > this.maxEntries) {
			this.map.clear();
		}
	}

	void clearCache() {
		this.map.clear();
	}
}
