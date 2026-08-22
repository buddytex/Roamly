"""
Mission Lifecycle Management & State Machine
"""
import time
from dataclasses import dataclass, field
from typing import Dict, List, Optional
from config import MissionStatus
from world.anomaly_manager import Anomaly

@dataclass
class Mission:
    mission_id: str
    anomaly_id: str
    assigned_robot_id: str
    target_track_id: str
    target_chainage_km: float
    target_x: float
    target_y: float
    planned_route: List[str]
    current_route_index: int = 0
    status: MissionStatus = MissionStatus.CREATED
    created_at: float = field(default_factory=time.time)
    completed_at: Optional[float] = None
    failure_reason: Optional[str] = None
    log_history: List[str] = field(default_factory=list)

    def log(self, message: str):
        t_str = time.strftime("%H:%M:%S", time.localtime())
        self.log_history.append(f"[{t_str}] {message}")

class MissionManager:
    """
    Coordinates creation, assignment, progression, and completion of inspection missions.
    """
    def __init__(self):
        self.missions: Dict[str, Mission] = {}
        self.mission_counter = 1

    def create_mission(
        self,
        anomaly: Anomaly,
        assigned_robot_id: str,
        initial_route: List[str]
    ) -> Mission:
        mission_id = f"M{self.mission_counter:03d}"
        self.mission_counter += 1

        mission = Mission(
            mission_id=mission_id,
            anomaly_id=anomaly.anomaly_id,
            assigned_robot_id=assigned_robot_id,
            target_track_id=anomaly.track_id,
            target_chainage_km=anomaly.chainage_km,
            target_x=anomaly.x,
            target_y=anomaly.y,
            planned_route=list(initial_route),
            status=MissionStatus.ASSIGNED
        )
        mission.log(f"Mission {mission_id} assigned to Robot {assigned_robot_id} for Anomaly {anomaly.anomaly_id}")
        self.missions[mission_id] = mission
        return mission

    def get_mission(self, mission_id: str) -> Optional[Mission]:
        return self.missions.get(mission_id)

    def update_status(self, mission_id: str, new_status: MissionStatus, reason: str = "") -> Optional[Mission]:
        mission = self.missions.get(mission_id)
        if not mission:
            return None
        mission.status = new_status
        if reason:
            mission.log(f"Status -> {new_status.value}: {reason}")
        else:
            mission.log(f"Status -> {new_status.value}")

        if new_status in (MissionStatus.COMPLETED, MissionStatus.FAILED, MissionStatus.ABORTED):
            mission.completed_at = time.time()

        return mission
