"""
Time-Aware Multi-factor A* and Graph Path Planner for Railway Network
Evaluates Distance, Travel Time, Expected Waiting Time, Hard Safety Constraints, and Switch Penalties.
"""
from __future__ import annotations
import heapq
import math
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Set, Tuple, TYPE_CHECKING
from config import TrackStatus, SWITCH_TRAVERSAL_PENALTY_SEC, MIN_HEADWAY_SECONDS
from world.graph import RailwayGraph, TrackSegment, SwitchNode

if TYPE_CHECKING:
    from entities.train import Train

@dataclass
class RoutePlan:
    route_id: str
    track_ids: List[str]
    total_distance_m: float
    estimated_time_sec: float
    expected_wait_sec: float
    polyline_geometry: List[Tuple[float, float]]
    is_safe: bool = True
    conflict_count: int = 0
    safety_margin_sec: float = float('inf')
    is_valid: bool = True
    action_description: str = "PROCEEDING"
    reason: str = "Optimal shortest safe path"

class PathPlanner:
    """
    Time-aware Railway Route Planner calculating the shortest safe trajectory
    considering train occupancy windows and intelligent waiting vs detour trade-offs.
    """
    def __init__(self, graph: RailwayGraph, min_headway_sec: float = MIN_HEADWAY_SECONDS):
        self.graph = graph
        self.min_headway_sec = min_headway_sec

    def find_route(
        self,
        start_track_id: str,
        target_track_id: str,
        agent_speed_mps: float = 4.5,
        avoid_track_ids: Optional[Set[str]] = None
    ) -> Optional[RoutePlan]:
        """Backward-compatible alias for find_shortest_safe_route."""
        return self.find_shortest_safe_route(
            start_track_id=start_track_id,
            target_track_id=target_track_id,
            agent_speed_mps=agent_speed_mps,
            avoid_track_ids=avoid_track_ids
        )

    def find_shortest_safe_route(
        self,
        start_track_id: str,
        target_track_id: str,
        agent_speed_mps: float = 4.5,
        trains: Optional[List[Train]] = None,
        avoid_track_ids: Optional[Set[str]] = None,
        start_time_offset_sec: float = 0.0,
        target_coords: Optional[Tuple[float, float]] = None
    ) -> Optional[RoutePlan]:
        """
        Finds the lowest-cost 100% safe route from start_track to target_track.
        Considers travel time, detour distance, and intelligent wait times before conflicting blocks.
        """
        if avoid_track_ids is None:
            avoid_track_ids = set()
        if trains is None:
            trains = []

        # 1. Compile all train occupancy reservation windows [t_enter - headway, t_exit + headway]
        train_reservations: Dict[str, List[Tuple[float, float]]] = {}
        for tr in trains:
            for win in tr.predict_occupancy_windows(sim_time_offset=start_time_offset_sec):
                if win.track_id not in train_reservations:
                    train_reservations[win.track_id] = []
                res_in = max(0.0, win.enter_time_sec - self.min_headway_sec)
                res_out = win.exit_time_sec + self.min_headway_sec
                train_reservations[win.track_id].append((res_in, res_out))

        start_track = self.graph.tracks.get(start_track_id)
        target_track = self.graph.tracks.get(target_track_id)
        if not start_track or not target_track:
            return None

        # Check direct single-track arrival
        if start_track_id == target_track_id:
            dur = start_track.length_m / agent_speed_mps
            has_conf, min_margin = self._check_track_conflict(start_track_id, start_time_offset_sec, start_time_offset_sec + dur, train_reservations)
            poly = list(start_track.geometry)
            if target_coords:
                poly = self._clip_geometry_to_target(poly, target_coords)
            
            return RoutePlan(
                route_id="DIRECT",
                track_ids=[start_track_id],
                total_distance_m=start_track.length_m,
                estimated_time_sec=dur,
                expected_wait_sec=0.0,
                polyline_geometry=poly,
                is_safe=not has_conf,
                conflict_count=1 if has_conf else 0,
                safety_margin_sec=min_margin,
                action_description="PROCEEDING",
                reason="Direct track arrival"
            )

        # Priority Queue: (cost, dist, current_time, total_wait_time, current_node_id, path_track_ids)
        # Cost = distance + (wait_time * speed_equivalent) + switch_penalties
        pq: List[Tuple[float, float, float, float, str, List[str]]] = []
        visited: Dict[Tuple[str, int], float] = {}

        # Initialize from start track endpoints
        init_dur = start_track.length_m / agent_speed_mps
        pq.append((start_track.length_m, start_track.length_m, start_time_offset_sec + init_dur, 0.0, start_track.end_node_id, [start_track_id]))
        if start_track.is_bidirectional:
            pq.append((start_track.length_m, start_track.length_m, start_time_offset_sec + init_dur, 0.0, start_track.start_node_id, [start_track_id]))

        best_safe_plan: Optional[RoutePlan] = None

        while pq:
            cost, dist, curr_time, total_wait, curr_node_id, path = heapq.heappop(pq)

            last_tid = path[-1]
            if last_tid == target_track_id:
                plan = self._assemble_time_route(
                    path, agent_speed_mps, start_time_offset_sec, train_reservations, target_coords, total_wait
                )
                if plan.is_safe:
                    return plan  # Guaranteed optimal cost safe route (A* optimality)

            v_key = (curr_node_id, len(path))
            if v_key in visited and visited[v_key] <= cost:
                continue
            visited[v_key] = cost

            # Explore outgoing candidate tracks
            for neighbor_node_id, track_id in self.graph.adjacency.get(curr_node_id, []):
                if track_id in path:
                    continue  # Avoid loops
                if track_id in avoid_track_ids:
                    continue

                track = self.graph.tracks.get(track_id)
                if not track or track.status == TrackStatus.BLOCKED:
                    continue

                seg_dur = track.length_m / agent_speed_mps
                switch_pen = (SWITCH_TRAVERSAL_PENALTY_SEC * agent_speed_mps) if isinstance(self.graph.nodes.get(curr_node_id), SwitchNode) else 0.0

                # 1. Option: Direct entry without waiting
                arr_time = curr_time
                dep_time = curr_time + seg_dur
                has_conf, _ = self._check_track_conflict(track_id, arr_time, dep_time, train_reservations)

                if not has_conf:
                    # Clean passage with 0s wait
                    new_dist = dist + track.length_m
                    new_cost = cost + track.length_m + switch_pen
                    heapq.heappush(pq, (new_cost, new_dist, dep_time, total_wait, neighbor_node_id, path + [track_id]))
                else:
                    # 2. Option: Waiting before conflict area until the train reservation clears
                    # Find when the track clears
                    clear_time = self._get_reservation_clear_time(track_id, arr_time, train_reservations)
                    if clear_time is not None and clear_time > arr_time:
                        wait_sec = clear_time - arr_time
                        if wait_sec <= 90.0:  # Sensible upper bound on holding time
                            new_arr = clear_time
                            new_dep = new_arr + seg_dur
                            # Verify cleared window has no secondary conflict
                            has_sec_conf, _ = self._check_track_conflict(track_id, new_arr, new_dep, train_reservations)
                            if not has_sec_conf:
                                wait_cost = wait_sec * agent_speed_mps * 1.2  # Slight wait penalty weight
                                new_dist = dist + track.length_m
                                new_cost = cost + track.length_m + switch_pen + wait_cost
                                heapq.heappush(pq, (new_cost, new_dist, new_dep, total_wait + wait_sec, neighbor_node_id, path + [track_id]))

        # Fallback: If no completely safe route exists, return standard shortest path marked with conflict
        return self._find_fallback_route(start_track_id, target_track_id, agent_speed_mps, start_time_offset_sec, train_reservations, target_coords)

    def _get_reservation_clear_time(self, track_id: str, current_time: float, reservations: Dict[str, List[Tuple[float, float]]]) -> Optional[float]:
        """Finds the timestamp when all active reservations covering current_time clear."""
        windows = reservations.get(track_id, [])
        max_clear = current_time
        for r_in, r_out in windows:
            if r_in <= current_time <= r_out or (current_time <= r_in and r_in - current_time <= 15.0):
                max_clear = max(max_clear, r_out)
        return max_clear if max_clear > current_time else None

    def _check_track_conflict(
        self,
        track_id: str,
        t_enter: float,
        t_exit: float,
        reservations: Dict[str, List[Tuple[float, float]]]
    ) -> Tuple[bool, float]:
        """Returns (has_conflict, min_headway_margin_sec)."""
        windows = reservations.get(track_id, [])
        if not windows:
            return False, float('inf')

        min_margin = float('inf')
        for r_in, r_out in windows:
            # Overlap check
            if max(t_enter, r_in) < min(t_exit, r_out):
                return True, 0.0
            margin = min(abs(t_enter - r_out), abs(r_in - t_exit))
            if margin < min_margin:
                min_margin = margin

        return False, min_margin

    def _clip_geometry_to_target(self, geometry: List[Tuple[float, float]], target_coords: Tuple[float, float]) -> List[Tuple[float, float]]:
        """Clips the polyline path so it terminates cleanly at the target anomaly coordinates."""
        if not geometry:
            return [target_coords]
        tx, ty = target_coords
        # Find closest point along polyline
        best_idx = 0
        min_dist_sq = float('inf')
        for i, pt in enumerate(geometry):
            d_sq = (pt[0] - tx)**2 + (pt[1] - ty)**2
            if d_sq < min_dist_sq:
                min_dist_sq = d_sq
                best_idx = i

        clipped = list(geometry[:best_idx + 1])
        if not clipped or (clipped[-1][0] != tx or clipped[-1][1] != ty):
            clipped.append((tx, ty))
        return clipped

    def _assemble_time_route(
        self,
        track_ids: List[str],
        agent_speed_mps: float,
        start_time_offset_sec: float,
        reservations: Dict[str, List[Tuple[float, float]]],
        target_coords: Optional[Tuple[float, float]] = None,
        expected_wait_sec: float = 0.0
    ) -> RoutePlan:
        """Assembles seamless continuous polyline geometry and verifies end-to-end timing."""
        combined_poly: List[Tuple[float, float]] = []
        total_dist = 0.0
        curr_time = start_time_offset_sec
        conf_count = 0
        min_margin = float('inf')

        for i, tid in enumerate(track_ids):
            track = self.graph.tracks.get(tid)
            if not track:
                continue
            seg_dur = track.length_m / agent_speed_mps
            has_conf, margin = self._check_track_conflict(tid, curr_time, curr_time + seg_dur, reservations)
            if has_conf:
                # Add wait time until clear
                clear_t = self._get_reservation_clear_time(tid, curr_time, reservations)
                if clear_t is not None and clear_t > curr_time:
                    wait_dur = clear_t - curr_time
                    expected_wait_sec += wait_dur
                    curr_time = clear_t
                    has_conf, margin = self._check_track_conflict(tid, curr_time, curr_time + seg_dur, reservations)
            
            if has_conf:
                conf_count += 1
            if margin < min_margin:
                min_margin = margin

            curr_time += seg_dur
            total_dist += track.length_m

            # Align segment geometry smoothly
            seg_pts = list(track.geometry)
            if combined_poly:
                last_pt = combined_poly[-1]
                d_fwd = (last_pt[0] - seg_pts[0][0])**2 + (last_pt[1] - seg_pts[0][1])**2
                d_rev = (last_pt[0] - seg_pts[-1][0])**2 + (last_pt[1] - seg_pts[-1][1])**2
                if d_rev < d_fwd:
                    seg_pts = list(reversed(seg_pts))
                if combined_poly and seg_pts and combined_poly[-1] == seg_pts[0]:
                    combined_poly.extend(seg_pts[1:])
                else:
                    combined_poly.extend(seg_pts)
            else:
                combined_poly.extend(seg_pts)

        if target_coords:
            combined_poly = self._clip_geometry_to_target(combined_poly, target_coords)

        est_time = (total_dist / agent_speed_mps) + expected_wait_sec
        action_desc = "WAITING FOR TRAIN" if expected_wait_sec > 0 else "PROCEEDING"

        return RoutePlan(
            route_id="TIME_A_STAR",
            track_ids=track_ids,
            total_distance_m=total_dist,
            estimated_time_sec=est_time,
            expected_wait_sec=expected_wait_sec,
            polyline_geometry=combined_poly,
            is_safe=(conf_count == 0),
            conflict_count=conf_count,
            safety_margin_sec=min_margin,
            action_description=action_desc,
            reason="Optimal time-aware shortest safe route"
        )

    def _find_fallback_route(
        self,
        start_track_id: str,
        target_track_id: str,
        agent_speed_mps: float,
        start_time_offset_sec: float,
        reservations: Dict[str, List[Tuple[float, float]]],
        target_coords: Optional[Tuple[float, float]] = None
    ) -> Optional[RoutePlan]:
        """Fallback shortest topological path if all candidates temporarily conflict."""
        # Simple Dijkstra on distance
        pq = [(0.0, self.graph.tracks[start_track_id].end_node_id, [start_track_id])]
        visited = set()

        while pq:
            dist, node_id, path = heapq.heappop(pq)
            if path[-1] == target_track_id:
                return self._assemble_time_route(path, agent_speed_mps, start_time_offset_sec, reservations, target_coords)

            if node_id in visited:
                continue
            visited.add(node_id)

            for neighbor, tid in self.graph.adjacency.get(node_id, []):
                if tid not in path:
                    track = self.graph.tracks.get(tid)
                    if track and track.status != TrackStatus.BLOCKED:
                        heapq.heappush(pq, (dist + track.length_m, neighbor, path + [tid]))

        return None
