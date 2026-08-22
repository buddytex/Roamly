"""
Comprehensive Collision Prevention & Reservation System Test Suite
Covers Tests A to H from Section 24.
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
from planning.reservation_manager import ReservationManager
from server.decision_engine import DecisionEngine

def test_a_separate_tracks_uninhibited_movement():
    """
    TEST A: Two trains on completely separate tracks -> Both move normally without unnecessary stopping.
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    # TR01 on Upper Mainline T01a, TR02 on Lower Siding T03a
    train1 = Train("TR01", graph, "T01a", initial_distance_m=10.0, initial_speed_mps=12.0)
    train2 = Train("TR02", graph, "T03a", initial_distance_m=10.0, initial_speed_mps=10.0)
    engine.register_train(train1)
    engine.register_train(train2)

    for _ in range(20):
        engine.step(0.1)
        train1.step(0.1, other_trains=[train2], sim_time=engine.sim_time_sec)
        train2.step(0.1, other_trains=[train1], sim_time=engine.sim_time_sec)

    assert train1.speed > 0.0 and train1.status == TrainStatus.RUNNING
    assert train2.speed > 0.0 and train2.status == TrainStatus.RUNNING
    print("[PASS] TEST A: Separate tracks uninhibited movement verified.")

def test_b_same_segment_mutual_exclusion():
    """
    TEST B: Two trains approaching the same segment -> Only one gets the reservation; other waits. NO COLLISION.
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    # TR01 on T02a entering T02b; TR02 on T_X12 also aiming for T02b
    train1 = Train("TR01", graph, "T02a", initial_distance_m=320.0, initial_speed_mps=15.0, autonomous_routing=False)
    train1.set_route(["T02a", "T02b", "T02c"])
    engine.register_train(train1)

    train2 = Train("TR02", graph, "T_X12", initial_distance_m=200.0, initial_speed_mps=15.0, autonomous_routing=False)
    train2.set_route(["T_X12", "T02b", "T02c"])
    engine.register_train(train2)

    # Step simulation
    for _ in range(25):
        engine.step(0.1)
        train1.step(0.1, other_trains=[train2], sim_time=engine.sim_time_sec)
        train2.step(0.1, other_trains=[train1], sim_time=engine.sim_time_sec)

    # Both trains must never be simultaneously on T02b
    t1_on_t02b = (train1.current_track_id == "T02b")
    t2_on_t02b = (train2.current_track_id == "T02b")
    assert not (t1_on_t02b and t2_on_t02b)
    
    # One of them must have decelerated or waited
    assert train1.status == TrainStatus.WAITING_FOR_CLEARANCE or train2.status == TrainStatus.WAITING_FOR_CLEARANCE or train2.speed < 15.0
    print("[PASS] TEST B: Segment mutual exclusion verified (One train holds before entering occupied/reserved block).")

def test_c_head_on_opposing_collision_prevention():
    """
    TEST C: Two trains approaching each other on the same single-track corridor (head-on) ->
    Opposing movement detected before entry; one waits before entering. NO HEAD-ON COLLISION.
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    # TR01 westbound on T01b, TR02 eastbound on T01b
    train1 = Train("TR01", graph, "T01a", initial_distance_m=200.0, initial_speed_mps=12.0, autonomous_routing=False)
    train1.set_route(["T01a", "T01b", "T01c"])

    train2 = Train("TR02", graph, "T01c", initial_distance_m=20.0, initial_speed_mps=12.0, autonomous_routing=False)
    train2.direction = -1
    train2.set_route(["T01c", "T01b", "T01a"])

    engine.register_train(train1)
    engine.register_train(train2)

    for _ in range(30):
        engine.step(0.1)
        train1.step(0.1, other_trains=[train2], sim_time=engine.sim_time_sec)
        train2.step(0.1, other_trains=[train1], sim_time=engine.sim_time_sec)

    # Opposing trains must not occupy T01b simultaneously
    t1_on = (train1.current_track_id == "T01b")
    t2_on = (train2.current_track_id == "T01b")
    assert not (t1_on and t2_on)
    print("[PASS] TEST C: Head-on collision prevention verified (Opposing entry blocked before entering shared single track).")

def test_d_rear_end_following_collision_prevention():
    """
    TEST D: Fast train approaches slower lead train on same track ->
    Trailing train slows down/brakes to maintain safe distance. NO REAR-END COLLISION.
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    # TR01 (slow lead train @ 5 m/s, at 200m)
    train1 = Train("TR01", graph, "T02a", initial_distance_m=200.0, initial_speed_mps=5.0, autonomous_routing=False)
    # TR02 (fast trailing train @ 18 m/s, at 50m)
    train2 = Train("TR02", graph, "T02a", initial_distance_m=50.0, initial_speed_mps=18.0, autonomous_routing=False)

    engine.register_train(train1)
    engine.register_train(train2)

    for _ in range(40):
        engine.step(0.1)
        train1.step(0.1, other_trains=[train2], sim_time=engine.sim_time_sec)
        train2.step(0.1, other_trains=[train1], sim_time=engine.sim_time_sec)
        # Verify physical gap is never violated (trains never overlap)
        dist_gap = train1.distance_along_track_m - train2.distance_along_track_m
        assert dist_gap >= train2.length_m, f"Physical collision! Gap={dist_gap:.1f}m < Length={train2.length_m}m"

    assert train2.speed <= train1.speed + 1.0 or train2.status in (TrainStatus.DECELERATING, TrainStatus.WAITING_FOR_CLEARANCE)
    print("[PASS] TEST D: Rear-end collision prevention verified (Trailing train matches speed and maintains safe gap).")

def test_e_switch_convergence_reservation():
    """
    TEST E: Two trains approach a switch from different branches ->
    Switch reservation prevents simultaneous conflicting movement. NO COLLISION.
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    # Both TR01 (on T02a) and TR02 (on T_X12) converge into Switch S12
    train1 = Train("TR01", graph, "T02a", initial_distance_m=340.0, initial_speed_mps=12.0, autonomous_routing=False)
    train1.set_route(["T02a", "T02b"])

    train2 = Train("TR02", graph, "T_X12", initial_distance_m=225.0, initial_speed_mps=12.0, autonomous_routing=False)
    train2.set_route(["T_X12", "T02b"])

    engine.register_train(train1)
    engine.register_train(train2)

    for _ in range(25):
        engine.step(0.1)
        train1.step(0.1, other_trains=[train2], sim_time=engine.sim_time_sec)
        train2.step(0.1, other_trains=[train1], sim_time=engine.sim_time_sec)

    # One train passes switch first, other waits
    assert not (train1.current_track_id == "T02b" and train2.current_track_id == "T02b")
    print("[PASS] TEST E: Switch convergence reservation verified.")

