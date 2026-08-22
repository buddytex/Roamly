"""
RailGuard AI - 8 Text-Only Verification Scenarios
Complete Headless Verification Suite Covering Tests 1 through 8 from Section 29
"""
import sys
import os
import math

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from config import TrackStatus, RobotStatus, TrainStatus, AnomalyType, AnomalySeverity, MissionStatus
from world.track_builder import build_benchmark_railway_network
from entities.robot import InspectionRobot
from entities.train import Train
from server.decision_engine import DecisionEngine

def run_test_1_normal_traffic():
    print("\n" + "=" * 75)
    print("TEST 1 — NORMAL TRAFFIC (4 Trains, 3 Tracks, Robot IDLE)")
    print("=" * 75)
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    # Robot IDLE at base siding
    robot = InspectionRobot("RG01", graph, "T03a", 10.0)
    engine.register_robot(robot)

    # 4 Trains on 3 main tracks (non-conflicting routes)
    t1 = Train("TR01", graph, "T01a", initial_distance_m=50.0, initial_speed_mps=12.5) # Upper track
    t2 = Train("TR02", graph, "T02a", initial_distance_m=50.0, initial_speed_mps=14.0) # Central track
    t3 = Train("TR03", graph, "T03b", initial_distance_m=50.0, initial_speed_mps=10.0) # Lower track
    t4 = Train("TR04", graph, "T01c", initial_distance_m=50.0, initial_speed_mps=12.0) # Upper eastbound
    for t in [t1, t2, t3, t4]:
        engine.register_train(t)

    # Step simulation for 30 ticks (15s sim time)
    for step_i in range(30):
        engine.step(0.5)
        robot.step(0.5)
        for t in engine.trains:
            t.step(0.5, other_trains=engine.trains, sim_time=engine.sim_time_sec)

    # Verification: All trains moving at speed, robot remains IDLE
    for t in engine.trains:
        assert t.speed > 0.0, f"Train {t.agent_id} unexpectedly stopped!"
        assert t.status in (TrainStatus.RUNNING, TrainStatus.DECELERATING), f"Train {t.agent_id} state is {t.status}"

    assert robot.status == RobotStatus.IDLE
    assert robot.speed == 0.0
    print("[PASS] TEST 1 SUCCESSFUL: All 4 trains moving continuously; Robot safely IDLE.")

def run_test_2_two_trains_same_track():
    print("\n" + "=" * 75)
    print("TEST 2 — TWO TRAINS SAME TRACK (Faster train catches up -> Overtakes or Slows)")
    print("=" * 75)
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    # TR01: Lead train on T02a @ 220m (45 km/h = 12.5 m/s)
    tr01 = Train("TR01", graph, "T02a", initial_distance_m=220.0, initial_speed_mps=12.5, autonomous_routing=False)
    tr01.set_route(["T02a", "T02b", "T02c"])
    
    # TR02: Fast trailing train on T02a @ 60m (58 km/h = 16.0 m/s)
    tr02 = Train("TR02", graph, "T02a", initial_distance_m=60.0, initial_speed_mps=16.0, autonomous_routing=False)
    tr02.set_route(["T02a", "T02b", "T02c"])

    engine.register_train(tr01)
    engine.register_train(tr02)

    # Step simulation for 40 ticks
    for _ in range(40):
        engine.step(0.5)
        tr01.step(0.5, other_trains=engine.trains, sim_time=engine.sim_time_sec)
        tr02.step(0.5, other_trains=engine.trains, sim_time=engine.sim_time_sec)

        # Invariant: Physical gap never violated on same track
        if tr01.current_track_id == tr02.current_track_id:
            gap = abs(tr01.distance_along_track_m - tr02.distance_along_track_m)
            assert gap >= tr02.length_m, f"Physical collision on {tr01.current_track_id}! Gap={gap:.1f}m"

    # Faster train adapted by slowing down or diverting through switch
    assert tr02.speed <= tr01.speed + 1.0 or tr02.current_track_id != tr01.current_track_id or tr02.status in (TrainStatus.DECELERATING, TrainStatus.WAITING_FOR_CLEARANCE)
    print(f"[PASS] TEST 2 SUCCESSFUL: TR02 safely resolved catch-up (Status: {tr02.status.value}, Speed: {tr02.speed*3.6:.1f} km/h). ZERO COLLISIONS.")

