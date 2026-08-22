import math
import sys
import os

# Ensure current directory is in sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from config import TrackStatus, RobotStatus, TrainStatus, AnomalyType, AnomalySeverity, MissionStatus, SafetyLevel
from world.track_builder import build_benchmark_railway_network
from entities.robot import InspectionRobot
from entities.train import Train
from entities.sensors import SensorSimulator
from planning.path_planner import PathPlanner
from planning.conflict_engine import ConflictEngine
from planning.rerouting_engine import ReroutingEngine
from server.decision_engine import DecisionEngine

def test_railway_graph_connectivity():
    """Validates railway graph structure, switches, and polyline length computations."""
    graph = build_benchmark_railway_network()
    
    assert "T01a" in graph.tracks
    assert "T02b" in graph.tracks
    assert "S12" in graph.nodes
    assert "S15" in graph.nodes
    
    t02a = graph.tracks["T02a"]
    assert t02a.length_m == 350.0
    
    pt_x, pt_y, heading = t02a.get_point_at_distance(100.0)
    assert abs(pt_x - 100.0) < 0.01
    assert abs(pt_y - 0.0) < 0.01
    assert abs(heading - 0.0) < 0.01

def test_path_planning_and_restrictions():
    """Validates A* routing and obstacle avoidance around blocked tracks."""
    graph = build_benchmark_railway_network()
    planner = PathPlanner(graph)

    # Initial route from T03a to T02b
    route = planner.find_route("T03a", "T02b", agent_speed_mps=4.0)
    assert route is not None
    assert route.track_ids == ["T03a", "T_X12", "T02b"]

    # Block direct crossover T_X12
    graph.tracks["T_X12"].status = TrackStatus.BLOCKED
    
    # Route should now find bypass via T_X15 -> T01b -> T_X16 -> T02b
    bypass_route = planner.find_route("T03a", "T02b", agent_speed_mps=4.0)
    assert bypass_route is not None
    assert "T_X12" not in bypass_route.track_ids
    assert "T_X15" in bypass_route.track_ids

def test_conflict_prediction():
    """Validates spatio-temporal conflict detection between robot and train."""
    graph = build_benchmark_railway_network()
    robot = InspectionRobot("RG01", graph, initial_track_id="T03a", initial_distance_m=50.0)
    robot.set_route(["T03a", "T_X12", "T02b"])

    train = Train("TR01", graph, initial_track_id="T02a", initial_distance_m=100.0, initial_speed_mps=15.0)
    train.set_route(["T02a", "T02b", "T02c"])

    conflict_engine = ConflictEngine(graph, safety_buffer_sec=50.0)
    warnings = conflict_engine.check_conflicts(robot, [train])

    assert len(warnings) > 0
    assert warnings[0].conflict_track_id == "T02b"
    assert warnings[0].train_id == "TR01"
    assert warnings[0].robot_id == "RG01"

def test_full_incident_response_lifecycle():
    """
    Simulates the entire 18-step incident response scenario in headless mode.
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    # 1. Register Robot
    robot = InspectionRobot("RG01", graph, initial_track_id="T03a", initial_distance_m=20.0)
    engine.register_robot(robot)

    # 2. Report Anomaly & Create Mission before train appears
    anomaly = engine.report_anomaly("ANOM_TEST", "T02b", chainage_km=184.70, x=440.0, y=0.0)
    assert anomaly.status.value == "REPORTED"

    # 3. Create & Dispatch Mission (initially clear, selects shortest direct route)
    mission = engine.create_and_dispatch_mission(anomaly.anomaly_id)
    assert mission is not None
    assert robot.status == RobotStatus.TRANSIT
    assert engine.current_route.track_ids == ["T03a", "T_X12", "T02b"]

    # Now train TR01 appears on T02a heading towards S12
    train = Train("TR01", graph, initial_track_id="T02a", initial_distance_m=0.0, initial_speed_mps=4.5, autonomous_routing=False)
    train.set_route(["T02a", "T02b", "T02c"])
    engine.register_train(train)

    # 4. Step Decision Engine -> Trigger conflict prediction & dynamic replan
    dt = 0.1
    for _ in range(5):
        engine.step(dt)
        robot.step(dt)
        train.step(dt)

    assert engine.has_replanned_for_conflict is True
    assert "T_X15" in engine.current_route.track_ids

    # 5. Fast-forward robot to target location
    robot.x = 440.0
    robot.y = 0.0
    robot.current_track_id = "T02b"
    
    # Step engine to trigger arrival
    engine.step(dt)
    assert robot.status == RobotStatus.INSPECTING

    # Step engine to advance inspection timer
    for _ in range(40):
        robot.step(0.1)

    assert robot.status == RobotStatus.UPLOADING_EVIDENCE

    # Step engine to process uploaded package and apply incident policy
    engine.step(dt)

    # Verify track blockage and incident response
    assert graph.tracks["T02b"].status == TrackStatus.BLOCKED
    assert anomaly.severity == AnomalySeverity.CRITICAL
    assert mission.status == MissionStatus.COMPLETED
    assert engine.has_rerouted_trains is True

if __name__ == "__main__":
    test_railway_graph_connectivity()
    print("[PASS] test_railway_graph_connectivity passed")
    test_path_planning_and_restrictions()
    print("[PASS] test_path_planning_and_restrictions passed")
    test_conflict_prediction()
    print("[PASS] test_conflict_prediction passed")
    test_full_incident_response_lifecycle()
    print("[PASS] test_full_incident_response_lifecycle passed")
    print("\nALL 4 INTEGRATION TEST SUITES PASSED SUCCESSFULLY!")
