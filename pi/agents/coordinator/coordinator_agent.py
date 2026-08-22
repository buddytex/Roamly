"""
Central Decision-Making Server & Orchestration Engine
Manages Destination-Driven Robot Missions, Train Priority, and Multi-Train Traffic Interaction
"""
import time
import math
from typing import Dict, List, Optional, Set, Tuple
from config import (
    TrackStatus, SwitchState, RobotStatus, TrainStatus,
    AnomalyType, AnomalySeverity, AnomalyStatus, MissionStatus, SafetyLevel,
    ROBOT_ARRIVAL_THRESHOLD_M
)
from world.graph import RailwayGraph, TrackSegment
from world.anomaly_manager import Anomaly, AnomalyManager
from world.switch_controller import SwitchController
from entities.robot import InspectionRobot
from entities.train import Train
from planning.path_planner import PathPlanner, RoutePlan
from planning.conflict_engine import ConflictEngine, ConflictWarning
from planning.rerouting_engine import ReroutingEngine
from planning.reservation_manager import ReservationManager
from missions.mission_manager import Mission, MissionManager
from missions.inspection_collector import InspectionPackage, InspectionCollector
from server.logger import DecisionLogger
from server.safety_layer import SafetySupervisor

class DecisionEngine:
    """
    Central Raspberry Pi Autonomous Railway Management Brain.
    Manages Destination-driven robot missions, train priority, and time-aware A* routing.
    """
    def __init__(self, graph: RailwayGraph):
        self.graph = graph
        self.logger = DecisionLogger()
        self.anomalies = AnomalyManager()
        self.missions = MissionManager()
        self.switches = SwitchController(graph)
        self.reservations = ReservationManager(graph)
        self.planner = PathPlanner(graph)
        self.conflict_engine = ConflictEngine(graph)
        self.rerouter = ReroutingEngine(graph, self.planner)
        self.collector = InspectionCollector()
        self.safety = SafetySupervisor()

        self.robot: Optional[InspectionRobot] = None
        self.trains: List[Train] = []
        
        # State tracking for UI visualization and dynamic routing
        self.active_conflicts: List[ConflictWarning] = []
        self.old_route: Optional[RoutePlan] = None
        self.current_route: Optional[RoutePlan] = None
        self.has_replanned_for_conflict: bool = False
        self.has_rerouted_trains: bool = False
        self.sim_time_sec: float = 0.0
        self.anomaly_counter: int = 1
        self.current_action: str = "PROCEEDING"

        # Train Priority & Conflict Clearance Attributes
        self.blocking_train_id: Optional[str] = None
        self.blocking_track_id: Optional[str] = None
        self.blocking_conflict_eta: float = 0.0
        self.blocking_clearance_time: float = 0.0
        self.last_replan_eval_time: float = 0.0

    def register_robot(self, robot: InspectionRobot):
        self.robot = robot
        self.logger.log("SYSTEM", f"Inspection Robot {robot.agent_id} registered on track {robot.current_track_id}")

    def register_train(self, train: Train):
        train.set_reservation_manager(self.reservations)
        self.trains.append(train)
        self.logger.log("SYSTEM", f"Train {train.agent_id} registered on track {train.current_track_id} (Speed: {train.max_speed_mps*3.6:.0f} km/h)")

    def report_anomaly(
        self,
        anomaly_id: str,
        track_id: str,
        chainage_km: float,
        x: float,
        y: float,
        reported_type: AnomalyType = AnomalyType.UNKNOWN
    ) -> Anomaly:
        """Central ingestion of an external anomaly notification."""
        anomaly = self.anomalies.report_anomaly(
            anomaly_id=anomaly_id,
            track_id=track_id,
            chainage_km=chainage_km,
            x=x,
            y=y,
            reported_type=reported_type
        )
        self.logger.log("ANOMALY", f"Anomaly {anomaly_id} reported at Track {track_id} (KM {chainage_km:.2f})")
        return anomaly

    def report_anomaly_at_coordinates(self, x: float, y: float, max_dist_m: float = 25.0) -> Optional[Anomaly]:
        """
        Snaps clicked coordinate (x, y) to the nearest track segment on the railway network.
        Sets this anomaly as the robot's destination and triggers dynamic time-aware A* planning.
        """
        # Guard against creating multiple active missions for RG01
        if self.robot and self.robot.active_mission_id:
            active_m = self.missions.get_mission(self.robot.active_mission_id)
            if active_m and active_m.status not in (MissionStatus.COMPLETED, MissionStatus.FAILED, MissionStatus.ABORTED):
                self.logger.log("MISSION", f"Mission {active_m.mission_id} currently ACTIVE on Track {active_m.target_track_id}. Ignoring additional anomaly dispatch.")
                return None

        best_track_id = None
        best_proj = None
        best_dist_sq = float('inf')

        for tid, track in self.graph.tracks.items():
            px, py, d_along = track.project_point(x, y)
            d_sq = (x - px) ** 2 + (y - py) ** 2
            if d_sq < best_dist_sq:
                best_dist_sq = d_sq
                best_track_id = tid
                best_proj = (px, py, d_along)

        if best_dist_sq > max_dist_m ** 2 or not best_track_id:
            return None

        track = self.graph.tracks[best_track_id]
        px, py, d_along = best_proj
        chainage_km = track.chainage_start_km + (d_along / 1000.0)
        
        anom_id = f"ANOM_{self.anomaly_counter:02d}"
        self.anomaly_counter += 1

        anomaly = self.report_anomaly(
            anomaly_id=anom_id,
            track_id=best_track_id,
            chainage_km=chainage_km,
            x=px,
            y=py,
            reported_type=AnomalyType.BROKEN_RAIL
        )

        # Immediately dispatch destination-driven mission
        self.create_and_dispatch_mission(anomaly.anomaly_id)
        return anomaly

    def create_and_dispatch_mission(self, anomaly_id: str) -> Optional[Mission]:
        """
        Creates a destination-driven mission to reach the anomaly.
        Dynamically plans the initial best safe route with Time-Aware A*.
        """
        anomaly = self.anomalies.get_anomaly(anomaly_id)
        if not anomaly or not self.robot:
            return None

        # Guard: Single active mission enforcement
        if self.robot.active_mission_id:
            active_m = self.missions.get_mission(self.robot.active_mission_id)
            if active_m and active_m.status not in (MissionStatus.COMPLETED, MissionStatus.FAILED, MissionStatus.ABORTED):
                self.logger.log("MISSION", f"Cannot dispatch: Robot {self.robot.agent_id} already has active Mission {active_m.mission_id}.")
                return None

        self.has_replanned_for_conflict = False
        self.old_route = None
        self.blocking_train_id = None
        self.blocking_track_id = None
        self.active_conflicts = []
        self.current_action = "PROCEEDING"

        # 1. Compute optimal time-aware shortest safe route clipped to anomaly coordinates
        route = self.planner.find_shortest_safe_route(
            start_track_id=self.robot.current_track_id,
            target_track_id=anomaly.track_id,
            agent_speed_mps=self.robot.max_speed_mps,
            trains=self.trains,
            start_time_offset_sec=self.sim_time_sec,
            target_coords=(anomaly.x, anomaly.y)
        )

        if not route:
            self.logger.log("MISSION", f"Failed to compute route for Anomaly {anomaly_id}")
            return None

        self.current_route = route
        self.robot.set_route(route.track_ids)
        self.robot.target_speed = self.robot.max_speed_mps
        self.robot.status = RobotStatus.TRANSIT

        mission = self.missions.create_mission(
            anomaly=anomaly,
            assigned_robot_id=self.robot.agent_id,
            initial_route=route.track_ids
        )
        self.robot.active_mission_id = mission.mission_id
        anomaly.status = AnomalyStatus.DISPATCHED

        route_str = " -> ".join(route.track_ids)
        safety_desc = "SAFE" if route.is_safe else "CAUTION (Conflict on path)"
        self.logger.log("MISSION", f"Mission {mission.mission_id} dispatched for Robot {self.robot.agent_id} | Route: {route_str} [{safety_desc}]")
        return mission

    def step(self, dt: float):
        """
        Main decision loop executed every simulation tick.
        """
        self.sim_time_sec += dt
        if not self.robot:
            return

        # 1. Update safety supervisor heartbeat
        self.safety.register_heartbeat()
        self.safety.evaluate_safety(self.robot)

        # 2. Continuous conflict monitoring while robot is in transit or changing tracks
        if self.robot.status in (RobotStatus.TRANSIT, RobotStatus.CHANGING_TRACK) and self.robot.active_mission_id:
            conflicts = self.conflict_engine.check_conflicts(self.robot, self.trains, self.sim_time_sec)
            self.active_conflicts = conflicts

            # Train priority resolution: Robot yields / changes track
            if conflicts and not self.has_replanned_for_conflict:
                primary_conf = conflicts[0]
                self._resolve_conflict(primary_conf)
            elif not conflicts and self.has_replanned_for_conflict and not self.blocking_train_id:
                self.has_replanned_for_conflict = False
                self.current_action = "PROCEEDING"
                if self.robot.status == RobotStatus.CHANGING_TRACK:
                    self.robot.status = RobotStatus.TRANSIT

        # 3. Handling YIELDING_TO_TRAIN state (Decelerating to a stop)
        elif self.robot.status == RobotStatus.YIELDING_TO_TRAIN and self.robot.active_mission_id:
            self.robot.target_speed = 0.0
            if self.robot.speed == 0.0:
                self.robot.status = RobotStatus.WAITING_FOR_CLEARANCE

        # 4. Intelligent Clearance Monitoring while Robot is Holding
        elif self.robot.status == RobotStatus.WAITING_FOR_CLEARANCE and self.robot.active_mission_id:
            self.robot.target_speed = 0.0
            self.robot.speed = 0.0

            # Only re-evaluate clearance when the blocking train has physically cleared the conflict area
            is_cleared = (self.sim_time_sec >= self.blocking_clearance_time) or (self.blocking_train_id is None)
            
            # Or if blocking train changed route and is no longer heading towards blocking track
            if not is_cleared and self.blocking_train_id:
                for tr in self.trains:
                    if tr.agent_id == self.blocking_train_id:
                        future_route = tr.assigned_route[tr.route_index:]
                        if tr.current_track_id != self.blocking_track_id and self.blocking_track_id not in future_route:
                            is_cleared = True
                            break

            if is_cleared and (self.sim_time_sec - self.last_replan_eval_time >= 0.5):
                self.last_replan_eval_time = self.sim_time_sec
                mission = self.missions.get_mission(self.robot.active_mission_id)
                if mission:
                    test_route = self.planner.find_shortest_safe_route(
                        start_track_id=self.robot.current_track_id,
                        target_track_id=mission.target_track_id,
                        agent_speed_mps=self.robot.max_speed_mps,
                        trains=self.trains,
                        start_time_offset_sec=self.sim_time_sec,
                        target_coords=(mission.target_x, mission.target_y)
                    )
                    if test_route and test_route.is_safe:
                        # Conflict has cleared!
                        b_train = self.blocking_train_id or "TRAIN"
                        b_track = self.blocking_track_id or "TRACK"
                        self.logger.log("SAFETY", f"Conflict with {b_train} on {b_track} cleared")
                        self.logger.log("PLANNING", "Recalculating dynamic route...")
                        
                        self.robot.status = RobotStatus.REPLANNING
                        self.current_route = test_route
                        self.robot.set_route(test_route.track_ids)
                        self.robot.target_speed = self.robot.max_speed_mps
                        self.robot.status = RobotStatus.TRANSIT
                        self.has_replanned_for_conflict = False
                        self.blocking_train_id = None
                        self.blocking_track_id = None
                        self.active_conflicts = []
                        self.current_action = "PROCEEDING"
                        
                        route_str = " -> ".join(test_route.track_ids)
                        self.logger.log("ROUTE", f"New shortest safe route selected: {route_str}")
                        self.logger.log("ROBOT", "Resuming transit")

        # 5. Check for arrival at target anomaly location
        if self.robot.status in (RobotStatus.TRANSIT, RobotStatus.CHANGING_TRACK) and self.robot.active_mission_id:
            mission = self.missions.get_mission(self.robot.active_mission_id)
            if mission:
                dist_to_target = math.hypot(self.robot.x - mission.target_x, self.robot.y - mission.target_y)
                if dist_to_target <= ROBOT_ARRIVAL_THRESHOLD_M:
                    self._handle_robot_arrival(mission)

        # 6. Handle inspection upload & classification
        if self.robot.status == RobotStatus.UPLOADING_EVIDENCE and self.robot.active_mission_id:
            mission = self.missions.get_mission(self.robot.active_mission_id)
            if mission:
                self._handle_inspection_analysis(mission)

    def force_recalculate_route(self):
        """Forces immediate dynamic route recalculation for active destination."""
        if not self.robot or not self.robot.active_mission_id:
            return
        mission = self.missions.get_mission(self.robot.active_mission_id)
        if not mission:
            return

        self.logger.log("PLANNING", "Manual trigger: Force recalculating dynamic safe route...")
        new_route = self.planner.find_shortest_safe_route(
            start_track_id=self.robot.current_track_id,
            target_track_id=mission.target_track_id,
            agent_speed_mps=self.robot.max_speed_mps,
            trains=self.trains,
            start_time_offset_sec=self.sim_time_sec,
            target_coords=(mission.target_x, mission.target_y)
        )

        if new_route:
            self.old_route = self.current_route
            self.current_route = new_route
            self.robot.set_route(new_route.track_ids)
            self.robot.target_speed = self.robot.max_speed_mps
            self.robot.status = RobotStatus.TRANSIT
            self.has_replanned_for_conflict = False
            self.blocking_train_id = None
            self.blocking_track_id = None
            self.current_action = "PROCEEDING"
            route_str = " -> ".join(new_route.track_ids)
            self.logger.log("PLANNING", f"NEW DYNAMIC ROUTE: {route_str} (Dist: {new_route.total_distance_m:.0f}m, ETA: {new_route.estimated_time_sec:.0f}s)")

    def _resolve_conflict(self, conflict: ConflictWarning):
        """
        Intelligent Robot Yield & Track Change Resolution:
        Evaluates CONTINUE, CHANGE_TRACK, SLOW_DOWN, WAIT.
        """
        # Action 1: CONTINUE if robot will safely clear the section ahead of the train
        if conflict.robot_eta_sec + 5.0 < conflict.train_eta_sec:
            self.logger.log("ROBOT", f"Robot will safely clear {conflict.conflict_track_id} before {conflict.train_id} arrives (ETA: {conflict.robot_eta_sec:.1f}s vs {conflict.train_eta_sec:.1f}s) -> CONTINUING")
            self.current_action = "PROCEEDING"
            self.robot.target_speed = self.robot.max_speed_mps
            self.robot.status = RobotStatus.TRANSIT
            return

        self.logger.log("ROBOT", f"Conflict predicted with {conflict.train_id}")
        self.logger.log("ROBOT", f"Robot ETA to conflict: {conflict.robot_eta_sec:.1f}s | Train ETA to conflict: {conflict.train_eta_sec:.1f}s")
        self.logger.log("ROBOT", "Evaluating alternative tracks...")
        self.old_route = self.current_route

        mission = self.missions.get_mission(self.robot.active_mission_id)
        target_coords = (mission.target_x, mission.target_y) if mission else None
        target_track = mission.target_track_id if mission else self.robot.assigned_route[-1]

        # Evaluate complete time-aware route options (detours vs waiting)
        new_plan = self.planner.find_shortest_safe_route(
            start_track_id=self.robot.current_track_id,
            target_track_id=target_track,
            agent_speed_mps=self.robot.max_speed_mps,
            trains=self.trains,
            start_time_offset_sec=self.sim_time_sec,
            target_coords=target_coords
        )

        # Check if train is behind the robot on the same track
        is_trailing_behind = False
        for tr in self.trains:
            if tr.agent_id == conflict.train_id and tr.current_track_id == self.robot.current_track_id:
                if tr.direction == self.robot.direction:
                    # Train is behind robot
                    if (self.robot.direction > 0 and tr.distance_along_track_m <= self.robot.distance_along_track_m) or \
                       (self.robot.direction < 0 and tr.distance_along_track_m >= self.robot.distance_along_track_m):
                        is_trailing_behind = True
                        break

        if is_trailing_behind:
            # Robot must not stop in front of the train! It must hasten to the upcoming switch to clear the line
            self.logger.log("ROBOT", f"Train {conflict.train_id} approaching from behind on {self.robot.current_track_id} -> Hastening to switch to clear line")
            self.robot.target_speed = self.robot.max_speed_mps
            self.robot.status = RobotStatus.CHANGING_TRACK
            self.current_action = f"MAKING WAY FOR {conflict.train_id}"
            self.has_replanned_for_conflict = True
            return

        if new_plan and new_plan.is_safe and new_plan.expected_wait_sec == 0.0:
            # Safe alternative track exists -> Robot actively changes track to avoid delaying the train!
            self.current_route = new_plan
            self.robot.set_route(new_plan.track_ids)
            self.robot.status = RobotStatus.CHANGING_TRACK
            self.current_action = f"MAKING WAY FOR {conflict.train_id}"
            self.has_replanned_for_conflict = True
            self.blocking_train_id = None
            self.blocking_track_id = None
            route_str = " -> ".join(new_plan.track_ids)
            self.logger.log("ROBOT", f"Safe alternative found: {route_str}")
            self.logger.log("ROBOT", f"Changing track to avoid delaying {conflict.train_id}")
        else:
            # No immediate safe alternative track -> Robot yields and waits before the conflict area
            self.logger.log("ROBOT", "No safe alternative available")
            self.logger.log("ROBOT", f"Reducing speed / WAITING_FOR_CLEARANCE for {conflict.train_id}")
            self.robot.target_speed = 0.0
            self.robot.status = RobotStatus.YIELDING_TO_TRAIN
            self.current_action = f"WAITING FOR {conflict.train_id}"
            self.has_replanned_for_conflict = True
            self.blocking_train_id = conflict.train_id
            self.blocking_track_id = conflict.conflict_track_id
            self.blocking_conflict_eta = conflict.time_to_conflict_sec
            
            # Estimate when train clears
            self.blocking_clearance_time = self.sim_time_sec + conflict.train_eta_sec + 20.0
            for tr in self.trains:
                if tr.agent_id == conflict.train_id:
                    for win in tr.predict_occupancy_windows(sim_time_offset=self.sim_time_sec):
                        if win.track_id == conflict.conflict_track_id:
                            self.blocking_clearance_time = win.exit_time_sec + 5.0

    def get_expected_clearance_sec(self) -> float:
        """Returns remaining seconds until the blocking train clears the conflict area."""
        if self.robot and self.robot.status in (RobotStatus.YIELDING_TO_TRAIN, RobotStatus.WAITING_FOR_CLEARANCE):
            rem = self.blocking_clearance_time - self.sim_time_sec
            return max(0.0, rem)
        return 0.0

    def _handle_robot_arrival(self, mission: Mission):
        """Triggers localized inspection mode upon arrival."""
        self.robot.status = RobotStatus.INSPECTING
        self.robot.target_speed = 0.0
        self.robot.speed = 0.0
        self.current_action = "INSPECTING"
        self.missions.update_status(mission.mission_id, MissionStatus.ARRIVED, "Robot arrived at target anomaly chainage")
        self.missions.update_status(mission.mission_id, MissionStatus.INSPECTING, "Onboard sensors capturing multi-spectral rail data")
        self.logger.log("ROBOT", f"Robot {self.robot.agent_id} ARRIVED at KM {mission.target_chainage_km:.2f}. Initiating RailGuard AI inspection.")

    def _handle_inspection_analysis(self, mission: Mission):
        """Processes uploaded inspection package, classifies severity, and applies track blocking."""
        self.missions.update_status(mission.mission_id, MissionStatus.ANALYZING, "Raspberry Pi decision engine evaluating defect telemetry")
        self.logger.log("ANALYSIS", "Inspection package uploaded. Analyzing RailGuard AI probability heatmaps...")

        # Collect synthesized multi-modal inspection package
        pkg = self.collector.collect_inspection(
            mission_id=mission.mission_id,
            robot_id=self.robot.agent_id,
            anomaly_id=mission.anomaly_id,
            track_id=mission.target_track_id,
            chainage_km=mission.target_chainage_km,
            latitude=self.robot.get_telemetry().latitude,
            longitude=self.robot.get_telemetry().longitude,
            heading_deg=math.degrees(self.robot.heading),
            defect_type=AnomalyType.BROKEN_RAIL,
            severity=AnomalySeverity.CRITICAL,
            confidence=0.95
        )

        # Update anomaly state
        anomaly = self.anomalies.assess_anomaly(
            anomaly_id=mission.anomaly_id,
            confirmed_type=pkg.detected_defect,
            severity=pkg.classified_severity,
            confidence=pkg.confidence_score,
            evidence_images=pkg.image_paths
        )

        self.logger.log("DECISION", f"DEFECT CLASSIFIED: {pkg.detected_defect.value} | SEVERITY: {pkg.classified_severity.value} (Confidence: {pkg.confidence_score*100:.1f}%)")

        # Apply incident response policy
        if pkg.classified_severity == AnomalySeverity.CRITICAL:
            self._block_track_and_reroute_trains(mission.target_track_id)

        self.missions.update_status(mission.mission_id, MissionStatus.COMPLETED, "Inspection and incident response complete")
        self.robot.status = RobotStatus.IDLE
        self.robot.active_mission_id = None
        self.current_route = None
        self.current_action = "IDLE"
        self.logger.log("MISSION", f"Mission {mission.mission_id} successfully COMPLETED.")

    def _block_track_and_reroute_trains(self, blocked_track_id: str):
        """Blocks the damaged track segment and recalculates alternative routes for all affected trains."""
        target_track = self.graph.tracks.get(blocked_track_id)
        if target_track:
            target_track.status = TrackStatus.BLOCKED
            self.logger.log("MAP_UPDATE", f"CRITICAL HAZARD: Track {blocked_track_id} status updated to BLOCKED")

        for train in self.trains:
            if blocked_track_id in train.assigned_route:
                self.logger.log("INCIDENT_RESPONSE", f"Train {train.agent_id} affected by blockage on {blocked_track_id}. Calculating diversion route...")
                
                dest_track = train.assigned_route[-1]
                bypass_plan = self.rerouter.replan_train_around_blockage(
                    train=train,
                    destination_track_id=dest_track,
                    blocked_track_ids={blocked_track_id}
                )

                if bypass_plan:
                    train.set_route(bypass_plan.track_ids)
                    train.status = TrainStatus.REROUTING
                    route_str = " -> ".join(bypass_plan.track_ids)
                    self.logger.log("INCIDENT_RESPONSE", f"Train {train.agent_id} REROUTED via: {route_str}")
                    self.has_rerouted_trains = True
                else:
                    self.logger.log("SAFETY", f"No alternative route for Train {train.agent_id}! Commanding EMERGENCY STOP.")
                    train.target_speed = 0.0
                    train.status = TrainStatus.STOPPED
