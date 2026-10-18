package com.cookiecraftmods.mta.client.debug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClientTrafficDebugState {
	private static final Map<UUID, ClientTrafficDebugTrack> TRACKS = new LinkedHashMap<>();
	private static final Map<UUID, Long> REMOVED_AT = new HashMap<>();
	private static final ClientTrafficPlaybackClock CLOCK = new ClientTrafficPlaybackClock();
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
		if (!CLOCK.observe(sampleNanos, nowNanos)) {
			return;
		}
		lastSequence = sequence;
		final Set<UUID> present = new HashSet<>();
		for (ClientTrafficDebugSnapshot snapshot : snapshots) {
			present.add(snapshot.id());
			REMOVED_AT.remove(snapshot.id());
			final ClientTrafficDebugTrack track = TRACKS.get(snapshot.id());
			if (track == null) {
				TRACKS.put(snapshot.id(), new ClientTrafficDebugTrack(snapshot, sampleNanos));
			} else {
				track.update(snapshot, sampleNanos);
			}
		}
		for (UUID id : TRACKS.keySet()) {
			if (!present.contains(id)) {
				REMOVED_AT.putIfAbsent(id, sampleNanos);
			}
		}
		pruneRemovedTracks(sampleNanos - ClientTrafficPlaybackClock.MAX_PLAYBACK_LAG_NANOS);
	}

	public static Collection<ClientTrafficDebugRenderState> allInterpolated() {
		return allInterpolatedAt(System.nanoTime());
	}

	static Collection<ClientTrafficDebugRenderState> allInterpolatedAt(long nowNanos) {
		final long playbackNanos = CLOCK.advance(nowNanos);
		// Creation and removal follow the same delayed timeline as movement.
		pruneRemovedTracks(playbackNanos);
		final Collection<ClientTrafficDebugRenderState> rendered = new ArrayList<>(TRACKS.size());
		for (ClientTrafficDebugTrack track : TRACKS.values()) {
			if (track.hasSampleAt(playbackNanos)) {
				rendered.add(track.interpolate(playbackNanos));
			}
		}
		return rendered;
	}

	private static void pruneRemovedTracks(long playbackNanos) {
		REMOVED_AT.entrySet().removeIf(entry -> {
			if (entry.getValue() <= playbackNanos) {
				TRACKS.remove(entry.getKey());
				return true;
			}
			return false;
		});
	}

	public static void clear() {
		TRACKS.clear();
		REMOVED_AT.clear();
		CLOCK.clear();
		lastSequence = -1L;
	}
}
