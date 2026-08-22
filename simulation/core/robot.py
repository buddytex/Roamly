"""
Autonomous Inspection Robot Entity
"""
import time
import math
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple
from config import RobotStatus, SafetyLevel, ROBOT_MAX_SPEED_MPS, ROBOT_ACCELERATION_MPS2, ROBOT_BRAKING_MPS2
from world.graph import RailwayGraph
from entities.base_agent import KinematicAgent

@dataclass
class RobotTelemetry:
    robot_id: str
    timestamp: float
    x: float
    y: float
    latitude: float
    longitude: float
    heading: float
    speed: float
    current_track_id: str
    distance_along_track_m: float
    battery_pct: float
    status: RobotStatus
    safety_level: SafetyLevel
    primary_track_detected: bool = True
    primary_track_confidence: float = 0.96
    secondary_tracks_count: int = 1

class InspectionRobot(KinematicAgent):
    """
    Simulated autonomous inspection robot equipped with onboard Android perception & sensors.
    """
    def __init__(
        self,
        robot_id: str,
        graph: RailwayGraph,
        initial_track_id: str = "T03a",
        initial_distance_m: float = 0.0
    ):
        super().__init__(
            agent_id=robot_id,
            graph=graph,
            initial_track_id=initial_track_id,
            initial_distance_m=initial_distance_m,
            max_speed_mps=ROBOT_MAX_SPEED_MPS,
            acceleration_mps2=ROBOT_ACCELERATION_MPS2,
            braking_mps2=ROBOT_BRAKING_MPS2
        )
        self.battery_pct = 98.5
        self.status = RobotStatus.IDLE
        self.safety_level = SafetyLevel.NORMAL
        self.active_mission_id: Optional[str] = None
        self.inspection_timer: float = 0.0
        self.inspection_duration: float = 3.5  # seconds in inspection mode

    def get_telemetry(self) -> RobotTelemetry:
        """Returns structured robot telemetry sent to Raspberry Pi decision brain."""
        # Baseline GPS conversion from local metric grid (Kochi Railway Division base: 9.9312 N, 76.2673 E)
        lat = 9.9312 + (self.y / 111139.0)
        lon = 76.2673 + (self.x / (111139.0 * math.cos(math.radians(9.9312))))
        
        return RobotTelemetry(
            robot_id=self.agent_id,
            timestamp=time.time(),
            x=self.x,
            y=self.y,
            latitude=lat,
            longitude=lon,
            heading=self.heading,
            speed=self.speed,
            current_track_id=self.current_track_id,
            distance_along_track_m=self.distance_along_track_m,
            battery_pct=self.battery_pct,
            status=self.status,
            safety_level=self.safety_level,
            primary_track_detected=True,
            primary_track_confidence=0.96,
            secondary_tracks_count=1
        )

    def step(self, dt: float):
        """Advances robot state & handles inspection timing."""
        if self.status == RobotStatus.INSPECTING:
            self.target_speed = 0.0
            self.speed = 0.0
            self.inspection_timer += dt
            if self.inspection_timer >= self.inspection_duration:
                self.status = RobotStatus.UPLOADING_EVIDENCE
        else:
            self.update_kinematics(dt)
            # Drain battery slightly during movement
            if self.speed > 0:
                self.battery_pct = max(0.0, self.battery_pct - 0.005 * dt)
