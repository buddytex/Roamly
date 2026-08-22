"""
Rolling Stock Train Entity with Resource Reservations, Anti-Collision, and Conflict-Aware Routing
"""
import time
import math
import random
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple, Set
from config import (
    TrainStatus, TrackStatus, TRAIN_DEFAULT_SPEED_MPS, TRAIN_ACCELERATION_MPS2,
    TRAIN_BRAKING_MPS2, TRAIN_LENGTH_M, MIN_TRAIN_HEADWAY_SECONDS, SAFE_TRAIN_DISTANCE_M, RANDOM_SEED
)
from world.graph import RailwayGraph, SwitchNode, TrackSegment
from entities.base_agent import KinematicAgent
from planning.reservation_manager import ReservationManager

@dataclass
class TrainOccupancyWindow:
    track_id: str
    enter_time_sec: float
    exit_time_sec: float
    direction: int = 1

@dataclass
class TrainTelemetry:
    train_id: str
    timestamp: float
    x: float
    y: float
    speed: float
    target_speed: float
    heading: float
    current_track_id: str
    distance_along_track_m: float
    length_m: float
    status: TrainStatus
    assigned_route: List[str]
    next_track_id: Optional[str]
    next_entry_eta_sec: Optional[float]
    next_exit_eta_sec: Optional[float]
    reservation_status: str = "SAFE"
    wait_reason: Optional[str] = None

