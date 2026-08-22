"""
RailGuard AI - Shared Data Schemas & Communication Protocols
Defines standard JSON contracts for Phone <-> Raspberry Pi <-> ESP32 interactions.
"""
from dataclasses import dataclass, field
from typing import List, Dict, Optional
import time

@dataclass
class AnomalyReportSchema:
    anomaly_id: str
    track_id: str
    chainage_km: float
    x: float
    y: float
    defect_type: str  # BROKEN_RAIL, OBSTACLE, TRACK_BUCKLING, FASTENER_FAILURE
    severity: str     # MINOR, MODERATE, CRITICAL
    confidence: float
    image_base64: Optional[str] = None
    timestamp: float = field(default_factory=time.time)

@dataclass
class RobotTelemetrySchema:
    robot_id: str
    x: float
    y: float
    heading: float
    speed_mps: float
    battery_pct: float
    current_track_id: str
    distance_along_track_m: float
    status: str
    safety_level: str
    primary_track_confidence: float
    timestamp: float = field(default_factory=time.time)

@dataclass
class MissionDispatchSchema:
    mission_id: str
    robot_id: str
    target_anomaly_id: str
    target_track_id: str
    target_x: float
    target_y: float
    planned_route_track_ids: List[str]
    max_speed_mps: float = 4.5
    created_timestamp: float = field(default_factory=time.time)
