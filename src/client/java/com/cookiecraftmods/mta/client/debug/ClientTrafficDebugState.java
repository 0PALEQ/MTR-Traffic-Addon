package com.cookiecraftmods.mta.client.debug;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientTrafficDebugState {
	private static final long INTERPOLATION_WINDOW_NANOS = 250_000_000L;
	private static final Map<UUID, ClientTrafficDebugTrack> TRACKS = new LinkedHashMap<>();

	private ClientTrafficDebugState() {
	}

	public static void replace(Collection<ClientTrafficDebugSnapshot> snapshots) {
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
			.map(track -> track.interpolate(nowNanos, INTERPOLATION_WINDOW_NANOS))
			.toList();
	}

	public static void clear() {
		TRACKS.clear();
	}
}
