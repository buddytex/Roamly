"""
RailGuard AI - Global Configuration & Simulation Constants
"""
import os
from enum import Enum
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

class TrackStatus(Enum):
    OPEN = "OPEN"
    RESTRICTED = "RESTRICTED"
    BLOCKED = "BLOCKED"
    UNDER_INSPECTION = "UNDER_INSPECTION"

class SwitchState(Enum):
    NORMAL = "NORMAL"        # Through route
    REVERSE = "REVERSE"      # Diverging route

class RobotStatus(Enum):
    IDLE = "IDLE"
    PLANNING = "PLANNING"
    TRANSIT = "TRANSIT"
    CHANGING_TRACK = "CHANGING_TRACK"
    YIELDING_TO_TRAIN = "YIELDING_TO_TRAIN"
    WAITING_FOR_CLEARANCE = "WAITING_FOR_CLEARANCE"
    REPLANNING = "REPLANNING"
    INSPECTING = "INSPECTING"
    UPLOADING_EVIDENCE = "UPLOADING"
    COMPLETED = "COMPLETE"
    FAULT = "FAULT"
    EMERGENCY_STOP = "EMERGENCY_STOP"

class TrainStatus(Enum):
    RUNNING = "RUNNING"
    DECELERATING = "DECELERATING"
    STOPPED = "STOPPED"
    WAITING_FOR_CLEARANCE = "WAITING_FOR_CLEARANCE"
    YIELDING = "YIELDING"
    REROUTING = "REROUTING"

class AnomalyType(Enum):
    UNKNOWN = "UNKNOWN"
    BROKEN_RAIL = "BROKEN_RAIL"
    OBSTRUCTION = "OBSTRUCTION"
    TRACK_GEOMETRY = "TRACK_GEOMETRY"
    VEGETATION = "VEGETATION"
    MISSING_FASTENER = "MISSING_FASTENER"

class AnomalySeverity(Enum):
    UNASSESSED = "UNASSESSED"
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"

class AnomalyStatus(Enum):
    REPORTED = "REPORTED"
    DISPATCHED = "DISPATCHED"
    INSPECTING = "INSPECTING"
    CONFIRMED = "CONFIRMED"
    CLEARED = "CLEARED"

class MissionStatus(Enum):
    CREATED = "CREATED"
    PLANNING = "PLANNING"
    ASSIGNED = "ASSIGNED"
    EN_ROUTE = "EN_ROUTE"
    ARRIVED = "ARRIVED"
    INSPECTING = "INSPECTING"
    UPLOADING = "UPLOADING"
    ANALYZING = "ANALYZING"
    COMPLETED = "COMPLETED"
    PAUSED = "PAUSED"
    FAILED = "FAILED"
    ABORTED = "ABORTED"

class SafetyLevel(Enum):
    NORMAL = "NORMAL"
    CAUTION = "CAUTION"
    EMERGENCY = "EMERGENCY"

# Kinematic Defaults
ROBOT_MAX_SPEED_MPS = 4.5        # ~16.2 km/h max inspection cart speed
ROBOT_ACCELERATION_MPS2 = 1.2    # 1.2 m/s^2
ROBOT_BRAKING_MPS2 = 2.5         # 2.5 m/s^2 braking
ROBOT_ARRIVAL_THRESHOLD_M = 3.0  # Distance threshold to trigger inspection mode

TRAIN_DEFAULT_SPEED_MPS = 16.0   # ~58 km/h standard line speed
TRAIN_RESTRICTED_SPEED_MPS = 7.0 # ~25 km/h cautionary speed
TRAIN_ACCELERATION_MPS2 = 0.5
TRAIN_BRAKING_MPS2 = 1.0
TRAIN_LENGTH_M = 75.0            # Simulated rolling stock length

# Conflict & Planning Parameters
MIN_TRAIN_HEADWAY_SECONDS = 12.0     # Minimum time-window headway between two trains
MIN_HEADWAY_SECONDS = 20.0          # Minimum safety headway separation between train and robot
SAFE_TRAIN_DISTANCE_M = 45.0        # Physical following safety distance margin
CONFLICT_SAFETY_HEADWAY_SEC = 25.0  # Conflict alert threshold
SWITCH_TRAVERSAL_PENALTY_SEC = 4.0  # Time/Cost penalty for turning through a switch point

# Simulation Speed & Determinism Defaults
DEFAULT_SIMULATION_SPEED = 10.0     # Default 10x fast-forward (1s real = 10s sim)
SIMULATION_SPEED_PRESETS = [1.0, 5.0, 10.0, 25.0, 50.0, 100.0]
RANDOM_SEED: Optional[int] = 42     # Set to an integer for reproducible test runs, or None for random
