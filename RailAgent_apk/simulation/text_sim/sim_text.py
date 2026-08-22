"""
RailGuard AI - Pure Text-Only Simulation Runner
Deterministic Railway Digital Twin & Traffic Interaction without GUI/Matplotlib
"""
import sys
import time
import math
from typing import Dict, List, Optional
from config import TrackStatus, RobotStatus, TrainStatus, AnomalyType, AnomalySeverity, MissionStatus
from world.track_builder import build_benchmark_railway_network
from entities.robot import InspectionRobot
from entities.train import Train
from server.decision_engine import DecisionEngine

class TextSimulationRunner:
    """
    Headless terminal simulation runner for testing the complete railway decision-making logic.
    """
    def __init__(self, deterministic: bool = True, dt: float = 0.5):
        self.dt = dt
        self.graph = build_benchmark_railway_network()
        self.engine = DecisionEngine(self.graph)
        self.deterministic = deterministic
        self.step_count = 0
        self._setup_entities()

    def _setup_entities(self):
        # 1. Deterministic Robot at Base Siding
        self.robot = InspectionRobot(
            robot_id="RG01",
            graph=self.graph,
            initial_track_id="T03a",
            initial_distance_m=10.0
        )
        self.engine.register_robot(self.robot)

        if self.deterministic:
            # Deterministic initial train setup:
            # TR01: Track T01a, Pos 80m, Speed 45 km/h (12.5 m/s), Direction +1
            self.train1 = Train("TR01", self.graph, "T01a", initial_distance_m=80.0, initial_speed_mps=12.5, autonomous_routing=True)
            self.train1.direction = 1

            # TR02: Track T02c, Pos 620m, Speed 58 km/h (16.0 m/s), Direction -1
            self.train2 = Train("TR02", self.graph, "T02c", initial_distance_m=620.0, initial_speed_mps=16.0, autonomous_routing=True)
            self.train2.direction = -1

            # TR03: Track T03b, Pos 680m, Speed 36 km/h (10.0 m/s), Direction -1
            self.train3 = Train("TR03", self.graph, "T03b", initial_distance_m=680.0, initial_speed_mps=10.0, autonomous_routing=True)
            self.train3.direction = -1

            # TR04: Track T01b, Pos 480m, Speed 50 km/h (14.0 m/s), Direction +1
            self.train4 = Train("TR04", self.graph, "T01b", initial_distance_m=480.0, initial_speed_mps=14.0, autonomous_routing=True)
            self.train4.direction = 1

            self.engine.register_train(self.train1)
            self.engine.register_train(self.train2)
            self.engine.register_train(self.train3)
            self.engine.register_train(self.train4)

    def trigger_anomaly(self, track_id: str = "T02b", position_m: float = 50.0):
        """Dispatches an anomaly at the specified track and position."""
        track = self.graph.tracks.get(track_id)
        if not track:
            return None
        px, py, _ = track.get_point_at_distance(position_m)
        chainage_km = track.chainage_start_km + (position_m / 1000.0)
        anomaly = self.engine.report_anomaly("ANOM_01", track_id, chainage_km, px, py, AnomalyType.BROKEN_RAIL)
        self.engine.create_and_dispatch_mission(anomaly.anomaly_id)
        return anomaly

    def step(self):
        """Advances the simulation by dt seconds."""
        self.step_count += 1
        self.engine.step(self.dt)
        if self.robot:
            self.robot.step(self.dt)
        for train in self.engine.trains:
            train.step(self.dt, other_trains=self.engine.trains, sim_time=self.engine.sim_time_sec)

    def print_summary(self):
        """Prints a clean, structured state snapshot to the terminal."""
        sim_sec = self.engine.sim_time_sec
        sim_mins = int(sim_sec // 60)
        sim_rem_sec = int(sim_sec % 60)
        time_str = f"{sim_mins:02d}:{sim_rem_sec:02d} ({sim_sec:.1f}s)"

        print("\n" + "=" * 65)
        print(f" TIME: {time_str} | STEP: {self.step_count}")
        print("=" * 65)

        print("\nTRAINS (4 ACTIVE):")
        for t in self.engine.trains:
            t_tel = t.get_telemetry()
            dir_str = "->" if t.direction > 0 else "<-"
            next_str = f"-> {t_tel.next_track_id}" if t_tel.next_track_id else "[End]"
            state_str = t.status.value
            if t.status == TrainStatus.WAITING_FOR_CLEARANCE:
                state_str = f"WAITING ({t_tel.wait_reason or 'Clearance'})"
            elif t.status == TrainStatus.DECELERATING:
                state_str = "SLOWING"
            print(f"  {t.agent_id} | Track: {t.current_track_id:6s} ({t.distance_along_track_m:5.1f}m {dir_str}) | {t.speed*3.6:4.1f} km/h | {state_str} {next_str}")

        print("\nROBOT (RG01):")
        if self.robot:
            rob_dest = "--"
            if self.robot.active_mission_id:
                m = self.engine.missions.get_mission(self.robot.active_mission_id)
                if m:
                    rob_dest = f"{m.anomaly_id} ({m.target_track_id} @ KM {m.target_chainage_km:.2f})"
            route_str = " -> ".join(self.engine.current_route.track_ids) if self.engine.current_route else "IDLE"
            safety_str = "SAFE" if (not self.engine.current_route or self.engine.current_route.is_safe) else "CONFLICT PENDING"
            print(f"  Track         : {self.robot.current_track_id} @ {self.robot.distance_along_track_m:.1f}m")
            print(f"  Speed         : {self.robot.speed*3.6:.1f} km/h (Target: {self.robot.target_speed*3.6:.1f} km/h)")
            print(f"  Destination   : {rob_dest}")
            print(f"  Status        : {self.robot.status.value}")
            print(f"  Current Action: {self.engine.current_action}")
            print(f"  Current Route : {route_str}")
            print(f"  Safety Status : {safety_str}")

        print("=" * 65)

    def run(self, max_seconds: float = 60.0, print_interval_sec: float = 5.0, anomaly_at_sec: Optional[float] = 4.0):
        """Runs the simulation loop until max_seconds or mission completion."""
        print("=" * 75)
        print(" RAILGUARD AI - PURE TEXT-ONLY SIMULATION RUNNER")
        print(" Deterministic Headless Railway Verification Engine")
        print("=" * 75)

        self.print_summary()
        last_print = 0.0

        while self.engine.sim_time_sec <= max_seconds:
            # Trigger anomaly if scheduled
            if anomaly_at_sec is not None and abs(self.engine.sim_time_sec - anomaly_at_sec) < (self.dt / 2.0):
                print(f"\n[{self.engine.sim_time_sec:05.1f}s] [USER] Destination anomaly selected: ANOM_01 on Track T02b @ 540m")
                self.trigger_anomaly(track_id="T02b", position_m=540.0)

            self.step()

            # Print state periodic snapshot
            if (self.engine.sim_time_sec - last_print) >= print_interval_sec:
                self.print_summary()
                last_print = self.engine.sim_time_sec

            # Check mission completion
            if self.robot and self.robot.active_mission_id:
                m = self.engine.missions.get_mission(self.robot.active_mission_id)
                if m and m.status == MissionStatus.COMPLETED:
                    print(f"\n[{self.engine.sim_time_sec:05.1f}s] [MISSION] MISSION COMPLETED! Robot successfully inspected destination anomaly.")
                    self.print_summary()
                    break

def main():
    runner = TextSimulationRunner(deterministic=True, dt=0.5)
    runner.run(max_seconds=90.0, print_interval_sec=10.0, anomaly_at_sec=2.0)

if __name__ == "__main__":
    main()
