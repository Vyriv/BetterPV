package dev.vy.betterpv.client.api;

import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public final class SessionAuthConnectionGateTest {
	private final AtomicLong now = new AtomicLong();
	private final SessionAuthConnectionGate gate = new SessionAuthConnectionGate(now::get);
	private final Object first = new Object();

	@Test
	public void playInitAloneCannotAdmitProofAndReadyHasGracePeriod() {
		assertEquals(-1L, gate.permit(first));
		gate.connecting();
		gate.expectPlay(first);
		now.addAndGet(TimeUnit.MINUTES.toNanos(1));
		assertEquals(-1L, gate.permit(first));
		gate.playReady(first);
		assertEquals(-1L, gate.permit(first));
		now.addAndGet(TimeUnit.SECONDS.toNanos(5) - 1L);
		assertEquals(-1L, gate.permit(first));
		now.incrementAndGet();
		assertTrue(gate.beginProof(gate.permit(first)));
	}

	@Test
	public void reconnectInvalidatesChallengeAndRejectsOldReadyEvent() {
		long oldPermit = ready(first);
		gate.connecting();
		gate.playReady(first);
		now.addAndGet(TimeUnit.SECONDS.toNanos(10));
		assertEquals(-1L, gate.permit(first));
		assertFalse(gate.beginProof(oldPermit));
		Object second = new Object();
		long newPermit = ready(second);
		gate.disconnected(first); // A late old disconnection cannot disable a new joined connection.
		assertEquals(newPermit, gate.permit(second));
		assertTrue(gate.beginProof(newPermit));
	}

	@Test
	public void configurationTransitionRequiresAnotherPlayJoin() {
		long previous = ready(first);
		gate.connecting(); // Configuration INIT, including a reconfiguration on the same socket.
		assertFalse(gate.beginProof(previous));
		gate.expectPlay(first);
		assertEquals(-1L, gate.permit(first));
		gate.playReady(first);
		now.addAndGet(TimeUnit.SECONDS.toNanos(5));
		assertTrue(gate.beginProof(gate.permit(first)));
	}

	@Test
	public void disconnectAndCancelledLoginStayClosed() {
		ready(first);
		gate.disconnected(first);
		now.addAndGet(TimeUnit.HOURS.toNanos(1));
		assertEquals(-1L, gate.permit(first));
		gate.connecting();
		assertEquals(-1L, gate.permit(new Object()));
	}

	@Test
	public void slowProofDoesNotBlockVanillaLifecycleAndItsResultIsAbandoned() throws Exception {
		long permit = ready(first);
		CountDownLatch proofStarted = new CountDownLatch(1);
		CountDownLatch releaseNetwork = new CountDownLatch(1);
		try (var workers = Executors.newFixedThreadPool(2)) {
			var proof = workers.submit(() -> {
				assertTrue(gate.beginProof(permit));
				proofStarted.countDown();
				// The production Mojang call likewise runs outside the gate monitor.
				assertTrue(releaseNetwork.await(5, TimeUnit.SECONDS));
				return gate.finishProof(permit);
			});
			try {
				assertTrue(proofStarted.await(2, TimeUnit.SECONDS));
				assertFalse(gate.beginProof(permit));
				assertTrue(workers.submit(gate::connecting).get(1, TimeUnit.SECONDS));
				Object transferred = new Object();
				long afterTransfer = ready(transferred);
				assertFalse(gate.beginProof(afterTransfer)); // Keep single flight across a transfer.
				releaseNetwork.countDown();
				assertFalse(proof.get(2, TimeUnit.SECONDS));
				assertTrue(gate.beginProof(afterTransfer));
				assertTrue(gate.finishProof(afterTransfer));
			} finally {
				releaseNetwork.countDown();
			}
		}
	}

	private long ready(Object listener) {
		gate.expectPlay(listener);
		gate.playReady(listener);
		now.addAndGet(TimeUnit.SECONDS.toNanos(5));
		return gate.permit(listener);
	}
}
