package com.cookiecraftmods.mta.traffic;

import com.cookiecraftmods.mta.traffic.mtr.graph.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrafficRefreshPerformanceTest {
	@Test
	void everyTiltSettingInvalidatesCachedGeometry() throws Exception {
		final var original = rail(0, 10, 40, false);
		final var components = MtrGraphBuilder.RailSnapshot.class.getRecordComponents();
		final Class<?>[] types = new Class<?>[components.length];
		final Object[] values = new Object[components.length];
		for (int i = 0; i < components.length; i++) {
			types[i] = components[i].getType();
			values[i] = components[i].getAccessor().invoke(original);
		}
		final var constructor = MtrGraphBuilder.RailSnapshot.class.getDeclaredConstructor(types);
		for (int i = 0; i < components.length; i++) {
			if (!components[i].getName().startsWith("tilt")) {
				continue;
			}
			final var cache = new MtrGraphBuilder.GeometryCache();
			final var first = cache.build(List.of(original));
			final Object[] edited = values.clone();
			if (types[i] == int.class) {
				edited[i] = 1;
			} else {
				edited[i] = 1.0D;
			}
			final var changed = cache.build(List.of(constructor.newInstance(edited)));
			assertNotSame(first.edges().getFirst().path(), changed.edges().getFirst().path(), components[i].getName());
			assertSame(changed.edges().getFirst().path(),
				cache.build(List.of(constructor.newInstance(edited))).edges().getFirst().path());
		}
	}

	@Test
	void cachedGeometryIsReusedButRailPropertiesAndShapeChangesAreNotLost() {
		final var cache = new MtrGraphBuilder.GeometryCache();
		final var original = rail(0, 1, 40, false);
		final var first = cache.build(List.of(original));
		final var changedProperties = cache.build(List.of(rail(0, 1, 80, true)));
		assertSame(first.edges().get(0).path(), changedProperties.edges().get(0).path());
		assertEquals(80, changedProperties.edges().get(0).speedLimitKph());
		assertTrue(changedProperties.edges().get(0).mtaPathBlocked());
		final var changedShape = cache.build(List.of(rail(0, 10, 80, true)));
		assertNotEquals(first.edges().get(0).path(), changedShape.edges().get(0).path());
		assertTrue(cache.build(List.of()).isEmpty());
		assertNotSame(first.edges().get(0).path(), cache.build(List.of(original)).edges().get(0).path(),
			"Removed rails must not remain in the geometry cache");
	}

	@Test
	void reusableSearchHandlesLaterEarlierUnreachableAndBlockedDestinations() {
		final var graph = MtrGraphBuilder.buildFromRailSnapshots(List.of(
			rail(0, 1, 40, false), rail(1, 2, 40, false), rail(2, 3, 40, false), rail(3, 4, 40, true)));
		final var search = new MtrGraphPathFinder.Search(graph, node(0));
		for (int destination : new int[]{1, 3, 2, 0, 4, 100, 3}) {
			assertEquals(MtrGraphPathFinder.findFastestRoute(graph, node(0), node(destination)), search.find(node(destination)));
		}
		assertEquals(3, search.find(node(3)).orElseThrow().route().segments().size());
		assertTrue(search.find(node(4)).isEmpty());
	}

	@Test
	void refreshAndSharedSearchOn44000NodesAnd62000Edges() {
		final List<MtrGraphBuilder.RailSnapshot> rails = new ArrayList<>();
		for (int i = 0; i < 43_999; i++) {
			rails.add(new MtrGraphBuilder.RailSnapshot(i, 64, 0, "E", i + 1, 64, 0, "W",
				"QUADRATIC", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 40, i < 18_001 ? 40 : 0, false, List.of()));
		}
		final var cache = new MtrGraphBuilder.GeometryCache();
		final long started = System.nanoTime();
		final var cold = cache.build(rails);
		final long coldEnd = System.nanoTime();
		final var warm = cache.build(rails);
		final long warmEnd = System.nanoTime();
		assertEquals(44_000, warm.adjacency().size());
		assertEquals(62_000, warm.edges().size());
		assertSame(cold.edges().get(0).path(), warm.edges().get(0).path());
		final var search = new MtrGraphPathFinder.Search(warm, node(0));
		for (int destination : new int[]{1000, 10_000, 30_000, 43_999, 5000}) {
			assertEquals(destination, search.find(node(destination)).orElseThrow().route().segments().size());
		}
		System.out.printf("Synthetic refresh (44,000 nodes / 62,000 edges): cold=%.1fms warm=%.1fms shared search=%.1fms%n",
			(coldEnd - started) / 1e6, (warmEnd - coldEnd) / 1e6, (System.nanoTime() - warmEnd) / 1e6);
	}

	private static MtrNodeKey node(long x) {
		return new MtrNodeKey(x, 64, 0);
	}

	private static MtrGraphBuilder.RailSnapshot rail(long start, long end, long speed, boolean blocked) {
		return new MtrGraphBuilder.RailSnapshot(start, 64, 0, "E", end, 64, 0, "W",
			"QUADRATIC", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, speed, 0, blocked, List.of());
	}
}
