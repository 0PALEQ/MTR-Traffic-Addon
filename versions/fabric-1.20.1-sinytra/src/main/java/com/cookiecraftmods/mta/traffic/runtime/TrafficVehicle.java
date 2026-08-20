package com.cookiecraftmods.mta.traffic.runtime;

import com.cookiecraftmods.mta.traffic.vehicle.TrafficVehicleDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TrafficVehicle {
	// Lane offset
	// Używane aby pojazdy nie jechały dokładnie w tym samym miejscu
	private static final double MIN_LATERAL_OFFSET_METERS = -0.3D;
	private static final double MAX_LATERAL_OFFSET_METERS = 0.3D;

	// ID pojazdu
	private final UUID id;
	// Definicja pojazdu (długość, max prędkość, przyspieszenie, hamowanie)
	private final TrafficVehicleDefinition definition;
	// Trasa którą pojazd podąża
	private final TrafficRoute route;
	// ID punktu gdzie pojazd się spawnuje
	private final String spawnPointId;
	// ID punktu gdzie pojazd znika
	private final String despawnPointId;
	// Przesunięcie pojazdu na boku (determinystyczne na podstawie UUID)
	private final double lateralOffsetMeters;

	private double smoothedSpeedKph;
	private TrafficVehiclePosition cachedCurrentPosition;
	private int cachedPositionSegmentIndex = -1;
	private double cachedPositionDistanceMeters = Double.NaN;
	
	// STAN POJAZDU PODCZAS RUCHU
	private int segmentIndex;                       // Obecny segment na trasie (0-based)
	private double distanceOnSegmentMeters;         // Dystans przejechany na obecnym segmencie
	private double speedKph;                        // Aktualna prędkość w km/h

	public TrafficVehicle(UUID id, TrafficVehicleDefinition definition, TrafficRoute route, String spawnPointId, String despawnPointId, double distanceOnSegmentMeters, double speedKph) {
		this(id, definition, route, spawnPointId, despawnPointId, 0, distanceOnSegmentMeters, speedKph);
	}

	public TrafficVehicle(UUID id, TrafficVehicleDefinition definition, TrafficRoute route, String spawnPointId, String despawnPointId, int segmentIndex, double distanceOnSegmentMeters, double speedKph) {
		this.id = id;
		this.definition = definition;
		this.route = route;
		this.spawnPointId = spawnPointId;
		this.despawnPointId = despawnPointId;
		this.lateralOffsetMeters = deterministicLateralOffset(id);
		this.segmentIndex = segmentIndex;
		this.distanceOnSegmentMeters = distanceOnSegmentMeters;
		this.speedKph = speedKph;
		this.smoothedSpeedKph = Math.max(0.0D, speedKph);
	}

	public UUID id() {
		return id;
	}

	public TrafficVehicleDefinition definition() {
		return definition;
	}

	public TrafficRoute route() {
		return route;
	}

	public String spawnPointId() {
		return spawnPointId;
	}

	public String despawnPointId() {
		return despawnPointId;
	}

	public int segmentIndex() {
		return segmentIndex;
	}

	public double distanceOnSegmentMeters() {
		return distanceOnSegmentMeters;
	}

	public double speedKph() {
		return speedKph;
	}

	public double smoothedSpeedKph() {
		return smoothedSpeedKph;
	}

	public Optional<TrafficRouteSegment> currentSegment() {
		final List<TrafficRouteSegment> segments = route.segments();
		if (segments.isEmpty() || segmentIndex < 0 || segmentIndex >= segments.size()) {
			return Optional.empty();
		}

		return Optional.of(segments.get(segmentIndex));
	}

	public Optional<String> currentConnectorId() {
		return currentSegment().map(TrafficRouteSegment::directedConnectorId);
	}

	public Optional<TrafficRouteSegment> nextSegment() {
		final List<TrafficRouteSegment> segments = route.segments();
		final int nextSegmentIndex = segmentIndex + 1;
		if (segments.isEmpty() || nextSegmentIndex < 0 || nextSegmentIndex >= segments.size()) {
			return Optional.empty();
		}

		return Optional.of(segments.get(nextSegmentIndex));
	}

	public double distanceToEndOfCurrentSegmentMeters() {
		return currentSegment().map(segment -> Math.max(0.0D, segment.lengthMeters() - distanceOnSegmentMeters)).orElse(0.0D);
	}

	public double currentSegmentSpeedLimitKph() {
		return currentSegment().map(TrafficRouteSegment::speedLimitKph).orElse(0.0D);
	}

	public TrafficVehiclePosition currentPosition() {
		final List<TrafficRouteSegment> segments = route.segments();
		if (segments.isEmpty()) {
			return new TrafficVehiclePosition(0.0D, 0.0D, 0.0D, 0.0F, 0.0F);
		}

		final int currentSegmentIndex = Math.min(segmentIndex, segments.size() - 1);
		if (cachedCurrentPosition == null || cachedPositionSegmentIndex != currentSegmentIndex || Double.compare(cachedPositionDistanceMeters, distanceOnSegmentMeters) != 0) {
			cachedCurrentPosition = positionOnSegment(segments.get(currentSegmentIndex), distanceOnSegmentMeters);
			cachedPositionSegmentIndex = currentSegmentIndex;
			cachedPositionDistanceMeters = distanceOnSegmentMeters;
		}
		return cachedCurrentPosition;
	}

	public TrafficVehiclePosition positionOnSegment(TrafficRouteSegment segment, double distanceMeters) {
		final PathSample pathSample = samplePath(segment, distanceMeters);
		final double yawRadians = Math.toRadians(pathSample.yawDegrees());
		final double x = pathSample.x() - Math.sin(yawRadians) * lateralOffsetMeters;
		final double y = pathSample.y();
		final double z = pathSample.z() + Math.cos(yawRadians) * lateralOffsetMeters;
		final float yawDegrees = pathSample.yawDegrees();
		final float pitchDegrees = pathSample.pitchDegrees();
		return new TrafficVehiclePosition(x, y, z, yawDegrees, pitchDegrees);
	}

	public boolean tick(double tickDurationSeconds, double allowedSpeedKph) {
		// GŁÓWNA PĘTLA SYMULACJI POJAZDU - wywoływana co 50ms
		// Zwraca true jeśli pojazd powinien być usunięty (dotarł do despawn)
		
		final List<TrafficRouteSegment> segments = route.segments();
		if (segments.isEmpty()) {
			// Brak trasy = pojazd jest dead
			return true;
		}

		// KROK 1: KONWERSJA JEDNOSTEK I POBIERANIE PARAMETRÓW
		// Prędkość z km/h na m/s (dzielenie przez 3.6)
		final double previousSpeedMetersPerSecond = Math.max(0.0D, speedKph) / 3.6D;
		
		// Oblicz docelową prędkość z uwzględnieniem hamowania na następny segment
		final double targetSpeedKph = speedTargetWithBrakingDistance(segments, allowedSpeedKph);
		final double targetSpeedMetersPerSecond = Math.max(0.0D, targetSpeedKph) / 3.6D;
		
		// Pobierz parametry pojazdu
		final double accelerationMetersPerSecondSquared = definition.effectiveAccelerationMetersPerSecondSquared();
		
		// KROK 2: OBLICZENIE PRĘDKOŚCI NA NASTĘPNY TICK
		// Przyspieszanie lub hamowanie w zależności od prędkości docelowej
		final double nextSpeedMetersPerSecond;
		if (previousSpeedMetersPerSecond < targetSpeedMetersPerSecond) {
			// Przyspieszaj: v = min(v_target, v_poprzednia + a*dt)
			nextSpeedMetersPerSecond = Math.min(targetSpeedMetersPerSecond, previousSpeedMetersPerSecond + accelerationMetersPerSecondSquared * tickDurationSeconds);
		} else {
			// The resolved target is a safety cap derived from the available stopping distance.
			// Never continue faster than that cap, otherwise a vehicle can brake physically yet
			// still overshoot into the vehicle or signal that produced the limit.
			nextSpeedMetersPerSecond = targetSpeedMetersPerSecond;
		}

		// Przywróć prędkość do km/h
		speedKph = nextSpeedMetersPerSecond * 3.6D;
		smoothedSpeedKph += (speedKph - smoothedSpeedKph) * 0.25D;
		
		// KROK 3: OBLICZENIE DYSTANSU PRZEJECHANY W TYM TICKU
		// Używamy średniej prędkości (trapez) żeby być dokładnym
		double remainingDistanceMeters = (previousSpeedMetersPerSecond + nextSpeedMetersPerSecond) * 0.5D * tickDurationSeconds;

		// KROK 4: PORUSZANIE SIĘ PO TRASIE
		// Pętla przesuwająca pojazd przez segmenty dopóki ma dystans do przejechania
		while (remainingDistanceMeters > 0.0D && segmentIndex < segments.size()) {
			final TrafficRouteSegment currentSegment = segments.get(segmentIndex);
			// Ile metrów zostało na obecnym segmencie
			final double remainingSegmentMeters = currentSegment.lengthMeters() - distanceOnSegmentMeters;

			// Jeśli dystans do przejechania < dystansu na segmencie
			if (remainingDistanceMeters < remainingSegmentMeters) {
				// Przesunięcie w obrębie tego samego segmentu - gotowe
				distanceOnSegmentMeters += remainingDistanceMeters;
				// Sprawdzenie czy to był segment despawn
				if (shouldDespawnOnCurrentSegment(segments)) {
					return true;
				}
				return false;
			}

			// Pojazd dojeżdża do końca segmentu
			remainingDistanceMeters -= Math.max(remainingSegmentMeters, 0.0D);
			segmentIndex++;  // Przejdź do następnego segmentu
			distanceOnSegmentMeters = 0.0D;  // Reset dystansu na nowym segmencie

			// Sprawdzenie czy pojazd nie przekroczył rozmiar trasy
			if (segmentIndex >= segments.size()) {
				// Koniec trasy - pojazd znika
				return true;
			}

			// Ogranicza prędkość do limitu następnego segmentu
			speedKph = Math.min(speedKph, Math.max(0.0D, Math.min(allowedSpeedKph, segments.get(segmentIndex).speedLimitKph())));
			
			// Sprawdzenie czy nowy segment jest despawn (mogło być przesunięcie bez pracy)
			if (shouldDespawnOnCurrentSegment(segments)) {
				return true;
			}
		}

		// Tick zakończył się - pojazd zostaje na mapie
		return false;
	}

	private double speedTargetWithBrakingDistance(List<TrafficRouteSegment> segments, double allowedSpeedKph) {
		if (segmentIndex < 0 || segmentIndex >= segments.size()) {
			return 0.0D;
		}

		final TrafficRouteSegment currentSegment = segments.get(segmentIndex);
		double targetSpeedKph = Math.max(0.0D, Math.min(allowedSpeedKph, Math.min(definition.maxSpeedKph(), currentSegment.speedLimitKph())));
		final double nextSegmentTargetSpeedKph = nextSegment()
			.map(segment -> Math.min(definition.maxSpeedKph(), segment.speedLimitKph()))
			.orElse(0.0D);

		if (nextSegmentTargetSpeedKph < targetSpeedKph) {
			final double distanceToBrakeMeters = Math.max(0.0D, currentSegment.lengthMeters() - distanceOnSegmentMeters);
			final double brakingMetersPerSecondSquared = definition.effectiveBrakingMetersPerSecondSquared();
			final double nextTargetMetersPerSecond = nextSegmentTargetSpeedKph / 3.6D;
			final double maxMetersPerSecondForBrakeDistance = Math.sqrt(nextTargetMetersPerSecond * nextTargetMetersPerSecond + 2.0D * brakingMetersPerSecondSquared * distanceToBrakeMeters);
			targetSpeedKph = Math.min(targetSpeedKph, maxMetersPerSecondForBrakeDistance * 3.6D);
		}

		return targetSpeedKph;
	}

	private boolean shouldDespawnOnCurrentSegment(List<TrafficRouteSegment> segments) {
		if (segmentIndex < 0 || segmentIndex >= segments.size()) {
			return false;
		}

		final TrafficRouteSegment currentSegment = segments.get(segmentIndex);
		if (!currentSegment.despawnConnector()) {
			return false;
		}

		final double activationDistance = Math.min(Math.max(definition.lengthMeters() * 0.25D, 0.5D), Math.max(currentSegment.lengthMeters(), 0.5D));
		return distanceOnSegmentMeters >= activationDistance || currentSegment.lengthMeters() <= activationDistance;
	}

	private static double lerp(double start, double end, double progress) {
		return start + (end - start) * progress;
	}

	private static double deterministicLateralOffset(UUID id) {
		final long hash = id == null ? 0L : id.getMostSignificantBits() ^ id.getLeastSignificantBits();
		final double normalized = (double) Long.remainderUnsigned(hash, 10_000L) / 9_999.0D;
		return MIN_LATERAL_OFFSET_METERS + normalized * (MAX_LATERAL_OFFSET_METERS - MIN_LATERAL_OFFSET_METERS);
	}

	private static PathSample samplePath(TrafficRouteSegment segment, double distanceMeters) {
		final List<TrafficPathPoint> path = segment.path();
		if (path.size() < 2) {
			final double progress = segment.lengthMeters() <= 0.0D ? 0.0D : Math.min(1.0D, Math.max(0.0D, distanceMeters / segment.lengthMeters()));
			final double x = lerp(segment.startX(), segment.endX(), progress);
			final double y = lerp(segment.startY(), segment.endY(), progress);
			final double z = lerp(segment.startZ(), segment.endZ(), progress);
			final Orientation orientation = orientation(
				segment.endX() - segment.startX(),
				segment.endY() - segment.startY(),
				segment.endZ() - segment.startZ()
			);
			return new PathSample(x, y, z, orientation.yawDegrees(), orientation.pitchDegrees());
		}

		final double normalizedDistance = segment.lengthMeters() <= 0.0D
			? 0.0D
			: Math.min(1.0D, Math.max(0.0D, distanceMeters / segment.lengthMeters()));
		final double scaledIndex = normalizedDistance * (path.size() - 1.0D);
		final int previousIndex = Math.min(path.size() - 2, (int) Math.floor(scaledIndex));
		final double progress = Math.min(1.0D, Math.max(0.0D, scaledIndex - previousIndex));
		final TrafficPathPoint previous = path.get(previousIndex);
		final TrafficPathPoint next = path.get(previousIndex + 1);
		final Orientation orientation = orientation(
			next.x() - previous.x(),
			next.y() - previous.y(),
			next.z() - previous.z()
		);
		return new PathSample(
			lerp(previous.x(), next.x(), progress),
			lerp(previous.y(), next.y(), progress),
			lerp(previous.z(), next.z(), progress),
			orientation.yawDegrees(),
			orientation.pitchDegrees()
		);
	}

	private static Orientation orientation(double dx, double dy, double dz) {
		final double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
		final float yawDegrees = (float) Math.toDegrees(Math.atan2(dz, dx));
		final float pitchDegrees = (float) Math.toDegrees(Math.atan2(dy, horizontalDistance));
		return new Orientation(yawDegrees, pitchDegrees);
	}

	private record PathSample(double x, double y, double z, float yawDegrees, float pitchDegrees) {
	}

	private record Orientation(float yawDegrees, float pitchDegrees) {
	}
}
