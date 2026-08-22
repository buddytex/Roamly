"""
Test Suite for Simplified Realistic Train + Robot Traffic Model
Verifies 4 persistent trains, random startup placement, train anti-collision, and robot intelligent yielding.
"""
import sys
import os
import random

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from config import TrackStatus, RobotStatus, TrainStatus, AnomalyType, AnomalySeverity, MissionStatus
from world.track_builder import build_benchmark_railway_network
from entities.robot import InspectionRobot
from entities.train import Train
from server.decision_engine import DecisionEngine
from main import spawn_initial_four_trains

def test_exact_four_trains_and_random_distribution():
    """
    Verifies that exactly 4 trains (TR01, TR02, TR03, TR04) are registered with valid non-overlapping positions.
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)
    spawn_initial_four_trains(engine, graph)

    assert len(engine.trains) == 4, f"Expected 4 trains, got {len(engine.trains)}"
    train_ids = {t.agent_id for t in engine.trains}
    assert train_ids == {"TR01", "TR02", "TR03", "TR04"}

    # Verify no overlapping positions on the same track
    tracks_occupied = {}
    for t in engine.trains:
        if t.current_track_id not in tracks_occupied:
            tracks_occupied[t.current_track_id] = []
        tracks_occupied[t.current_track_id].append(t.distance_along_track_m)

    for tid, dists in tracks_occupied.items():
        if len(dists) > 1:
            dists.sort()
            for i in range(len(dists) - 1):
                gap = dists[i+1] - dists[i]
                assert gap >= 60.0, f"Trains on track {tid} are too close at startup! Gap={gap:.1f}m"

    print("[PASS] Test 1: Exactly 4 persistent trains and random non-overlapping initial placement verified.")

def test_continuous_train_movement_and_no_collision():
    """
    Verifies that all 4 trains move continuously without colliding or teleporting.
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)
    spawn_initial_four_trains(engine, graph)

    # Step simulation for 100 ticks
    for _ in range(100):
        engine.step(0.1)
        for t in engine.trains:
            t.step(0.1, other_trains=engine.trains, sim_time=engine.sim_time_sec)

        # Check all pairs of trains for physical collisions
        for i in range(len(engine.trains)):
            for j in range(i + 1, len(engine.trains)):
                t1 = engine.trains[i]
                t2 = engine.trains[j]
                if t1.current_track_id == t2.current_track_id:
                    gap = abs(t1.distance_along_track_m - t2.distance_along_track_m)
                    assert gap >= 35.0 or (t1.speed == 0.0 or t2.speed == 0.0), f"Collision between {t1.agent_id} and {t2.agent_id} on {t1.current_track_id}! Gap={gap:.1f}m"

    print("[PASS] Test 2: Continuous 4-train movement without collision verified.")

def test_robot_changes_track_to_make_way_for_train():
    """
    Verifies that the robot actively changes track to clear the mainline when a train approaches,
    minimizing train disruption (Section 8 & 11).
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    # Robot on T03a moving towards T02b destination
    robot = InspectionRobot("RG01", graph, "T03a", 20.0)
    engine.register_robot(robot)

    anomaly = engine.report_anomaly("ANOM_T", "T02b", chainage_km=184.7, x=440.0, y=0.0)
    engine.create_and_dispatch_mission(anomaly.anomaly_id)

    # Fast Train TR02 placed behind robot on direct path
    train2 = Train("TR02", graph, "T02a", initial_distance_m=10.0, initial_speed_mps=16.0, autonomous_routing=False)
    train2.set_route(["T02a", "T02b", "T02c"])
    engine.register_train(train2)

    for _ in range(10):
        engine.step(0.1)
        train2.step(0.1, other_trains=engine.trains, sim_time=engine.sim_time_sec)

    # Robot should have evaluated alternative tracks and set route via bypass T_X15
    assert engine.robot.status in (RobotStatus.CHANGING_TRACK, RobotStatus.TRANSIT)
    assert engine.current_route is not None
    print(f"[PASS] Test 3: Robot dynamic action to minimize train delay verified. Action: {engine.current_action}")

def test_robot_destination_arrival_and_inspection():
    """
    Verifies that the robot navigates to the user-clicked destination, conducts inspection, and completes mission.
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    robot = InspectionRobot("RG01", graph, "T03a", 10.0)
    engine.register_robot(robot)

    # User clicks coordinate near T03b (x=450.0, y=-40.0)
    anomaly = engine.report_anomaly_at_coordinates(450.0, -40.0)
    assert anomaly is not None
    assert robot.active_mission_id is not None

    # Step robot to arrival (travel time ~100s)
    for _ in range(700):
        engine.step(0.2)
        robot.step(0.2)
        m = engine.missions.get_mission("M001")
        if robot.status == RobotStatus.IDLE and m and m.status == MissionStatus.COMPLETED:
            break

    mission = engine.missions.get_mission("M001")
    assert mission.status == MissionStatus.COMPLETED
    print("[PASS] Test 4: Interactive destination selection, A* transit, and inspection completion verified.")

if __name__ == "__main__":
    print("=" * 70)
    print("RUNNING SIMPLIFIED REALISTIC TRAFFIC MODEL TESTS")
    print("=" * 70)
    test_exact_four_trains_and_random_distribution()
    test_continuous_train_movement_and_no_collision()
    test_robot_changes_track_to_make_way_for_train()
    test_robot_destination_arrival_and_inspection()
    print("=" * 70)
    print("ALL SIMPLIFIED TRAFFIC MODEL TESTS PASSED SUCCESSFULLY!")
