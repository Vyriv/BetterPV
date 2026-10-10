package dev.vy.betterpv.client.api;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class HypixelApiClientBodyReadTimingTest {
	@Test
	public void readsExactBodyWithoutChangingBytes() throws IOException {
		byte[] expected = "complete streamed profile".getBytes(StandardCharsets.UTF_8);
		HypixelApiClient.BodyReadTiming timed = new HypixelApiClient.BodyReadTiming(
			new ByteArrayInputStream(expected), System.nanoTime()
		);
		try (timed) {
			assertArrayEquals(expected, timed.readAllBytes());
		}
		assertEquals(expected.length, timed.bytesRead());
		assertTrue(timed.readCalls() >= 2);
		assertTrue(timed.firstByteMillis() >= 0L);
	}

	@Test
	public void preservesPrematureReadFailureAndPartialByteCount() {
		InputStream interrupted = new InputStream() {
			private boolean sent;

			@Override
			public int read() throws IOException {
				if (this.sent) throw new IOException("closed");
				this.sent = true;
				return 'x';
			}

			@Override
			public int read(byte[] buffer, int offset, int length) throws IOException {
				if (this.sent) throw new IOException("closed");
				this.sent = true;
				buffer[offset] = 'x';
				return 1;
			}
		};
		HypixelApiClient.BodyReadTiming timed = new HypixelApiClient.BodyReadTiming(interrupted, System.nanoTime());
		try (timed) {
			timed.readAllBytes();
			throw new AssertionError("The interrupted stream must fail");
		} catch (IOException exception) {
			assertEquals("closed", exception.getMessage());
		}
		assertEquals(1, timed.bytesRead());
		assertTrue(timed.readCalls() >= 2);
	}
}
