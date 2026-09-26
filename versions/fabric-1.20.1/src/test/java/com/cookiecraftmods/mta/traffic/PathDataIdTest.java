package com.cookiecraftmods.mta.traffic;

import org.junit.jupiter.api.Test;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.data.TwoPositionsBase;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.tool.Angle;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PathDataIdTest {
	@Test
	void reusesIdsAndPreservesBothDirections() {
		final Position start = new Position(Long.MIN_VALUE, -1, 30_000_000);
		final Position end = new Position(Long.MAX_VALUE, 0, -30_000_000);
		final PathData path = path(start, end);
		final String forward = path.getHexId(false);
		final String reverse = path.getHexId(true);
		assertEquals(TwoPositionsBase.getHexIdRaw(start, end), forward);
		assertEquals(TwoPositionsBase.getHexIdRaw(end, start), reverse);
		assertNotEquals(forward, reverse);
		for (int i = 0; i < 100; i++) {
			assertSame(forward, path.getHexId(false));
			assertSame(reverse, path.getHexId(true));
		}
	}

	@Test
	void keepsPathsIndependentIncludingReversedAndCoincidentEndpoints() {
		final Random random = new Random(42);
		for (int i = 0; i < 100; i++) {
			final Position start = new Position(random.nextLong(), random.nextLong(), random.nextLong());
			final Position end = i == 0 ? start : new Position(random.nextLong(), random.nextLong(), random.nextLong());
			final PathData first = path(start, end);
			final PathData second = path(end, start);
			assertEquals(TwoPositionsBase.getHexIdRaw(end, start), first.getHexId(true));
			assertEquals(first.getHexId(true), second.getHexId(false));
			assertEquals(first.getHexId(false), second.getHexId(true));
			assertSame(first.getHexId(true), first.getHexId(true));
		}
	}

	@Test
	void preservesIdsAfterDataUpdatesAndPathCopies() {
		final PathData path = path(new Position(1, 2, 3), new Position(4, 5, 6));
		final String forward = path.getHexId(false);
		final String reverse = path.getHexId(true);
		path.updateData(JsonReader.parse("{\"speedLimit\":80,\"verticalRadius\":200}"));
		final PathData copy = new PathData(path, 100, 200);
		assertSame(forward, path.getHexId(false));
		assertSame(reverse, path.getHexId(true));
		assertEquals(forward, copy.getHexId(false));
		assertEquals(reverse, copy.getHexId(true));
		assertSame(copy.getHexId(false), copy.getHexId(false));
	}

	@Test
	void supportsConcurrentReaders() throws Exception {
		final Position start = new Position(-100, 64, 200);
		final Position end = new Position(500, -64, -200);
		final PathData path = path(start, end);
		final String forward = TwoPositionsBase.getHexIdRaw(start, end);
		final String reverse = TwoPositionsBase.getHexIdRaw(end, start);
		final var executor = Executors.newFixedThreadPool(4);
		try {
			final var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
			for (int thread = 0; thread < 4; thread++) {
				tasks.add(executor.submit(() -> {
					for (int i = 0; i < 100; i++) {
						assertEquals(forward, path.getHexId(false));
						assertEquals(reverse, path.getHexId(true));
					}
				}));
			}
			for (var task : tasks) {
				task.get(10, TimeUnit.SECONDS);
			}
		} finally {
			executor.shutdownNow();
		}
	}

	private static PathData path(Position start, Position end) {
		return new PathData(null, 0, 0, 0, 0, 100, start, Angle.E, end, Angle.W);
	}
}
