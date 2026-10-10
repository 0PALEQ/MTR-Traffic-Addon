package com.cookiecraftmods.mta.traffic;

import com.cookiecraftmods.mta.mixin.DeleteDataResponseAccessor;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionDefinition;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNodeType;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionRegistry;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraph;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphBuilder;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphEdge;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphEndpointIndex;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrNodeKey;
import com.cookiecraftmods.mta.traffic.point.TrafficPointDefinition;
import com.cookiecraftmods.mta.traffic.point.TrafficPointType;
import com.cookiecraftmods.mta.traffic.point.TrafficSavedPointRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mtr.core.data.Rail;
import org.mtr.core.operation.DeleteDataRequest;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TrafficNetworkLifecycleTest {
	private static final String DIMENSION = "minecraft:overworld";
	@TempDir Path world;

	@BeforeEach
	void start() throws Exception {
		clearRegistries();
		field(TrafficManager.class, "fullGraphRefreshInFlight").setBoolean(null, false);
		field(TrafficManager.class, "submittedRailGraphSignature").set(null, null);
		field(TrafficManager.class, "pendingFullGraphRefresh").set(null, null);
		field(TrafficManager.class, "graphRefreshRevision").setLong(null, 0L);
		invokeManager("startGraphBuildExecutor");
	}

	@AfterEach
	void stop() throws Exception {
		invokeManager("stopGraphBuildExecutor");
		field(TrafficManager.class, "requestedNetworkRefreshDimensionId").set(null, null);
		clearRegistries();
	}

	@Test
	void startupSelectsTheSavedTrafficDimensionAndBuildsWithoutAPlayer() throws Exception {
		assertEquals(DIMENSION, TrafficManager.initialNetworkDimension(DIMENSION));
		putPoint(point("example:city|spawn|one", TrafficPointType.SPAWN, false));
		final String selected = TrafficManager.initialNetworkDimension(DIMENSION);
		assertEquals("example:city", selected);
		field(TrafficManager.class, "requestedNetworkRefreshDimensionId").set(null, selected);
		field(TrafficManager.class, "networkRefreshNotBeforeNanos").setLong(null, 0);
		TrafficManager.updateFullMtrRailGraph("example/city", List.of(rail(0, 40)));
		flushGraphWorker();
		assertNotNull(field(TrafficManager.class, "pendingFullGraphRefresh").get(null));
		assertNull(field(TrafficManager.class, "requestedNetworkRefreshDimensionId").get(null));
	}

	@Test
	void railDeletionDoesNotRequestANetworkRefresh() throws Exception {
		field(TrafficManager.class, "requestedNetworkRefreshDimensionId").set(null, null);
		field(TrafficManager.class, "latestGraphDimensionId").set(null, DIMENSION);
		final long revision = field(TrafficManager.class, "graphRefreshRevision").getLong(null);
		TrafficManager.onMtrRailsDeleted(DIMENSION, List.of(rail(0, 40).getHexId()));
		final var method = TrafficManager.class.getDeclaredMethod("applyPendingRailDeletions", net.minecraft.server.MinecraftServer.class);
		method.setAccessible(true);
		method.invoke(null, new Object[]{null});
		assertNull(field(TrafficManager.class, "requestedNetworkRefreshDimensionId").get(null));
		assertEquals(revision, field(TrafficManager.class, "graphRefreshRevision").getLong(null));
	}

	@Test
	void refreshDuringABuildDiscardsTheOldResultAndKeepsTheNewRequest() throws Exception {
		final CountDownLatch release = new CountDownLatch(1);
		executor().execute(() -> {
			try {
				release.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		try {
			requestNow();
			TrafficManager.updateFullMtrRailGraph("minecraft/overworld", List.of(rail(0, 40)));
			requestNow();
		} finally {
			release.countDown();
		}
		flushGraphWorker();
		assertNull(field(TrafficManager.class, "pendingFullGraphRefresh").get(null));
		assertEquals(DIMENSION, field(TrafficManager.class, "requestedNetworkRefreshDimensionId").get(null));
		TrafficManager.updateFullMtrRailGraph(DIMENSION, List.of(rail(0, 40)));
		flushGraphWorker();
		assertNotNull(field(TrafficManager.class, "pendingFullGraphRefresh").get(null));
	}

	@Test
	void fullSnapshotRemovesDisconnectedPointsAndPreservesDisabledRails() throws Exception {
		final Rail disabled = rail(0, 0);
		final TrafficPointDefinition existing = point(DIMENSION + "|spawn|existing", TrafficPointType.SPAWN, false);
		final TrafficPointDefinition reversed = point(DIMENSION + "|despawn|reversed", TrafficPointType.DESPAWN, true);
		putPoint(existing);
		putPoint(reversed);
		putPoint(point("minecraft:the_nether|spawn|other", TrafficPointType.SPAWN, false));
		assertTrue(MtrGraphBuilder.buildFromRailSnapshots(MtrGraphBuilder.snapshotRails(List.of(disabled))).isEmpty());
		final MtrGraph partial = MtrGraphBuilder.buildFromRailSnapshots(MtrGraphBuilder.snapshotRails(List.of(disabled, rail(10, 40))));
		TrafficSavedPointRegistry.refreshConnectorRoutes(DIMENSION, partial, 0, 0, 30_000);
		assertEquals(3, TrafficSavedPointRegistry.getDefinitions().size(), "A routable graph must not prune a disabled rail");
		assertTrue(TrafficSavedPointRegistry.reconcileConnectorPoints(DIMENSION, Set.of(disabled.getHexId())).isEmpty());
		assertEquals(Set.of(existing.id(), reversed.id()), Set.copyOf(TrafficSavedPointRegistry.reconcileConnectorPoints(DIMENSION, Set.of())));
		assertEquals(1, TrafficSavedPointRegistry.getDefinitions().size());
		requestNow();
		TrafficManager.updateFullMtrRailGraph(DIMENSION, List.of());
		flushGraphWorker();
		assertNotNull(field(TrafficManager.class, "pendingFullGraphRefresh").get(null), "Deleting the last rail must still publish an empty graph");
	}

	@Test
	void actualMtrRailAndNodeDeletionQueuesBothConnectorTypes() throws Exception {
		final Simulator simulator = new Simulator("minecraft/overworld", new String[]{"minecraft/overworld"}, world, false);
		try {
			for (boolean deleteNode : new boolean[]{false, true}) {
				final Rail rail = rail(0, 40);
				simulator.rails.add(rail);
				simulator.sync();
				final TrafficPointDefinition spawn = point(DIMENSION + "|spawn|one", TrafficPointType.SPAWN, false);
				final TrafficPointDefinition despawn = point(DIMENSION + "|despawn|two", TrafficPointType.DESPAWN, true);
				putPoint(spawn);
				putPoint(despawn);
				final DeleteDataRequest request = new DeleteDataRequest();
				if (deleteNode) {
					request.addRailNodePosition(new org.mtr.core.data.Position(0, 64, 0));
				} else {
					request.addRailId(rail.getHexId());
				}
				final var response = request.delete(simulator);
				final List<String> deleted = ((DeleteDataResponseAccessor) (Object) response).mta$getDeletedRailIds();
				assertEquals(List.of(rail.getHexId()), deleted);
				assertTrue(simulator.rails.isEmpty());
				assertFalse(((Queue<?>) field(TrafficManager.class, "PENDING_RAIL_DELETIONS").get(null)).isEmpty(), "Real MTR deletion must invoke the addon mixin");
				assertEquals(Set.of(spawn.id(), despawn.id()), Set.copyOf(TrafficSavedPointRegistry.removeConnectorPoints(DIMENSION, deleted)));
				assertTrue(TrafficSavedPointRegistry.removeConnectorPoints(DIMENSION, deleted).isEmpty());
			}
		} finally {
			simulator.stop();
		}
	}

	@Test
	void intersectionQueriesStayLocalOnALargeNetworkAndHandleNegativeCoordinates() {
		final List<MtrGraphEdge> edges = new ArrayList<>();
		for (int i = 0; i < 20_000; i++) {
			edges.add(edge(i * 256L, i * 256L + 10L));
		}
		final MtrGraphEdge nearby = edge(-130, -120);
		edges.add(nearby);
		final MtrGraph graph = new MtrGraph(Map.of(), edges);
		final MtrGraphEndpointIndex index = new MtrGraphEndpointIndex(graph);
		assertEquals(List.of(nearby), List.copyOf(index.query(-130, 0, -120, 0)));
		assertTrue(index.query(0, 0, 10, 0).size() < 10);
		assertEquals(edges.size(), index.query(-30_000_000, -30_000_000, 30_000_000, 30_000_000).size());
	}

	@Test
	@SuppressWarnings("unchecked")
	void nodeRefreshHandlesEmptyGraphsAndPreservesConcurrentDashboardEdits() throws Exception {
		final TrafficIntersectionDefinition original = new TrafficIntersectionDefinition("test", "test", DIMENSION,
			0, 0, 0, 20, 100, 20, true, true, null, null, null, null, null, null,
			List.of(new TrafficIntersectionNode(0, 64, 0, TrafficIntersectionNodeType.IN, 1)));
		final Map<String, TrafficIntersectionDefinition> definitions = (Map<String, TrafficIntersectionDefinition>) field(TrafficIntersectionRegistry.class, "DEFINITIONS").get(null);
		definitions.put(original.id(), original);
		final var refresh = TrafficIntersectionRegistry.prepareNodeRefresh(DIMENSION, new MtrGraph(Map.of(), List.of()));
		definitions.put(original.id(), original.withName("edited"));
		assertEquals(0, TrafficIntersectionRegistry.applyNodeRefresh(refresh));
		assertEquals("edited", TrafficIntersectionRegistry.getDefinition(original.id()).orElseThrow().name());
		assertEquals(1, TrafficIntersectionRegistry.refreshNodes(DIMENSION, new MtrGraph(Map.of(), List.of())));
		assertTrue(TrafficIntersectionRegistry.getDefinition(original.id()).orElseThrow().nodes().isEmpty());
	}

	private static TrafficPointDefinition point(String id, TrafficPointType type, boolean reverse) {
		return new TrafficPointDefinition(id, type, 5, 64, 0, true, 40,
			reverse ? 10L : 0L, 64L, 0L, reverse ? 0L : 10L, 64L, 0L, "custom", List.of("taxi"));
	}

	private static Rail rail(long x, int speed) {
		return new Rail(JsonReader.parse("{\"position1\":{\"x\":" + x + ",\"y\":64,\"z\":0},\"position2\":{\"x\":" + (x + 10) + ",\"y\":64,\"z\":0},\"angle1\":\"E\",\"angle2\":\"W\",\"shape\":\"QUADRATIC\",\"speedLimit1\":" + speed + ",\"speedLimit2\":" + speed + "}"));
	}

	private static MtrGraphEdge edge(long x1, long x2) {
		return new MtrGraphEdge(x1 + ":" + x2, new MtrNodeKey(x1, 64, 0), new MtrNodeKey(x2, 64, 0), 10, 40, false, List.of(), List.of());
	}

	private static void putPoint(TrafficPointDefinition point) throws Exception {
		final var method = TrafficSavedPointRegistry.class.getDeclaredMethod("putDefinition", TrafficPointDefinition.class);
		method.setAccessible(true);
		method.invoke(null, point);
	}

	private static void requestNow() throws Exception {
		assertTrue(TrafficManager.requestNetworkRefresh(DIMENSION));
		field(TrafficManager.class, "networkRefreshNotBeforeNanos").setLong(null, 0);
	}

	private static ExecutorService executor() throws Exception {
		return (ExecutorService) field(TrafficManager.class, "graphBuildExecutor").get(null);
	}

	private static void flushGraphWorker() throws Exception {
		executor().submit(() -> { }).get(10, TimeUnit.SECONDS);
	}

	private static void clearRegistries() throws Exception {
		((Map<?, ?>) field(TrafficSavedPointRegistry.class, "DEFINITIONS").get(null)).clear();
		((Map<?, ?>) field(TrafficSavedPointRegistry.class, "POINT_IDS_BY_RAIL").get(null)).clear();
		((Map<?, ?>) field(TrafficIntersectionRegistry.class, "DEFINITIONS").get(null)).clear();
	}

	private static void invokeManager(String name) throws Exception {
		final var method = TrafficManager.class.getDeclaredMethod(name);
		method.setAccessible(true);
		method.invoke(null);
	}

	private static Field field(Class<?> type, String name) throws Exception {
		final Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}
}
