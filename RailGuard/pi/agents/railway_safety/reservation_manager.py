"""
Railway Segment & Switch Resource Reservation System
Guarantees Time-Window Mutual Exclusion and Headway Separation for All Rolling Stock
"""
import time
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Set, Tuple
from config import MIN_TRAIN_HEADWAY_SECONDS, SAFE_TRAIN_DISTANCE_M
from world.graph import RailwayGraph, SwitchNode

@dataclass
class SegmentReservation:
    train_id: str
    track_id: str
    entry_time: float
    exit_time: float
    direction: int  # +1 (start->end) or -1 (end->start)
    granted_at: float = field(default_factory=time.time)

@dataclass
class SwitchReservation:
    train_id: str
    switch_id: str
    entry_time: float
    exit_time: float
    from_track_id: str
    to_track_id: str

class ReservationManager:
    """
    Central Coordinator managing railway block reservations.
    Enforces time-window mutual exclusion, switch clearance, and headway margins.
    """
    def __init__(self, graph: RailwayGraph, min_headway_sec: float = MIN_TRAIN_HEADWAY_SECONDS):
        self.graph = graph
        self.min_headway_sec = min_headway_sec
        # track_id -> List[SegmentReservation]
        self.track_reservations: Dict[str, List[SegmentReservation]] = {}
        # switch_id -> List[SwitchReservation]
        self.switch_reservations: Dict[str, List[SwitchReservation]] = {}

    def is_segment_available(
        self,
        train_id: str,
        track_id: str,
        entry_time: float,
        exit_time: float,
        direction: int = 1,
        headway_sec: Optional[float] = None
    ) -> Tuple[bool, Optional[str], float]:
        """
        Evaluates whether a track segment can be safely reserved.
        Returns: (is_available, conflicting_train_id, conflict_margin_sec)
        """
        h_sec = headway_sec if headway_sec is not None else self.min_headway_sec
        existing = self.track_reservations.get(track_id, [])

        for res in existing:
            if res.train_id == train_id:
                continue

            # 1. Opposing Direction Conflict (Head-on)
            if res.direction != direction:
                # Any overlap with buffer is strictly forbidden
                if max(entry_time - h_sec, res.entry_time - h_sec) < min(exit_time + h_sec, res.exit_time + h_sec):
                    margin = min(abs(entry_time - res.exit_time), abs(res.entry_time - exit_time))
                    return False, res.train_id, margin

            # 2. Same Direction (Following Headway)
            # Both entry and exit must satisfy minimum headway
            if max(entry_time, res.entry_time) < min(exit_time, res.exit_time):
                # Direct time overlap on single track
                margin = 0.0
                return False, res.train_id, margin

            # Separation buffer check
            if entry_time < res.exit_time + h_sec and exit_time > res.entry_time - h_sec:
                margin = min(abs(entry_time - res.exit_time), abs(res.entry_time - exit_time))
                if margin < h_sec:
                    return False, res.train_id, margin

        return True, None, float('inf')

    def is_switch_available(
        self,
        train_id: str,
        switch_id: str,
        entry_time: float,
        exit_time: float,
        from_track_id: str,
        to_track_id: str,
        headway_sec: Optional[float] = None
    ) -> Tuple[bool, Optional[str]]:
        """
        Evaluates whether a switch node is available for traversal.
        """
        h_sec = headway_sec if headway_sec is not None else (self.min_headway_sec * 0.5)
        existing = self.switch_reservations.get(switch_id, [])

        for res in existing:
            if res.train_id == train_id:
                continue
            # Overlapping switch occupation window
            if max(entry_time - h_sec, res.entry_time - h_sec) < min(exit_time + h_sec, res.exit_time + h_sec):
                return False, res.train_id

        return True, None

    def request_route_reservations(
        self,
        train_id: str,
        planned_windows: List[Tuple[str, float, float, int]],  # (track_id, entry_time, exit_time, direction)
        switches_traversed: Optional[List[Tuple[str, float, float, str, str]]] = None # (switch_id, enter, exit, from_t, to_t)
    ) -> Tuple[bool, Optional[str], Optional[str]]:
        """
        Atomically tests and grants all reservations for an entire forward lookahead route.
        Returns: (success, conflicting_train_id, conflicting_resource_id)
        """
        # Step 1: Dry-run check on all segments
        for tid, t_in, t_out, direction in planned_windows:
            avail, conflict_train, _ = self.is_segment_available(train_id, tid, t_in, t_out, direction)
            if not avail:
                return False, conflict_train, tid

        # Step 2: Dry-run check on all switches
        if switches_traversed:
            for sw_id, sw_in, sw_out, from_t, to_t in switches_traversed:
                avail, conflict_train = self.is_switch_available(train_id, sw_id, sw_in, sw_out, from_t, to_t)
                if not avail:
                    return False, conflict_train, sw_id

        # Step 3: Grant and lock all reservations atomically
        self.release_train_reservations(train_id)

        for tid, t_in, t_out, direction in planned_windows:
            if tid not in self.track_reservations:
                self.track_reservations[tid] = []
            self.track_reservations[tid].append(SegmentReservation(
                train_id=train_id,
                track_id=tid,
                entry_time=t_in,
                exit_time=t_out,
                direction=direction
            ))

        if switches_traversed:
            for sw_id, sw_in, sw_out, from_t, to_t in switches_traversed:
                if sw_id not in self.switch_reservations:
                    self.switch_reservations[sw_id] = []
                self.switch_reservations[sw_id].append(SwitchReservation(
                    train_id=train_id,
                    switch_id=sw_id,
                    entry_time=sw_in,
                    exit_time=sw_out,
                    from_track_id=from_t,
                    to_track_id=to_t
                ))

        return True, None, None

    def release_train_reservations(self, train_id: str):
        """Releases all reservations currently held by this train."""
        for tid in list(self.track_reservations.keys()):
            self.track_reservations[tid] = [r for r in self.track_reservations[tid] if r.train_id != train_id]
            if not self.track_reservations[tid]:
                del self.track_reservations[tid]

        for sw_id in list(self.switch_reservations.keys()):
            self.switch_reservations[sw_id] = [r for r in self.switch_reservations[sw_id] if r.train_id != train_id]
            if not self.switch_reservations[sw_id]:
                del self.switch_reservations[sw_id]

    def release_cleared_segments(self, train_id: str, current_track_id: str, sim_time: float):
        """Releases track segments that the train has already physically cleared."""
        for tid in list(self.track_reservations.keys()):
            # Keep current track and future tracks; release tracks where exit_time < sim_time and tid != current_track_id
            retained = []
            for r in self.track_reservations[tid]:
                if r.train_id == train_id:
                    if r.track_id == current_track_id or r.exit_time > sim_time:
                        retained.append(r)
                else:
                    retained.append(r)
            if retained:
                self.track_reservations[tid] = retained
            else:
                del self.track_reservations[tid]

    def get_track_status(self, track_id: str, sim_time: float) -> str:
        """Returns clean status string for HUD / visualization."""
        res_list = self.track_reservations.get(track_id, [])
        if not res_list:
            return "FREE"
        active = [r for r in res_list if r.entry_time <= sim_time <= r.exit_time]
        if active:
            return f"OCCUPIED ({active[0].train_id})"
        future = [r for r in res_list if r.entry_time > sim_time]
        if future:
            earliest = min(future, key=lambda x: x.entry_time)
            return f"RESERVED ({earliest.train_id} @ {earliest.entry_time:.0f}s)"
        return "CLEARING"

    def get_all_reservations(self) -> Dict[str, List[SegmentReservation]]:
        """Returns copy of all active segment reservations."""
        return {k: list(v) for k, v in self.track_reservations.items()}