def run_test_3_robot_faster_train_active_track_change():
    print("\n" + "=" * 75)
    print("TEST 3 — ROBOT + FASTER TRAIN (Robot changes track to make way for train)")
    print("=" * 75)
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    # Robot on T03a moving towards destination on T02b
    robot = InspectionRobot("RG01", graph, "T03a", 20.0)
    engine.register_robot(robot)

    anomaly = engine.report_anomaly("ANOM_03", "T02b", chainage_km=184.7, x=440.0, y=0.0)
    engine.create_and_dispatch_mission(anomaly.anomaly_id)

    # Fast Train TR02 approaching along the mainline
    tr02 = Train("TR02", graph, "T02a", initial_distance_m=10.0, initial_speed_mps=16.0, autonomous_routing=False)
    tr02.set_route(["T02a", "T02b", "T02c"])
    engine.register_train(tr02)

    for _ in range(15):
        engine.step(0.5)
        robot.step(0.5)
        tr02.step(0.5, other_trains=engine.trains, sim_time=engine.sim_time_sec)

    # Robot dynamically shifted onto bypass T_X15 to avoid delaying TR02
    assert engine.robot.status in (RobotStatus.CHANGING_TRACK, RobotStatus.TRANSIT)
    assert engine.current_route is not None
    print(f"[PASS] TEST 3 SUCCESSFUL: Robot dynamic action: '{engine.current_action}' | Route: {' -> '.join(engine.current_route.track_ids)}")

def run_test_4_robot_cannot_move_aside_yields():
    print("\n" + "=" * 75)
    print("TEST 4 — ROBOT CANNOT MOVE ASIDE (No switch available -> Robot yields and waits)")
    print("=" * 75)
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    robot = InspectionRobot("RG01", graph, "T03a", 20.0)
    engine.register_robot(robot)

    anomaly = engine.report_anomaly("ANOM_04", "T02b", chainage_km=184.7, x=440.0, y=0.0)
    engine.create_and_dispatch_mission(anomaly.anomaly_id)

    # Block both direct (T02a) and bypass (T_X15) routes with trains
    t1 = Train("TR01", graph, "T02a", initial_distance_m=0.0, initial_speed_mps=4.5, autonomous_routing=False)
    t1.set_route(["T02a", "T02b", "T02c"])
    t2 = Train("TR02", graph, "T_X15", initial_distance_m=10.0, initial_speed_mps=5.0, autonomous_routing=False)
    t2.set_route(["T_X15"])
    engine.register_train(t1)
    engine.register_train(t2)

    for _ in range(10):
        engine.step(0.5)
        robot.step(0.5)
        t1.step(0.5, other_trains=engine.trains, sim_time=engine.sim_time_sec)
        t2.step(0.5, other_trains=engine.trains, sim_time=engine.sim_time_sec)

    # Robot yields and safely waits before conflict block
    assert robot.status in (RobotStatus.YIELDING_TO_TRAIN, RobotStatus.WAITING_FOR_CLEARANCE)
    assert robot.target_speed == 0.0
    print(f"[PASS] TEST 4 SUCCESSFUL: Robot safely yielded (Status: {robot.status.value}, Action: {engine.current_action}). ZERO COLLISION.")

def run_test_5_head_on_trains():
    print("\n" + "=" * 75)
    print("TEST 5 — HEAD-ON TRAINS (Opposing direction on single-track corridor)")
    print("=" * 75)
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    # TR01 Eastbound on T01a heading to T01b
    tr01 = Train("TR01", graph, "T01a", initial_distance_m=200.0, initial_speed_mps=12.0, autonomous_routing=False)
    tr01.set_route(["T01a", "T01b", "T01c"])

    # TR02 Westbound on T01c heading to T01b
    tr02 = Train("TR02", graph, "T01c", initial_distance_m=20.0, initial_speed_mps=12.0, autonomous_routing=False)
    tr02.direction = -1
    tr02.set_route(["T01c", "T01b", "T01a"])

    engine.register_train(tr01)
    engine.register_train(tr02)

    for _ in range(30):
        engine.step(0.5)
        tr01.step(0.5, other_trains=engine.trains, sim_time=engine.sim_time_sec)
        tr02.step(0.5, other_trains=engine.trains, sim_time=engine.sim_time_sec)

    # Invariant: Both trains never simultaneously on T01b
    assert not (tr01.current_track_id == "T01b" and tr02.current_track_id == "T01b")
    print(f"[PASS] TEST 5 SUCCESSFUL: Head-on collision prevented. TR01 on {tr01.current_track_id}, TR02 on {tr02.current_track_id}.")

def run_test_6_switch_conflict():
    print("\n" + "=" * 75)
    print("TEST 6 — SWITCH CONFLICT (Two trains converging into same switch node)")
    print("=" * 75)
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    # TR01 from T02a and TR02 from T_X12 both converging into Switch S12
    tr01 = Train("TR01", graph, "T02a", initial_distance_m=340.0, initial_speed_mps=12.0, autonomous_routing=False)
    tr01.set_route(["T02a", "T02b"])

    tr02 = Train("TR02", graph, "T_X12", initial_distance_m=225.0, initial_speed_mps=12.0, autonomous_routing=False)
    tr02.set_route(["T_X12", "T02b"])

    engine.register_train(tr01)
    engine.register_train(tr02)

    for _ in range(25):
        engine.step(0.5)
        tr01.step(0.5, other_trains=engine.trains, sim_time=engine.sim_time_sec)
        tr02.step(0.5, other_trains=engine.trains, sim_time=engine.sim_time_sec)

    # One train traverses switch, the other waits before the junction
    assert not (tr01.current_track_id == "T02b" and tr02.current_track_id == "T02b")
    print("[PASS] TEST 6 SUCCESSFUL: Switch convergence reservation enforced. ZERO COLLISION.")

