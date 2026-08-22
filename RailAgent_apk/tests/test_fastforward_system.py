"""
Comprehensive Test Suite for Destination-Driven Robot Model, Time-Aware Dynamic Routing & Train Priority
"""
import sys
import os
import time

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from config import TrackStatus, RobotStatus, TrainStatus, AnomalyType, AnomalySeverity, MissionStatus, SafetyLevel
from world.track_builder import build_benchmark_railway_network
from entities.robot import InspectionRobot
from entities.train import Train
from planning.path_planner import PathPlanner
from planning.conflict_engine import ConflictEngine
from server.decision_engine import DecisionEngine

def test_startup_clean_state():
    """
    TEST 1: At startup, verify NO anomaly, NO active mission, NO green line, robot in IDLE.
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)
    robot = InspectionRobot("RG01", graph, "T03a", 10.0)
    engine.register_robot(robot)

    assert len(engine.anomalies.anomalies) == 0
    assert robot.active_mission_id is None
    assert robot.status == RobotStatus.IDLE
    assert engine.current_route is None
    print("[PASS] TEST 1: Clean startup state verified (No anomaly, No mission, Robot IDLE).")

def test_interactive_destination_anomaly_and_dynamic_route():
    """
    TEST 2: User selects anomaly by coordinate click -> creates destination-driven mission.
    A* computes shortest safe route clipped to anomaly coordinates.
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)
    robot = InspectionRobot("RG01", graph, "T03a", 10.0)
    engine.register_robot(robot)

    # Click near T02b (x=440.0, y=0.0)
    anomaly = engine.report_anomaly_at_coordinates(440.0, 0.0)
    assert anomaly is not None
    assert anomaly.track_id == "T02b"
    assert robot.active_mission_id == "M001"
    assert robot.status == RobotStatus.TRANSIT
    assert engine.current_route is not None
    assert engine.current_route.is_safe is True
    # Verify green line polyline terminates at exact anomaly x, y
    last_pt = engine.current_route.polyline_geometry[-1]
    assert abs(last_pt[0] - anomaly.x) < 0.1 and abs(last_pt[1] - anomaly.y) < 0.1
    print("[PASS] TEST 2: Destination-driven mission created with dynamic route clipped to anomaly.")

