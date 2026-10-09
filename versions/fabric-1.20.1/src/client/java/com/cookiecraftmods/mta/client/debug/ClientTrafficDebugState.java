package com.cookiecraftmods.mta.client.debug;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientTrafficDebugState {
	private static final Map<UUID, ClientTrafficDebugTrack> TRACKS = new LinkedHashMap<>();
	private static final ClientTrafficPlaybackClock PLAYBACK_CLOCK = new ClientTrafficPlaybackClock();
	private static long lastSequence = -1L;

	private ClientTrafficDebugState() {
	}

	public static void replace(long sequence, Collection<ClientTrafficDebugSnapshot> snapshots) {
		replace(sequence, snapshots, Long.MIN_VALUE);
	}

	public static void replace(long sequence, Collection<ClientTrafficDebugSnapshot> snapshots, long simulationNanos) {
		replaceAt(sequence, snapshots, simulationNanos, System.nanoTime());
	}

	static void replaceAt(long sequence, Collection<ClientTrafficDebugSnapshot> snapshots, long simulationNanos, long nowNanos) {
		if (sequence <= lastSequence) {
			return;
		}
		final long sampleNanos = simulationNanos == Long.MIN_VALUE ? nowNanos : simulationNanos;
		if (!PLAYBACK_CLOCK.observe(sampleNanos, nowNanos)) {
			return;
		}
		lastSequence = sequence;
		final Map<UUID, ClientTrafficDebugTrack> updatedTracks = new LinkedHashMap<>();

		for (ClientTrafficDebugSnapshot snapshot : snapshots) {
			final ClientTrafficDebugTrack track = TRACKS.get(snapshot.id());
			if (track == null) {
				updatedTracks.put(snapshot.id(), new ClientTrafficDebugTrack(snapshot, nowNanos, sampleNanos));
			} else {
				track.update(snapshot, sampleNanos);
				updatedTracks.put(snapshot.id(), track);
			}
		}

		TRACKS.clear();
		TRACKS.putAll(updatedTracks);
	}

	public static Collection<ClientTrafficDebugRenderState> allInterpolated() {
		return allInterpolatedAt(System.nanoTime());
	}

	static Collection<ClientTrafficDebugRenderState> allInterpolatedAt(long nowNanos) {
		final long playbackNanos = PLAYBACK_CLOCK.advance(nowNanos);
		return TRACKS.values().stream()
			.map(track -> track.interpolateAt(playbackNanos))
			.toList();
	}

	public static void clear() {
		TRACKS.clear();
		PLAYBACK_CLOCK.clear();
		lastSequence = -1L;
	}
}