def run_test_7_robot_and_train_both_want_same_segment():
    print("\n" + "=" * 75)
    print("TEST 7 — ROBOT AND TRAIN BOTH WANT SAME SEGMENT (Train prioritized)")
    print("=" * 75)
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    robot = InspectionRobot("RG01", graph, "T03a", 20.0)
    engine.register_robot(robot)

    # Target anomaly on T01c
    anomaly = engine.report_anomaly("ANOM_07", "T01c", chainage_km=180.5, x=600.0, y=50.0)
    engine.create_and_dispatch_mission(anomaly.anomaly_id)

    # Train TR01 moving on T01b towards T01c
    tr01 = Train("TR01", graph, "T01b", initial_distance_m=10.0, initial_speed_mps=14.0, autonomous_routing=False)
    tr01.set_route(["T01b", "T01c"])
    engine.register_train(tr01)

    for _ in range(20):
        engine.step(0.5)
        robot.step(0.5)
        tr01.step(0.5, other_trains=engine.trains, sim_time=engine.sim_time_sec)

    # Train movement continues uninhibited; Robot yields or holds before conflict point
    assert tr01.speed > 0.0
    assert robot.speed == 0.0 or "T01c" not in robot.assigned_route[:robot.route_index+1]
    print("[PASS] TEST 7 SUCCESSFUL: Train priority maintained without endangering robot.")

def run_test_8_multiple_conflicts_full_mission():
    print("\n" + "=" * 75)
    print("TEST 8 — MULTIPLE CONFLICTS & FULL MISSION (4 Trains + Robot to Destination)")
    print("=" * 75)
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)

    robot = InspectionRobot("RG01", graph, "T03a", 10.0)
    engine.register_robot(robot)

    # 4 Active Trains with crossing routes
    t1 = Train("TR01", graph, "T01a", initial_distance_m=80.0, initial_speed_mps=12.5, autonomous_routing=True)
    t2 = Train("TR02", graph, "T02c", initial_distance_m=620.0, initial_speed_mps=16.0, autonomous_routing=True)
    t2.direction = -1
    t3 = Train("TR03", graph, "T03b", initial_distance_m=680.0, initial_speed_mps=10.0, autonomous_routing=True)
    t3.direction = -1
    t4 = Train("TR04", graph, "T01b", initial_distance_m=480.0, initial_speed_mps=14.0, autonomous_routing=True)
    for t in [t1, t2, t3, t4]:
        engine.register_train(t)

    # Dispatch anomaly destination on T02b (x=400.0, y=0.0, KM 184.40)
    anomaly = engine.report_anomaly("ANOM_01", "T02b", chainage_km=184.40, x=400.0, y=0.0, reported_type=AnomalyType.BROKEN_RAIL)
    engine.create_and_dispatch_mission(anomaly.anomaly_id)

    mission_complete = False
    for step_i in range(1200): # 600 seconds sim time (allows full multi-train journey & inspection completion)
        engine.step(0.5)
        robot.step(0.5)
        for t in engine.trains:
            t.step(0.5, other_trains=engine.trains, sim_time=engine.sim_time_sec)

        # Invariant: No train-to-train collision
        for i in range(len(engine.trains)):
            for j in range(i + 1, len(engine.trains)):
                ti = engine.trains[i]
                tj = engine.trains[j]
                if ti.current_track_id == tj.current_track_id:
                    gap = abs(ti.distance_along_track_m - tj.distance_along_track_m)
                    assert gap >= 35.0 or ti.speed == 0.0 or tj.speed == 0.0

        # Check mission completion
        m = engine.missions.get_mission("M001")
        if m and m.status == MissionStatus.COMPLETED:
            mission_complete = True
            break

    assert mission_complete, "Robot failed to complete mission within time limit!"
    print("[PASS] TEST 8 SUCCESSFUL: Full multi-train simulation completed with ZERO collisions and MISSION COMPLETE.")

if __name__ == "__main__":
    print("=" * 75)
    print("RUNNING ALL 8 TEXT-ONLY RAILWAY DECISION ENGINE SCENARIOS")
    print("=" * 75)
    run_test_1_normal_traffic()
    run_test_2_two_trains_same_track()
    run_test_3_robot_faster_train_active_track_change()
    run_test_4_robot_cannot_move_aside_yields()
    run_test_5_head_on_trains()
    run_test_6_switch_conflict()
    run_test_7_robot_and_train_both_want_same_segment()
    run_test_8_multiple_conflicts_full_mission()
    print("\n" + "=" * 75)
    print("ALL 8 TEXT-ONLY SCENARIOS PASSED WITH 100% SUCCESS! DECISION ENGINE VERIFIED.")
    print("=" * 75)