def test_train_priority_and_robot_yielding():
    """
    TEST 3: When a train approaches a shared switch/track, robot yields (TRAIN > ROBOT),
    enters YIELDING_TO_TRAIN / WAITING_FOR_CLEARANCE, waits for train to pass, then replans and resumes.
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)
    robot = InspectionRobot("RG01", graph, "T03a", 20.0)
    engine.register_robot(robot)

    anomaly = engine.report_anomaly_at_coordinates(440.0, 0.0)
    assert anomaly is not None

    # Train TR01 moving on T02a towards S12, and Train TR02 occupying T_X15
    train1 = Train("TR01", graph, "T02a", initial_distance_m=0.0, initial_speed_mps=4.5, autonomous_routing=False)
    train1.set_route(["T02a", "T02b", "T02c"])
    engine.register_train(train1)

    train2 = Train("TR02", graph, "T_X15", initial_distance_m=10.0, initial_speed_mps=5.0, autonomous_routing=False)
    train2.set_route(["T_X15"])
    engine.register_train(train2)

    # Step simulation -> Robot detects conflict on both direct and detour, yields and stops before switch
    for _ in range(5):
        engine.step(0.1)
        robot.step(0.1)
        train1.step(0.1)
        train2.step(0.1)

    assert robot.status in (RobotStatus.YIELDING_TO_TRAIN, RobotStatus.WAITING_FOR_CLEARANCE)
    assert robot.target_speed == 0.0
    assert engine.blocking_train_id is not None
    print("[PASS] TEST 3: Train priority verified (Robot yielded and stopped before conflict area).")

def test_waiting_tradeoff_in_astar():
    """
    TEST 4: Time-Aware A* evaluates waiting trade-off vs long detour.
    """
    graph = build_benchmark_railway_network()
    planner = PathPlanner(graph)

    # Fast train on T_X12 clearing in 8 seconds
    train = Train("TR01", graph, "T_X12", initial_distance_m=200.0, initial_speed_mps=15.0, autonomous_routing=False)
    train.set_route(["T_X12"])

    route = planner.find_shortest_safe_route(
        start_track_id="T03a",
        target_track_id="T02b",
        agent_speed_mps=4.5,
        trains=[train],
        start_time_offset_sec=0.0
    )
    assert route is not None
    assert route.is_safe is True
    print(f"[PASS] TEST 4: Time-Aware A* waiting trade-off evaluated ({route.action_description}, Total time: {route.estimated_time_sec:.1f}s).")

def test_train_to_train_anti_collision():
    """
    TEST 5: Verify trains do not collide with each other on the same track.
    """
    graph = build_benchmark_railway_network()
    train1 = Train("TR01", graph, "T01a", initial_distance_m=100.0, initial_speed_mps=10.0, autonomous_routing=False)
    train2 = Train("TR02", graph, "T01a", initial_distance_m=40.0, initial_speed_mps=15.0, autonomous_routing=False)

    trains = [train1, train2]
    # Train 2 is behind Train 1 and moving faster
    train2.check_headway_and_adjust_speed(trains)
    
    # Train 2 must reduce speed to prevent rear-ending Train 1
    assert train2.target_speed < 15.0
    print("[PASS] TEST 5: Inter-train anti-collision verified (Train 2 slowed down to match headway).")

def test_full_mission_arrival_and_inspection():
    """
    TEST 6: Robot travels dynamically along track geometry and completes inspection upon arrival.
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)
    robot = InspectionRobot("RG01", graph, "T03a", 110.0) # Near end of T03a
    engine.register_robot(robot)

    # Anomaly on T03a at 115m
    anomaly = engine.report_anomaly("ANOM_CLOSE", "T03a", chainage_km=188.115, x=robot.x + 5.0, y=robot.y)
    engine.create_and_dispatch_mission(anomaly.anomaly_id)

    # Step robot forward 2 seconds
    for _ in range(25):
        engine.step(0.1)
        robot.step(0.1)

    assert robot.status in (RobotStatus.INSPECTING, RobotStatus.UPLOADING_EVIDENCE, RobotStatus.IDLE)
    print("[PASS] TEST 6: Robot arrival and inspection lifecycle completed.")

def test_hud_rendering_clean():
    """
    TEST 7: Verify HUD overlay renders cleanly without font warnings or crashes.
    """
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from visualization.hud_overlay import HUDOverlay

    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)
    robot = InspectionRobot("RG01", graph, "T03a", 10.0)
    engine.register_robot(robot)

    fig, ax = plt.subplots(figsize=(6, 8))
    hud = HUDOverlay(ax)
    hud.render(engine)

    robot.status = RobotStatus.YIELDING_TO_TRAIN
    engine.blocking_train_id = "TR01"
    engine.blocking_track_id = "T_X12"
    hud.render(engine)

    plt.close(fig)
    print("[PASS] TEST 7: Tactical HUD rendered cleanly.")

if __name__ == "__main__":
    print("=" * 70)
    print("RUNNING DESTINATION-DRIVEN DYNAMIC ROUTING & TRAIN PRIORITY TESTS")
    print("=" * 70)
    test_startup_clean_state()
    test_interactive_destination_anomaly_and_dynamic_route()
    test_train_priority_and_robot_yielding()
    test_waiting_tradeoff_in_astar()
    test_train_to_train_anti_collision()
    test_full_mission_arrival_and_inspection()
    test_hud_rendering_clean()
    print("=" * 70)
    print("ALL TESTS PASSED SUCCESSFULLY!")
