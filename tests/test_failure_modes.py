"""
Robustness & Failure Mode Verification Test Suite for RailGuard AI
"""
import sys
import os
import time
import urllib.request
import json
import numpy as np

# Ensure root in sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from config import TrackStatus, RobotStatus, SafetyLevel, AnomalyType, AnomalySeverity
from world.track_builder import build_benchmark_railway_network
from entities.robot import InspectionRobot
from entities.train import Train
from server.decision_engine import DecisionEngine
from server.api_server import RailGuardAPIServer
from localization.ekf_fusion import RailwayEKFFusion
from perception.heatmap_decoder import RailHeatmapDecoder

def test_rest_api_server():
    """Tests embedded Raspberry Pi REST API server communication."""
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)
    robot = InspectionRobot("RG01", graph, "T03a", 10.0)
    engine.register_robot(robot)

    # Start API server on local test port 8888
    api = RailGuardAPIServer(engine, port=8888)
    api.start()
    time.sleep(0.3)

    try:
        # 1. Test System Status GET
        req = urllib.request.urlopen("http://127.0.0.1:8888/api/v1/system/status")
        assert req.status == 200
        res = json.loads(req.read().decode('utf-8'))
        assert res["status"] == "ONLINE"
        assert res["safety_level"] == "NORMAL"

        # 2. Test Anomaly Reporting POST
        post_data = json.dumps({
            "anomaly_id": "ANOM_API_01",
            "track_id": "T02b",
            "chainage_km": 184.7,
            "x": 440.0,
            "y": 0.0,
            "reported_type": "BROKEN_RAIL"
        }).encode('utf-8')

        req2 = urllib.request.Request("http://127.0.0.1:8888/api/v1/anomalies/report", data=post_data, headers={"Content-Type": "application/json"})
        resp2 = urllib.request.urlopen(req2)
        assert resp2.status == 201
        res2 = json.loads(resp2.read().decode('utf-8'))
        assert res2["anomaly_id"] == "ANOM_API_01"
        assert res2["status"] == "DISPATCHED"

        # 3. Test Active Mission Query GET
        req3 = urllib.request.urlopen("http://127.0.0.1:8888/api/v1/missions/active")
        assert req3.status == 200
        res3 = json.loads(req3.read().decode('utf-8'))
        assert res3["anomaly_id"] == "ANOM_API_01"

    finally:
        api.stop()

def test_ekf_sensor_fusion_convergence():
    """Tests EKF localization filtering out Gaussian GPS noise."""
    np.random.seed(42)
    graph = build_benchmark_railway_network()
    ekf = RailwayEKFFusion(graph, initial_track_id="T02a", init_x=0.0, init_y=0.0)

    # Simulate vehicle moving along track T02a at 4.0 m/s for 10 seconds with noisy GPS
    dt = 0.1
    true_x = 0.0
    for _ in range(50):
        true_x += 4.0 * dt
        ekf.predict(dt, accel_mps2=0.0, yaw_rate_rads=0.0)
        
        # Ingest noisy GPS measurement (noise std = 1.5m)
        gps_meas_x = true_x + np.random.normal(0.0, 1.5)
        gps_meas_y = 0.0 + np.random.normal(0.0, 1.5)
        ekf.update_gps(gps_meas_x, gps_meas_y)
        ekf.apply_track_rail_constraint()

    state = ekf.get_state()
    # Lateral Y position on T02a should remain snapped close to y=0.0
    assert abs(state.y - 0.0) < 0.35
    # Longitudinal X position should track true_x closely
    assert abs(state.x - true_x) < 1.5

def test_rail_perception_fracture_detection():
    """Tests Model V5 heatmap decoder identifying broken rail fracture gap."""
    decoder = RailHeatmapDecoder(threshold=0.40)
    synthetic_hm = decoder.generate_synthetic_broken_rail_heatmap()
    
    assert synthetic_hm.shape == (2, 288, 512)
    decoded = decoder.decode(synthetic_hm)

    assert decoded.is_defect_detected is True
    assert decoded.defect_location_px is not None

def test_communication_loss_fail_safe():
    """Tests that a loss of telemetry heartbeat triggers EMERGENCY_STOP."""
    graph = build_benchmark_railway_network()
    engine = DecisionEngine(graph)
    robot = InspectionRobot("RG01", graph, "T03a", 20.0)
    engine.register_robot(robot)
    robot.target_speed = 4.0
    robot.status = RobotStatus.TRANSIT

    # Artificially set last heartbeat to 5 seconds ago (timeout threshold is 3.0s)
    engine.safety.last_heartbeat_time = time.time() - 5.0

    # Evaluate safety supervisor
    safety_level = engine.safety.evaluate_safety(robot)
    assert safety_level == SafetyLevel.EMERGENCY
    assert robot.status == RobotStatus.EMERGENCY_STOP
    assert robot.target_speed == 0.0

if __name__ == "__main__":
    test_rest_api_server()
    print("[PASS] test_rest_api_server passed")
    test_ekf_sensor_fusion_convergence()
    print("[PASS] test_ekf_sensor_fusion_convergence passed")
    test_rail_perception_fracture_detection()
    print("[PASS] test_rail_perception_fracture_detection passed")
    test_communication_loss_fail_safe()
    print("[PASS] test_communication_loss_fail_safe passed")
    print("\nALL FAILURE MODE & ROBUSTNESS TESTS PASSED SUCCESSFULLY!")