class Train(KinematicAgent):
    """
    Simulated rolling stock train moving continuously and autonomously across the railway network
    with guaranteed inter-train collision avoidance and time-window segment reservations.
    """
    def __init__(
        self,
        train_id: str,
        graph: RailwayGraph,
        initial_track_id: str,
        initial_distance_m: float = 0.0,
        initial_speed_mps: float = TRAIN_DEFAULT_SPEED_MPS,
        length_m: float = TRAIN_LENGTH_M,
        rng_seed: Optional[int] = None,
        autonomous_routing: bool = True,
        reservation_manager: Optional[ReservationManager] = None
    ):
        super().__init__(
            agent_id=train_id,
            graph=graph,
            initial_track_id=initial_track_id,
            initial_distance_m=initial_distance_m,
            max_speed_mps=initial_speed_mps,
            acceleration_mps2=TRAIN_ACCELERATION_MPS2,
            braking_mps2=TRAIN_BRAKING_MPS2
        )
        self.length_m = length_m
        self.status = TrainStatus.RUNNING
        self.target_speed = initial_speed_mps
        self.speed = initial_speed_mps
        self.autonomous_routing = autonomous_routing
        self.reservation_manager = reservation_manager
        self.wait_reason: Optional[str] = None
        self.last_replan_time: float = 0.0
        
        # Initialize RNG for deterministic or random traffic branching
        seed_val = rng_seed if rng_seed is not None else (RANDOM_SEED + hash(train_id) % 1000 if RANDOM_SEED is not None else None)
        self.rng = random.Random(seed_val)

        # Build initial forward lookahead route and lock reservations
        if self.autonomous_routing and len(self.assigned_route) <= 1:
            self._extend_route(steps=4, sim_time=0.0)

    def set_reservation_manager(self, rm: ReservationManager):
        self.reservation_manager = rm
        self.sync_reservations(sim_time=0.0)

    def _extend_route(self, steps: int = 3, sim_time: float = 0.0):
        """
        Extends the assigned route by choosing conflict-free valid physical continuations at junctions.
        """
        for _ in range(steps):
            last_track_id = self.assigned_route[-1]
            last_track = self.graph.tracks.get(last_track_id)
            if not last_track:
                break

            # Find exit node based on current direction
            if len(self.assigned_route) == 1:
                exit_node_id = last_track.end_node_id if self.direction >= 0 else last_track.start_node_id
            else:
                prev_track = self.graph.tracks.get(self.assigned_route[-2])
                if prev_track:
                    exit_node_id = last_track.end_node_id if prev_track.start_node_id == last_track.start_node_id or prev_track.end_node_id == last_track.start_node_id else last_track.start_node_id
                else:
                    exit_node_id = last_track.end_node_id

            # Find candidate next tracks connected to exit_node_id
            candidates: List[str] = []
            for neighbor_node_id, track_id in self.graph.adjacency.get(exit_node_id, []):
                if track_id == last_track_id:
                    continue  # Prefer not reversing immediately
                track = self.graph.tracks.get(track_id)
                if track and track.status != TrackStatus.BLOCKED:
                    candidates.append(track_id)

            if not candidates:
                break

            # Controlled randomness: Mainline (75-80%) vs Turnout (20-25%)
            mainline_candidates = [c for c in candidates if not c.startswith("T_X")]
            turnout_candidates = [c for c in candidates if c.startswith("T_X")]

            # Prioritize candidates based on topology preference
            ordered_candidates: List[str] = []
            if mainline_candidates and turnout_candidates:
                if self.rng.random() < 0.78:
                    ordered_candidates = list(mainline_candidates) + list(turnout_candidates)
                else:
                    ordered_candidates = list(turnout_candidates) + list(mainline_candidates)
            else:
                ordered_candidates = list(candidates)

            # Filter candidates using ReservationManager time-window checks
            chosen_track = None
            if self.reservation_manager:
                windows = self.predict_occupancy_windows(sim_time_offset=sim_time)
                eff_speed = max(3.0, self.speed if self.speed > 0 else self.max_speed_mps)
                accum_time = windows[-1].exit_time_sec if windows else sim_time

                for cand in ordered_candidates:
                    cand_track = self.graph.tracks.get(cand)
                    if not cand_track:
                        continue
                    c_in = accum_time
                    c_out = accum_time + (cand_track.length_m + self.length_m) / eff_speed
                    c_dir = 1 if cand_track.start_node_id == exit_node_id else -1
                    
                    is_avail, conf_tr, _ = self.reservation_manager.is_segment_available(
                        self.agent_id, cand, c_in, c_out, direction=c_dir
                    )
                    if is_avail:
                        chosen_track = cand
                        break

            if chosen_track is None:
                # If all choices have future reservations, pick first candidate but note wait requirement
                chosen_track = ordered_candidates[0]

            self.assigned_route.append(chosen_track)

        self.sync_reservations(sim_time)

    def sync_reservations(self, sim_time: float = 0.0):
        """Locks all segment and switch reservations for this train's route with the ReservationManager."""
        if not self.reservation_manager:
            return

        windows = self.predict_occupancy_windows(sim_time_offset=sim_time)
        planned_windows = [(w.track_id, w.enter_time_sec, w.exit_time_sec, w.direction) for w in windows]
        
        # Build switch list
        switches_traversed = []
        for i in range(len(windows) - 1):
            t1 = self.graph.tracks.get(windows[i].track_id)
            t2 = self.graph.tracks.get(windows[i+1].track_id)
            if t1 and t2:
                # Find common switch node
                common_nodes = {t1.start_node_id, t1.end_node_id}.intersection({t2.start_node_id, t2.end_node_id})
                for n_id in common_nodes:
                    node = self.graph.nodes.get(n_id)
                    if isinstance(node, SwitchNode):
                        sw_in = windows[i+1].enter_time_sec - 1.5
                        sw_out = windows[i+1].enter_time_sec + 2.5
                        switches_traversed.append((n_id, sw_in, sw_out, t1.track_id, t2.track_id))

        self.reservation_manager.request_route_reservations(
            self.agent_id,
            planned_windows=planned_windows,
            switches_traversed=switches_traversed
        )

    def on_route_exhausted(self, from_end: bool, overshoot: float):
        """
        Called when a train reaches the end of its assigned path. Automatically determines continuation.
        """
        current_track = self.graph.tracks.get(self.current_track_id)
        if not current_track:
            return

        exit_node_id = current_track.end_node_id if from_end else current_track.start_node_id
        candidates: List[str] = []

        for neighbor_node_id, track_id in self.graph.adjacency.get(exit_node_id, []):
            if track_id != self.current_track_id:
                track = self.graph.tracks.get(track_id)
                if track and track.status != TrackStatus.BLOCKED:
                    candidates.append(track_id)

        if candidates:
            # Pick a valid connected track
            mainline = [c for c in candidates if not c.startswith("T_X")]
            turnout = [c for c in candidates if c.startswith("T_X")]
            if mainline and turnout:
                chosen = self.rng.choice(mainline) if self.rng.random() < 0.80 else self.rng.choice(turnout)
            else:
                chosen = self.rng.choice(candidates)

            next_track = self.graph.tracks.get(chosen)
            if next_track:
                self.current_track_id = chosen
                self.assigned_route = [chosen]
                self.route_index = 0
                
                # Orient direction
                if next_track.start_node_id == exit_node_id:
                    self.direction = 1
                    self.distance_along_track_m = min(next_track.length_m, overshoot)
                else:
                    self.direction = -1
                    self.distance_along_track_m = max(0.0, next_track.length_m - overshoot)

                self._extend_route(steps=4)
                self.target_speed = self.max_speed_mps
                return

        # If at a terminal buffer node, safely reverse direction
        self.direction = -self.direction
        self.distance_along_track_m = current_track.length_m if self.direction < 0 else 0.0
        self.assigned_route = [self.current_track_id]
        self.route_index = 0
        self._extend_route(steps=4)
        self.target_speed = self.max_speed_mps

    def predict_occupancy_windows(self, sim_time_offset: float = 0.0) -> List[TrainOccupancyWindow]:
        """
        Calculates predicted [enter_time, exit_time] for all tracks in this train's route.
        """
        windows: List[TrainOccupancyWindow] = []
        eff_speed = max(3.0, self.speed if self.speed > 0 else self.max_speed_mps)

        curr_track = self.graph.tracks.get(self.current_track_id)
        if not curr_track:
            return windows

        rem_dist = (curr_track.length_m - self.distance_along_track_m) if self.direction > 0 else self.distance_along_track_m
        rem_dist = max(0.0, rem_dist)

        # Current track is occupied from now until rear clears
        t_curr_exit = sim_time_offset + (rem_dist + self.length_m) / eff_speed
        windows.append(TrainOccupancyWindow(
            track_id=self.current_track_id,
            enter_time_sec=sim_time_offset,
            exit_time_sec=t_curr_exit,
            direction=self.direction
        ))

        accum_time = sim_time_offset + rem_dist / eff_speed
        last_dir = self.direction
        last_exit_node = curr_track.end_node_id if last_dir > 0 else curr_track.start_node_id

        for tid in self.assigned_route[self.route_index + 1:]:
            track = self.graph.tracks.get(tid)
            if not track:
                continue
            entry_t = accum_time
            exit_t = accum_time + (track.length_m + self.length_m) / eff_speed
            track_dir = 1 if track.start_node_id == last_exit_node else -1
            last_exit_node = track.end_node_id if track_dir > 0 else track.start_node_id

            windows.append(TrainOccupancyWindow(
                track_id=tid,
                enter_time_sec=entry_t,
                exit_time_sec=exit_t,
                direction=track_dir
            ))
            accum_time += track.length_m / eff_speed

        return windows

    def set_route(self, route: List[str]):
        super().set_route(route)
        if hasattr(self, 'reservation_manager') and self.reservation_manager:
            self.sync_reservations(sim_time=0.0)

    def can_transition_to_track(self, next_track_id: str) -> bool:
        """
        Hard constraint: Verifies that entry into next_track_id is clear of physical trains and opposing reservations.
        """
        next_track = self.graph.tracks.get(next_track_id)
        if not next_track or next_track.status == TrackStatus.BLOCKED:
            self.status = TrainStatus.WAITING_FOR_CLEARANCE
            self.wait_reason = f"{next_track_id} BLOCKED"
            return False

        # Physical occupancy check
        for occ in next_track.occupants:
            if occ != self.agent_id:
                self.status = TrainStatus.WAITING_FOR_CLEARANCE
                self.wait_reason = f"{next_track_id} OCCUPIED BY {occ}"
                return False

        # Reservation manager check
        if self.reservation_manager:
            eff_speed = max(3.0, self.speed if self.speed > 0 else self.max_speed_mps)
            curr_track = self.graph.tracks.get(self.current_track_id)
            exit_node_id = curr_track.end_node_id if self.direction > 0 else curr_track.start_node_id
            t_dir = 1 if next_track.start_node_id == exit_node_id else -1
            t_in = 0.0
            t_out = (next_track.length_m + self.length_m) / eff_speed

            is_avail, conf_tr, _ = self.reservation_manager.is_segment_available(
                self.agent_id, next_track_id, t_in, t_out, direction=t_dir
            )
            if not is_avail:
                self.status = TrainStatus.WAITING_FOR_CLEARANCE
                self.wait_reason = f"{next_track_id} RESERVED BY {conf_tr or 'ANOTHER TRAIN'}"
                return False

        return True

    def check_headway_and_adjust_speed(self, other_trains: List['Train'], sim_time: float = 0.0):
        """Backward-compatible alias for check_traffic_and_reservations."""
        return self.check_traffic_and_reservations(other_trains, sim_time)

    def check_traffic_and_reservations(self, other_trains: List['Train'], sim_time: float):
        """
        Hard safety checks:
        1. Rear-end collision prevention on same track.
        2. Signal boundary & switch reservation check before entering next track block.
        3. Opposing train head-on collision check.
        """
        self.wait_reason = None

        # Dynamic required stopping distance
        braking_dist = (self.speed ** 2) / (2.0 * max(0.5, self.braking_mps2)) + SAFE_TRAIN_DISTANCE_M

        # Check 1: Same-track following distance & opposing train detection
        for other in other_trains:
            if other.agent_id == self.agent_id:
                continue

            if other.current_track_id == self.current_track_id:
                # Same track!
                if other.direction == self.direction:
                    # Following train in same direction
                    dist_gap = (other.distance_along_track_m - self.distance_along_track_m) if self.direction > 0 else (self.distance_along_track_m - other.distance_along_track_m)
                    if 0.0 < dist_gap < (self.length_m + braking_dist + 50.0):
                        if dist_gap < (self.length_m + SAFE_TRAIN_DISTANCE_M):
                            # Too close -> Stop completely
                            self.target_speed = 0.0
                            self.status = TrainStatus.WAITING_FOR_CLEARANCE
                            self.wait_reason = f"SAFE DISTANCE BEHIND {other.agent_id} ({dist_gap:.0f}m)"
                            return
                        else:
                            # Match lead train speed smoothly
                            self.target_speed = min(self.max_speed_mps, max(0.0, other.speed * 0.75))
                            self.status = TrainStatus.DECELERATING
                            return
                else:
                    # Opposing direction on SAME track -> Immediate full stop
                    self.target_speed = 0.0
                    self.status = TrainStatus.WAITING_FOR_CLEARANCE
                    self.wait_reason = f"OPPOSING TRAIN {other.agent_id} ON TRACK"
                    return

        # Check 2: Approaching Next Track Boundary & Switch Resource Check
        curr_track = self.graph.tracks.get(self.current_track_id)
        if curr_track and len(self.assigned_route) > self.route_index + 1:
            rem_dist = (curr_track.length_m - self.distance_along_track_m) if self.direction > 0 else self.distance_along_track_m
            next_tid = self.assigned_route[self.route_index + 1]
            next_track = self.graph.tracks.get(next_tid)

            # If within braking distance of signal boundary / switch entry
            if rem_dist <= (braking_dist + 40.0) and next_track:
                next_blocked = False
                block_reason = None
                conflict_train_name = None

                # Verify if another train occupies next_tid
                for other in other_trains:
                    if other.agent_id == self.agent_id:
                        continue
                    if other.current_track_id == next_tid:
                        next_blocked = True
                        conflict_train_name = other.agent_id
                        block_reason = f"{next_tid} OCCUPIED BY {other.agent_id}"
                        break

                if not next_blocked and self.reservation_manager:
                    eff_speed = max(3.0, self.speed if self.speed > 0 else self.max_speed_mps)
                    t_in = sim_time + (rem_dist / eff_speed)
                    t_out = t_in + (next_track.length_m + self.length_m) / eff_speed
                    exit_node_id = curr_track.end_node_id if self.direction > 0 else curr_track.start_node_id
                    t_dir = 1 if next_track.start_node_id == exit_node_id else -1

                    is_avail, conflict_train, _ = self.reservation_manager.is_segment_available(
                        self.agent_id, next_tid, t_in, t_out, direction=t_dir
                    )
                    if not is_avail:
                        next_blocked = True
                        conflict_train_name = conflict_train or "ANOTHER TRAIN"
                        block_reason = f"{next_tid} RESERVED BY {conflict_train_name}"

                if next_blocked:
                    # Evaluate candidate alternative branches at the approaching switch
                    exit_node_id = curr_track.end_node_id if self.direction > 0 else curr_track.start_node_id
                    alt_candidates = []
                    for n_id, c_tid in self.graph.adjacency.get(exit_node_id, []):
                        if c_tid not in (self.current_track_id, next_tid):
                            c_track = self.graph.tracks.get(c_tid)
                            if c_track and c_track.status != TrackStatus.BLOCKED:
                                alt_candidates.append(c_tid)

                    chosen_alt = None
                    if self.reservation_manager:
                        eff_speed = max(3.0, self.speed if self.speed > 0 else self.max_speed_mps)
                        t_in = sim_time + (rem_dist / eff_speed)
                        for alt_tid in alt_candidates:
                            alt_track = self.graph.tracks.get(alt_tid)
                            if not alt_track:
                                continue
                            t_out = t_in + (alt_track.length_m + self.length_m) / eff_speed
                            t_dir = 1 if alt_track.start_node_id == exit_node_id else -1
                            is_avail, _, _ = self.reservation_manager.is_segment_available(
                                self.agent_id, alt_tid, t_in, t_out, direction=t_dir
                            )
                            # Also ensure no physical occupant
                            if is_avail and not alt_track.occupants:
                                chosen_alt = alt_tid
                                break

                    if chosen_alt:
                        # Change track proactively through switch to maintain traffic flow
                        print(f"[{sim_time:05.1f}s] [CONFLICT] {self.agent_id} detected conflict ahead with {conflict_train_name} on {next_tid}")
                        print(f"[{sim_time:05.1f}s] [PLANNER] Evaluating alternative routes from switch {exit_node_id}...")
                        print(f"[{sim_time:05.1f}s] [TRAFFIC] {self.agent_id} -> changing from {self.current_track_id} to {chosen_alt}")
                        print(f"[{sim_time:05.1f}s] [SAFETY] Conflict resolved. Train continuing at cruising speed.")
                        self.assigned_route = [self.current_track_id, chosen_alt]
                        self.route_index = 0
                        self._extend_route(steps=3, sim_time=sim_time)
                        self.target_speed = self.max_speed_mps
                        self.status = TrainStatus.RUNNING
                        return
                    else:
                        # No alternative -> Stop before boundary
                        self.target_speed = 0.0
                        self.status = TrainStatus.WAITING_FOR_CLEARANCE
                        self.wait_reason = block_reason
                        return

        # Track is completely free -> Maintain normal cruising speed
        if self.target_speed == 0.0 and self.status == TrainStatus.WAITING_FOR_CLEARANCE:
            self.target_speed = self.max_speed_mps
            self.status = TrainStatus.RUNNING

    def step(self, dt: float, other_trains: Optional[List['Train']] = None, sim_time: float = 0.0):
        # Auto-maintain forward route lookahead
        if self.autonomous_routing and (len(self.assigned_route) - self.route_index) < 3:
            self._extend_route(steps=3, sim_time=sim_time)

        # Anti-collision & reservation checks
        if other_trains:
            self.check_traffic_and_reservations(other_trains, sim_time)

        # Release segments that have been physically cleared
        if self.reservation_manager:
            self.reservation_manager.release_cleared_segments(self.agent_id, self.current_track_id, sim_time)

        self.update_kinematics(dt)
        if self.speed == 0.0 and self.target_speed == 0.0:
            if self.status != TrainStatus.WAITING_FOR_CLEARANCE:
                self.status = TrainStatus.STOPPED
        elif self.speed < self.target_speed:
            self.status = TrainStatus.RUNNING
        elif self.speed > self.target_speed:
            self.status = TrainStatus.DECELERATING
        else:
            self.status = TrainStatus.RUNNING

    def get_telemetry(self) -> TrainTelemetry:
        next_tid = None
        next_entry_eta = None
        next_exit_eta = None

        windows = self.predict_occupancy_windows()
        if len(windows) > 1:
            next_tid = windows[1].track_id
            next_entry_eta = windows[1].enter_time_sec
            next_exit_eta = windows[1].exit_time_sec

        res_desc = "SAFE" if not self.wait_reason else "WAITING"

        return TrainTelemetry(
            train_id=self.agent_id,
            timestamp=time.time(),
            x=self.x,
            y=self.y,
            speed=self.speed,
            target_speed=self.target_speed,
            heading=self.heading,
            current_track_id=self.current_track_id,
            distance_along_track_m=self.distance_along_track_m,
            length_m=self.length_m,
            status=self.status,
            assigned_route=list(self.assigned_route),
            next_track_id=next_tid,
            next_entry_eta_sec=next_entry_eta,
            next_exit_eta_sec=next_exit_eta,
            reservation_status=res_desc,
            wait_reason=self.wait_reason
        )
