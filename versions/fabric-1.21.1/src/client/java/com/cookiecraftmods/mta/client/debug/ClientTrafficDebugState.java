package com.cookiecraftmods.mta.client.debug;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientTrafficDebugState {
	private static final Map<UUID, ClientTrafficDebugTrack> TRACKS = new LinkedHashMap<>();
	private static long lastSequence = -1L;

	private ClientTrafficDebugState() {
	}

	public static void replace(long sequence, Collection<ClientTrafficDebugSnapshot> snapshots) {
		if (sequence <= lastSequence) {
			return;
		}
		lastSequence = sequence;
		final long nowNanos = System.nanoTime();
		final Map<UUID, ClientTrafficDebugTrack> updatedTracks = new LinkedHashMap<>();

		for (ClientTrafficDebugSnapshot snapshot : snapshots) {
			final ClientTrafficDebugTrack track = TRACKS.get(snapshot.id());
			if (track == null) {
				updatedTracks.put(snapshot.id(), new ClientTrafficDebugTrack(snapshot, nowNanos));
			} else {
				track.update(snapshot, nowNanos);
				updatedTracks.put(snapshot.id(), track);
			}
		}

		TRACKS.clear();
		TRACKS.putAll(updatedTracks);
	}

	public static Collection<ClientTrafficDebugRenderState> allInterpolated() {
		final long nowNanos = System.nanoTime();
		return TRACKS.values().stream()
			.map(track -> track.interpolate(nowNanos))
			.toList();
	}

	public static void clear() {
		TRACKS.clear();
		lastSequence = -1L;
	}
}