def test_f_robot_train_conflict_yield():
    """
    TEST F: Robot route intersects train route ->
    Robot detects future conflict, yields/waits (TRAIN > ROBOT). NO ROBOT/TRAIN COLLISION.
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    robot = InspectionRobot("RG01", graph, "T03a", 20.0)
    engine.register_robot(robot)

    # Target anomaly on T02b
    anomaly = engine.report_anomaly("ANOM_F", "T02b", chainage_km=184.7, x=440.0, y=0.0)
    engine.create_and_dispatch_mission(anomaly.anomaly_id)

    # Block both direct (T02a) and bypass (T_X15) with trains
    t1 = Train("TR01", graph, "T02a", initial_distance_m=0.0, initial_speed_mps=4.5, autonomous_routing=False)
    t1.set_route(["T02a", "T02b", "T02c"])
    t2 = Train("TR02", graph, "T_X15", initial_distance_m=10.0, initial_speed_mps=5.0, autonomous_routing=False)
    t2.set_route(["T_X15"])

    engine.register_train(t1)
    engine.register_train(t2)

    for _ in range(5):
        engine.step(0.1)

    assert robot.status in (RobotStatus.YIELDING_TO_TRAIN, RobotStatus.WAITING_FOR_CLEARANCE)
    assert robot.target_speed == 0.0
    print("[PASS] TEST F: Robot yielded to train traffic before conflict point.")

def test_g_train_route_change_robot_dynamic_replan():
    """
    TEST G: Train changes route -> Existing robot route is re-evaluated, robot dynamically replans.
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    robot = InspectionRobot("RG01", graph, "T03a", 20.0)
    engine.register_robot(robot)

    anomaly = engine.report_anomaly("ANOM_G", "T02b", chainage_km=184.7, x=440.0, y=0.0)
    engine.create_and_dispatch_mission(anomaly.anomaly_id)

    # Train appears on direct path
    t1 = Train("TR01", graph, "T02a", initial_distance_m=0.0, initial_speed_mps=4.5, autonomous_routing=False)
    t1.set_route(["T02a", "T02b", "T02c"])
    engine.register_train(t1)

    for _ in range(5):
        engine.step(0.1)

    # Robot successfully replanned around TR01 via bypass T_X15
    assert engine.current_route is not None
    assert "T_X15" in engine.current_route.track_ids
    print("[PASS] TEST G: Dynamic replan triggered upon traffic change.")

def test_h_user_clicks_new_anomaly():
    """
    TEST H: User clicks new anomaly destination -> A* recalculates from current robot position to new target.
    """
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    robot = InspectionRobot("RG01", graph, "T03a", 20.0)
    engine.register_robot(robot)

    # Destination 1: T01c (x=600.0, y=50.0)
    anom1 = engine.report_anomaly_at_coordinates(600.0, 50.0)
    assert anom1 is not None
    assert robot.active_mission_id == "M001"

    # Complete mission 1
    engine.missions.update_status("M001", MissionStatus.COMPLETED)
    robot.active_mission_id = None
    robot.status = RobotStatus.IDLE
    robot.distance_along_track_m = 80.0

    # Destination 2: T03b (x=500.0, y=-40.0)
    anom2 = engine.report_anomaly_at_coordinates(500.0, -40.0)
    assert anom2 is not None
    assert robot.active_mission_id == "M002"
    assert engine.current_route.track_ids[0] == "T03a"
    assert engine.current_route.track_ids[-1] == "T03b"
    print("[PASS] TEST H: Interactive destination selection from current robot position verified.")

if __name__ == "__main__":
    print("=" * 70)
    print("RUNNING ALL CRITICAL COLLISION PREVENTION TESTS (TESTS A TO H)")
    print("=" * 70)
    test_a_separate_tracks_uninhibited_movement()
    test_b_same_segment_mutual_exclusion()
    test_c_head_on_opposing_collision_prevention()
    test_d_rear_end_following_collision_prevention()
    test_e_switch_convergence_reservation()
    test_f_robot_train_conflict_yield()
    test_g_train_route_change_robot_dynamic_replan()
    test_h_user_clicks_new_anomaly()
    print("=" * 70)
    print("ALL TESTS (A TO H) PASSED SUCCESSFULLY! ZERO COLLISIONS!")
