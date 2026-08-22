"""
Spatio-Temporal Train-Robot Conflict Prediction Engine
"""
from __future__ import annotations
import math
from dataclasses import dataclass
from typing import Dict, List, Optional, Set, Tuple, TYPE_CHECKING
from config import CONFLICT_SAFETY_HEADWAY_SEC, MIN_HEADWAY_SECONDS
from world.graph import RailwayGraph

if TYPE_CHECKING:
    from entities.robot import InspectionRobot
    from entities.train import Train

@dataclass
class ConflictWarning:
    conflict_id: str
    train_id: str
    robot_id: str
    conflict_track_id: str
    time_to_conflict_sec: float
    train_eta_sec: float
    robot_eta_sec: float
    headway_margin_sec: float
    severity: str  # IMMINENT, POTENTIAL, CAUTION
    description: str

class ConflictEngine:
    """
    Predicts spatial-temporal collision and fouling point hazards across the railway network.
    """
    def __init__(self, graph: RailwayGraph, safety_buffer_sec: float = CONFLICT_SAFETY_HEADWAY_SEC):
        self.graph = graph
        self.safety_buffer_sec = safety_buffer_sec

    def check_conflicts(
        self,
        robot: InspectionRobot,
        trains: List[Train],
        sim_time_sec: float = 0.0
    ) -> List[ConflictWarning]:
        """
        Analyzes future trajectories of robot and all active trains.
        """
        warnings: List[ConflictWarning] = []
        robot_route = robot.assigned_route
        if not robot_route:
            return warnings

        # 1. Estimate robot occupancy windows: track_id -> (enter_time, exit_time)
        robot_schedule: Dict[str, Tuple[float, float]] = {}
        robot_speed = max(1.5, robot.speed if robot.speed > 0 else robot.max_speed_mps)
        
        curr_track = self.graph.tracks.get(robot.current_track_id)
        rem_curr = max(0.0, (curr_track.length_m - robot.distance_along_track_m)) if curr_track else 0.0
        robot_schedule[robot.current_track_id] = (sim_time_sec, sim_time_sec + rem_curr / robot_speed)
        
        accum_time = sim_time_sec + rem_curr / robot_speed
        for tid in robot_route[robot.route_index + 1:]:
            track = self.graph.tracks.get(tid)
            if not track:
                continue
            dur = track.length_m / robot_speed
            robot_schedule[tid] = (accum_time, accum_time + dur)
            accum_time += dur

        # 2. Check each train's predicted occupancy windows
        for train in trains:
            train_windows = train.predict_occupancy_windows(sim_time_offset=sim_time_sec)
            for twin in train_windows:
                if twin.track_id in robot_schedule:
                    r_in, r_out = robot_schedule[twin.track_id]
                    t_in = twin.enter_time_sec
                    t_out = twin.exit_time_sec

                    # True Spatio-temporal conflict check:
                    h_sec = 4.0
                    has_conflict = False

                    # If train clears the segment before robot arrives, no conflict
                    if t_out + h_sec <= r_in:
                        has_conflict = False
                    # If robot clears the segment before train arrives, no conflict
                    elif r_out + h_sec <= t_in:
                        has_conflict = False
                    # Direct time-interval overlap on shared block
                    elif max(r_in, t_in) < min(r_out, t_out):
                        has_conflict = True
                    # Insufficient headway between departure and arrival
                    elif r_in < t_out + h_sec and r_out > t_in - h_sec:
                        has_conflict = True

                    if has_conflict:
                        time_to_conflict = max(0.0, min(r_in, t_in) - sim_time_sec)
                        headway_diff = abs(r_in - t_in)
                        severity = "IMMINENT" if headway_diff < 8.0 else "POTENTIAL"
                        warnings.append(ConflictWarning(
                            conflict_id=f"CONF_{train.agent_id}_{robot.agent_id}_{twin.track_id}",
                            train_id=train.agent_id,
                            robot_id=robot.agent_id,
                            conflict_track_id=twin.track_id,
                            time_to_conflict_sec=time_to_conflict,
                            train_eta_sec=max(0.0, t_in - sim_time_sec),
                            robot_eta_sec=max(0.0, r_in - sim_time_sec),
                            headway_margin_sec=headway_diff,
                            severity=severity,
                            description=(
                                f"Train {train.agent_id} (ETA: {t_in - sim_time_sec:.1f}s) and Robot {robot.agent_id} "
                                f"(ETA: {r_in - sim_time_sec:.1f}s) conflict on Track {twin.track_id} "
                                f"within {headway_diff:.1f}s headway margin."
                            )
                        ))

        return warnings
