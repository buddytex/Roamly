"""
Kinematic Physics Engine for Railway Vehicles
"""
import math
from typing import List, Optional, Tuple
from config import TrackStatus
from world.graph import RailwayGraph, TrackSegment

class KinematicAgent:
    """
    Base kinematic vehicle traversing interconnected track polyline geometry.
    """
    def __init__(
        self,
        agent_id: str,
        graph: RailwayGraph,
        initial_track_id: str,
        initial_distance_m: float = 0.0,
        max_speed_mps: float = 5.0,
        acceleration_mps2: float = 1.0,
        braking_mps2: float = 2.0
    ):
        self.agent_id = agent_id
        self.graph = graph
        self.current_track_id = initial_track_id
        self.distance_along_track_m = initial_distance_m
        self.direction = 1  # +1 from start_node to end_node, -1 opposite
        self.speed = 0.0
        self.target_speed = 0.0
        self.max_speed_mps = max_speed_mps
        self.acceleration_mps2 = acceleration_mps2
        self.braking_mps2 = braking_mps2
        
        # Route is a list of track IDs to traverse in order
        self.assigned_route: List[str] = [initial_track_id]
        self.route_index: int = 0
        
        # World spatial state
        self.x = 0.0
        self.y = 0.0
        self.heading = 0.0
        self._update_spatial_pose()

    def set_route(self, route: List[str]):
        """Sets the sequence of track IDs for navigation."""
        if not route:
            return
        self.assigned_route = list(route)
        self.route_index = 0
        self.current_track_id = self.assigned_route[0]

    def _update_spatial_pose(self):
        track = self.graph.tracks.get(self.current_track_id)
        if track:
            self.x, self.y, self.heading = track.get_point_at_distance(self.distance_along_track_m)
            if self.direction < 0:
                self.heading = (self.heading + math.pi) % (2 * math.pi)

    def update_kinematics(self, dt: float):
        """
        Advances the agent position based on acceleration limits and track boundaries.
        """
        # Speed profile update
        if self.speed < self.target_speed:
            self.speed = min(self.target_speed, self.speed + self.acceleration_mps2 * dt)
        elif self.speed > self.target_speed:
            self.speed = max(self.target_speed, self.speed - self.braking_mps2 * dt)

        if self.speed == 0.0:
            self._update_spatial_pose()
            return

        track = self.graph.tracks.get(self.current_track_id)
        if not track:
            return

        # Check track blockage
        if track.status == TrackStatus.BLOCKED:
            self.speed = 0.0
            self.target_speed = 0.0
            return

        # Compute delta movement
        delta_dist = self.speed * dt * self.direction
        new_dist = self.distance_along_track_m + delta_dist

        # Check for segment boundary transition
        if new_dist > track.length_m:
            overshoot = new_dist - track.length_m
            self._transition_to_next_track(overshoot, from_end=True)
        elif new_dist < 0.0:
            overshoot = -new_dist
            self._transition_to_next_track(overshoot, from_end=False)
        else:
            self.distance_along_track_m = new_dist

        self._update_spatial_pose()

    def can_transition_to_track(self, next_track_id: str) -> bool:
        """Hook to verify if transition into next track is authorized. Override in subclasses."""
        return True

    def _transition_to_next_track(self, overshoot: float, from_end: bool):
        """
        Transitions to the next track segment in the assigned route with signal boundary enforcement.
        """
        current_track = self.graph.tracks.get(self.current_track_id)

        if self.route_index + 1 < len(self.assigned_route):
            candidate_next_id = self.assigned_route[self.route_index + 1]
            if not self.can_transition_to_track(candidate_next_id):
                # Blocked signal/reservation boundary -> Clamp position and stop safely
                if from_end:
                    self.distance_along_track_m = max(0.0, current_track.length_m - 0.5) if current_track else 0.0
                else:
                    self.distance_along_track_m = 0.5
                self.speed = 0.0
                self.target_speed = 0.0
                return

            if current_track and self.agent_id in current_track.occupants:
                current_track.occupants.remove(self.agent_id)

            self.route_index += 1
            next_track_id = self.assigned_route[self.route_index]
            next_track = self.graph.tracks.get(next_track_id)
            
            if next_track:
                # Determine entry direction on next track
                exit_node = current_track.end_node_id if from_end else current_track.start_node_id
                
                if next_track.start_node_id == exit_node:
                    self.direction = 1
                    self.distance_along_track_m = min(next_track.length_m, overshoot)
                elif next_track.end_node_id == exit_node:
                    self.direction = -1
                    self.distance_along_track_m = max(0.0, next_track.length_m - overshoot)
                else:
                    # Default alignment
                    self.direction = 1
                    self.distance_along_track_m = 0.0

                self.current_track_id = next_track_id
                next_track.occupants.add(self.agent_id)
            else:
                self.speed = 0.0
                self.target_speed = 0.0
        else:
            # End of route reached - call hook for autonomous continuation
            self.on_route_exhausted(from_end, overshoot)
            if current_track:
                current_track.occupants.add(self.agent_id)

    def on_route_exhausted(self, from_end: bool, overshoot: float):
        """Hook called when the agent reaches the end of its assigned route."""
        current_track = self.graph.tracks.get(self.current_track_id)
        if from_end:
            self.distance_along_track_m = current_track.length_m if current_track else 0.0
        else:
            self.distance_along_track_m = 0.0
        self.speed = 0.0
        self.target_speed = 0.0
