package com.cookiecraftmods.mta.traffic.intersection;

import com.cookiecraftmods.mta.traffic.mtr.graph.MtrNodeKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TrafficIntersectionEditor {
	private static final int MIN_GREEN_TICKS = 300;

	private TrafficIntersectionEditor() {
	}

	public static Optional<TrafficIntersectionDefinition> update(TrafficIntersectionDefinition definition, String action, int delta, String value) {
		return Optional.ofNullable(switch (action) {
			case "name" -> definition.withName(value);
			case "enabled" -> definition.withControl(!definition.isEnabled(), definition.autoDetectNodes(), definition.signalMode(), definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), definition.nodes());
			case "signal_mode" -> definition.withControl(definition.enabled(), definition.autoDetectNodes(), definition.effectiveSignalMode() == TrafficIntersectionSignalMode.AUTO ? TrafficIntersectionSignalMode.MANUAL : TrafficIntersectionSignalMode.AUTO, definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), definition.nodes());
			case "level" -> definition.withLevel(definition.effectiveLevel() == TrafficIntersectionLevel.TRAIN ? TrafficIntersectionLevel.CROSSING : TrafficIntersectionLevel.TRAIN);
			case "find_nodes" -> definition.withControl(definition.enabled(), true, definition.signalMode(), definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), definition.nodes());
			case "train_node_toggle" -> definition.withTrainNodeNumbers(toggleTrainNode(definition, value));
			case "phase_duration" -> definition.withControl(definition.enabled(), definition.autoDetectNodes(), definition.signalMode(), clamp(definition.effectivePhaseDurationTicks() + delta, MIN_GREEN_TICKS, 2400), definition.phaseOrder(), updateGroupDuration(dashboardGroups(definition), value, delta), definition.nodes());
			case "node_type" -> definition.withControl(definition.enabled(), false, definition.signalMode(), definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), toggleNodeType(definition, value));
			case "phase_assign" -> definition.withControl(definition.enabled(), definition.autoDetectNodes(), definition.signalMode(), definition.phaseDurationTicks(), addPhase(definition.phaseOrder(), delta), addNodeToGroup(dashboardGroups(definition), value, delta), definition.nodes());
			case "phase_remove" -> definition.withControl(definition.enabled(), definition.autoDetectNodes(), definition.signalMode(), definition.phaseDurationTicks(), removePhase(definition.phaseOrder(), delta), removeGroupOrNode(dashboardGroups(definition), value, delta), definition.nodes());
			case "group_add" -> definition.withControl(definition.enabled(), definition.autoDetectNodes(), definition.signalMode(), definition.phaseDurationTicks(), definition.phaseOrder(), addGroup(dashboardGroups(definition)), definition.nodes());
			default -> null;
		});
	}

	private static List<Integer> toggleTrainNode(TrafficIntersectionDefinition definition, String encodedNode) {
		final MtrNodeKey targetNode = decodeNode(encodedNode);
		if (targetNode == null) {
			return definition.trainNodeNumbers();
		}
		Integer nodeNumber = null;
		for (TrafficIntersectionNode node : definition.nodes()) {
			if (node.x() == targetNode.x() && node.y() == targetNode.y() && node.z() == targetNode.z()) {
				nodeNumber = node.number();
				break;
			}
		}
		if (nodeNumber == null || nodeNumber <= 0) {
			return definition.trainNodeNumbers();
		}
		final List<Integer> updated = new ArrayList<>(definition.trainNodeNumbers());
		if (updated.contains(nodeNumber)) {
			updated.remove(Integer.valueOf(nodeNumber));
		} else {
			updated.add(nodeNumber);
		}
		return updated.stream().distinct().sorted().toList();
	}

	private static List<TrafficIntersectionNode> toggleNodeType(TrafficIntersectionDefinition definition, String encodedNode) {
		final MtrNodeKey targetNode = decodeNode(encodedNode);
		if (targetNode == null) {
			return definition.nodes();
		}

		final List<TrafficIntersectionNode> updatedNodes = new ArrayList<>(definition.nodes().size());
		for (TrafficIntersectionNode node : definition.nodes()) {
			if (node.x() == targetNode.x() && node.y() == targetNode.y() && node.z() == targetNode.z()) {
				final TrafficIntersectionNodeType type = node.type() == TrafficIntersectionNodeType.IN ? TrafficIntersectionNodeType.OUT : TrafficIntersectionNodeType.IN;
				updatedNodes.add(new TrafficIntersectionNode(node.x(), node.y(), node.z(), type, node.number()));
			} else {
				updatedNodes.add(node);
			}
		}
		return updatedNodes;
	}

	private static List<Integer> addPhase(List<Integer> phaseOrder, int number) {
		final List<Integer> updatedPhaseOrder = new ArrayList<>(phaseOrder);
		updatedPhaseOrder.add(Math.max(1, number));
		return updatedPhaseOrder;
	}

	private static List<Integer> removePhase(List<Integer> phaseOrder, int number) {
		final List<Integer> updatedPhaseOrder = new ArrayList<>(phaseOrder);
		updatedPhaseOrder.remove(Integer.valueOf(number));
		return updatedPhaseOrder;
	}

	private static List<TrafficIntersectionGroup> addGroup(List<TrafficIntersectionGroup> groups) {
		final List<TrafficIntersectionGroup> updatedGroups = new ArrayList<>(groups);
		updatedGroups.add(new TrafficIntersectionGroup("Group " + (updatedGroups.size() + 1), 100, List.of()));
		return updatedGroups;
	}

	private static List<TrafficIntersectionGroup> dashboardGroups(TrafficIntersectionDefinition definition) {
		if (!definition.groups().isEmpty()) {
			return definition.groups();
		}

		final List<Integer> inNumbers = definition.nodes().stream()
			.filter(node -> node.type() == TrafficIntersectionNodeType.IN)
			.map(TrafficIntersectionNode::number)
			.distinct()
			.sorted()
			.toList();
		final List<Integer> phaseOrder = definition.phaseOrder().isEmpty() ? inNumbers : definition.phaseOrder().stream().filter(inNumbers::contains).toList();
		return phaseOrder.stream()
			.map(number -> new TrafficIntersectionGroup("Group " + number, definition.effectivePhaseDurationTicks(), List.of(number)))
			.toList();
	}

	private static List<TrafficIntersectionGroup> addNodeToGroup(List<TrafficIntersectionGroup> groups, String rawIndex, int nodeNumber) {
		final int groupIndex = parseIndex(rawIndex);
		if (groupIndex < 0 || groupIndex >= groups.size() || nodeNumber <= 0) {
			return groups;
		}

		final List<TrafficIntersectionGroup> updatedGroups = new ArrayList<>(groups.size());
		for (int i = 0; i < groups.size(); i++) {
			final TrafficIntersectionGroup group = groups.get(i);
			if (i == groupIndex) {
				final List<Integer> nodeNumbers = new ArrayList<>(group.nodeNumbers());
				if (!nodeNumbers.contains(nodeNumber)) {
					nodeNumbers.add(nodeNumber);
				}
				updatedGroups.add(new TrafficIntersectionGroup(group.name(), group.effectiveGreenDurationTicks(), nodeNumbers));
			} else {
				updatedGroups.add(group);
			}
		}
		return updatedGroups;
	}

	private static List<TrafficIntersectionGroup> removeGroupOrNode(List<TrafficIntersectionGroup> groups, String rawIndex, int nodeNumber) {
		final int groupIndex = parseIndex(rawIndex);
		if (groupIndex < 0 || groupIndex >= groups.size()) {
			return groups;
		}

		final List<TrafficIntersectionGroup> updatedGroups = new ArrayList<>(groups);
		if (nodeNumber <= 0) {
			updatedGroups.remove(groupIndex);
			return updatedGroups;
		}

		final TrafficIntersectionGroup group = updatedGroups.get(groupIndex);
		final List<Integer> nodeNumbers = new ArrayList<>(group.nodeNumbers());
		nodeNumbers.remove(Integer.valueOf(nodeNumber));
		updatedGroups.set(groupIndex, new TrafficIntersectionGroup(group.name(), group.effectiveGreenDurationTicks(), nodeNumbers));
		return updatedGroups;
	}

	private static List<TrafficIntersectionGroup> updateGroupDuration(List<TrafficIntersectionGroup> groups, String rawIndex, int delta) {
		final int groupIndex = parseIndex(rawIndex);
		if (groupIndex < 0 || groupIndex >= groups.size()) {
			return groups;
		}

		final List<TrafficIntersectionGroup> updatedGroups = new ArrayList<>(groups);
		final TrafficIntersectionGroup group = updatedGroups.get(groupIndex);
		updatedGroups.set(groupIndex, new TrafficIntersectionGroup(group.name(), clamp(group.effectiveGreenDurationTicks() + delta, 20, 2400), group.nodeNumbers()));
		return updatedGroups;
	}

	private static int parseIndex(String rawIndex) {
		if (rawIndex == null || rawIndex.isBlank()) {
			return -1;
		}
		try {
			return Integer.parseInt(rawIndex);
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}

	private static MtrNodeKey decodeNode(String encodedNode) {
		if (encodedNode == null || encodedNode.isBlank()) {
			return null;
		}
		final String[] parts = encodedNode.split(",");
		if (parts.length != 3) {
			return null;
		}
		try {
			return new MtrNodeKey(Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2]));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}

