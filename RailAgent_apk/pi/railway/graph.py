"""
Railway Network Graph & Geometry Engine
"""
import math
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple, Set
from config import TrackStatus, SwitchState

@dataclass
class Node:
    node_id: str
    x: float
    y: float
    name: str = ""
    node_type: str = "JUNCTION"  # JUNCTION, SWITCH, STATION, BUFFER
    connected_tracks: List[str] = field(default_factory=list)

@dataclass
class SwitchNode(Node):
    stem_track_id: str = ""
    normal_track_id: str = ""
    reverse_track_id: str = ""
    current_state: SwitchState = SwitchState.NORMAL
    is_locked: bool = False
    transition_progress: float = 1.0  # 1.0 = fully thrown

    def __post_init__(self):
        self.node_type = "SWITCH"

@dataclass
class TrackSegment:
    track_id: str
    start_node_id: str
    end_node_id: str
    geometry: List[Tuple[float, float]]  # Polyline coordinates [(x0, y0), (x1, y1), ...]
    length_m: float = 0.0
    speed_limit_mps: float = 20.0
    status: TrackStatus = TrackStatus.OPEN
    occupants: Set[str] = field(default_factory=set)
    is_bidirectional: bool = True
    chainage_start_km: float = 0.0
    chainage_end_km: float = 0.0

    def __post_init__(self):
        if self.length_m <= 0.0 and len(self.geometry) >= 2:
            self.length_m = self._compute_length()
        if self.chainage_end_km == 0.0 and self.length_m > 0:
            self.chainage_end_km = self.chainage_start_km + (self.length_m / 1000.0)

    def _compute_length(self) -> float:
        total = 0.0
        for i in range(len(self.geometry) - 1):
            p1 = self.geometry[i]
            p2 = self.geometry[i + 1]
            total += math.hypot(p2[0] - p1[0], p2[1] - p1[1])
        return total

    def get_point_at_distance(self, dist_m: float) -> Tuple[float, float, float]:
        """
        Returns (x, y, heading_rad) at metric distance along this track segment.
        Heading is in radians [0, 2pi), 0 = along +X axis.
        """
        dist_m = max(0.0, min(self.length_m, dist_m))
        if len(self.geometry) < 2:
            return (self.geometry[0][0], self.geometry[0][1], 0.0) if self.geometry else (0.0, 0.0, 0.0)

        accum = 0.0
        for i in range(len(self.geometry) - 1):
            p1 = self.geometry[i]
            p2 = self.geometry[i + 1]
            seg_len = math.hypot(p2[0] - p1[0], p2[1] - p1[1])
            if accum + seg_len >= dist_m or i == len(self.geometry) - 2:
                ratio = 0.0 if seg_len == 0 else (dist_m - accum) / seg_len
                x = p1[0] + ratio * (p2[0] - p1[0])
                y = p1[1] + ratio * (p2[1] - p1[1])
                heading = math.atan2(p2[1] - p1[1], p2[0] - p1[0])
                return (x, y, heading)
            accum += seg_len

        last = self.geometry[-1]
        p_prev = self.geometry[-2]
        heading = math.atan2(last[1] - p_prev[1], last[0] - p_prev[0])
        return (last[0], last[1], heading)

    def project_point(self, px: float, py: float) -> Tuple[float, float, float]:
        """
        Projects point (px, py) onto this polyline segment.
        Returns: (closest_x, closest_y, distance_along_track_m)
        """
        best_dist_sq = float('inf')
        best_pt = (self.geometry[0][0], self.geometry[0][1])
        best_track_dist = 0.0
        accum = 0.0

        for i in range(len(self.geometry) - 1):
            p1 = self.geometry[i]
            p2 = self.geometry[i + 1]
            dx = p2[0] - p1[0]
            dy = p2[1] - p1[1]
            seg_len_sq = dx * dx + dy * dy

            if seg_len_sq == 0:
                t = 0.0
            else:
                t = max(0.0, min(1.0, ((px - p1[0]) * dx + (py - p1[1]) * dy) / seg_len_sq))

            proj_x = p1[0] + t * dx
            proj_y = p1[1] + t * dy
            d_sq = (px - proj_x) ** 2 + (py - proj_y) ** 2

            if d_sq < best_dist_sq:
                best_dist_sq = d_sq
                best_pt = (proj_x, proj_y)
                best_track_dist = accum + t * math.sqrt(seg_len_sq)

            accum += math.sqrt(seg_len_sq)

        return (best_pt[0], best_pt[1], best_track_dist)


class RailwayGraph:
    """
    Topological and Geometric Railway Network Graph
    """
    def __init__(self):
        self.nodes: Dict[str, Node] = {}
        self.tracks: Dict[str, TrackSegment] = {}
        self.adjacency: Dict[str, List[Tuple[str, str]]] = {} # node_id -> [(neighbor_node_id, track_id)]

    def add_node(self, node: Node):
        self.nodes[node.node_id] = node
        if node.node_id not in self.adjacency:
            self.adjacency[node.node_id] = []

    def add_track(self, track: TrackSegment):
        self.tracks[track.track_id] = track
        
        # Link to nodes
        if track.start_node_id in self.nodes:
            self.nodes[track.start_node_id].connected_tracks.append(track.track_id)
            if track.start_node_id not in self.adjacency:
                self.adjacency[track.start_node_id] = []
            self.adjacency[track.start_node_id].append((track.end_node_id, track.track_id))

        if track.end_node_id in self.nodes:
            self.nodes[track.end_node_id].connected_tracks.append(track.track_id)
            if track.end_node_id not in self.adjacency:
                self.adjacency[track.end_node_id] = []
            if track.is_bidirectional:
                self.adjacency[track.end_node_id].append((track.start_node_id, track.track_id))

    def get_track_between(self, u: str, v: str) -> Optional[TrackSegment]:
        for neighbor, track_id in self.adjacency.get(u, []):
            if neighbor == v:
                return self.tracks[track_id]
        return None

    def get_other_node(self, track: TrackSegment, node_id: str) -> Optional[str]:
        if track.start_node_id == node_id:
            return track.end_node_id
        elif track.end_node_id == node_id:
            return track.start_node_id
        return None

    def get_switch_active_track(self, switch_id: str) -> Optional[str]:
        node = self.nodes.get(switch_id)
        if isinstance(node, SwitchNode):
            if node.current_state == SwitchState.NORMAL:
                return node.normal_track_id
            else:
                return node.reverse_track_id
        return None
