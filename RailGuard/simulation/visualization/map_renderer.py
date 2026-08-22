"""
High-Performance Railway Map Renderer with State-Aware Colors and In-Place Dynamic Artist Updates
"""
import math
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from typing import Dict, List, Optional
from config import TrackStatus, RobotStatus, TrainStatus, AnomalySeverity, AnomalyStatus
from world.graph import RailwayGraph, SwitchNode
from server.decision_engine import DecisionEngine

class MapRenderer:
    """
    Renders topological railway network using optimized in-place artist updates.
    Color coding:
      Trains: Blue (Normal), Yellow/Orange (Slowing/Yielding), Red (Waiting/Stopped)
      Robot: Green (Transit), Cyan (Changing Track), Yellow (Yielding), Orange (Waiting), Purple (Inspecting)
    """
    def __init__(self, ax: plt.Axes, graph: RailwayGraph):
        self.ax = ax
        self.graph = graph
        self.toast_text_timer: float = 0.0

        # Setup static aesthetic railway network
        self._setup_static_layers()
        self._setup_dynamic_artists()

    def _setup_static_layers(self):
        self.ax.set_facecolor('#0B0F19')
        self.track_lines: Dict[str, plt.Line2D] = {}

        # 1. Base Railway Tracks
        for track_id, track in self.graph.tracks.items():
            xs = [p[0] for p in track.geometry]
            ys = [p[1] for p in track.geometry]

            # Outer ballast line
            line_outer, = self.ax.plot(xs, ys, color='#334155', linewidth=3.0, zorder=2)
            # Inner rail line
            line_inner, = self.ax.plot(xs, ys, color='#64748B', linewidth=1.2, zorder=3)
            self.track_lines[track_id] = line_outer

            # Track Label at midpoint
            mid_idx = len(track.geometry) // 2
            mid_pt = track.geometry[mid_idx]
            self.ax.text(
                mid_pt[0], mid_pt[1] + 4.5, f"{track_id}",
                color='#94A3B8', fontsize=8, fontweight='bold',
                ha='center', va='bottom', zorder=4
            )

        # 2. Static Nodes & Switches
        for node_id, node in self.graph.nodes.items():
            if isinstance(node, SwitchNode):
                self.ax.scatter(node.x, node.y, s=70, c='#F59E0B', edgecolors='#FFFFFF', linewidth=1.2, zorder=6)
                self.ax.text(node.x, node.y - 7.0, f"SW {node_id}", color='#FCD34D', fontsize=7.5, fontweight='bold', ha='center', zorder=6)
            elif node.node_type == "STATION":
                self.ax.scatter(node.x, node.y, s=90, c='#3B82F6', marker='s', edgecolors='#93C5FD', linewidth=1.2, zorder=6)
                self.ax.text(node.x, node.y - 7.0, node.name, color='#93C5FD', fontsize=7, ha='center', zorder=6)
            elif node.node_type == "BUFFER":
                self.ax.scatter(node.x, node.y, s=50, c='#6B7280', marker='|', linewidth=2.0, zorder=6)

        self.ax.set_xlim(-40.0, 800.0)
        self.ax.set_ylim(-70.0, 95.0)
        self.ax.set_xlabel("Railway Network Easting (Meters) [Click on map to set Destination Anomaly]", color='#94A3B8', fontsize=9)
        self.ax.set_ylabel("Northing (Meters)", color='#94A3B8', fontsize=9)
        self.ax.tick_params(colors='#64748B', labelsize=8)
        self.ax.grid(True, linestyle=':', color='#1E293B', alpha=0.7)
        self.ax.set_title("RAILGUARD AI - AUTONOMOUS RAILWAY DIGITAL TWIN & TRAFFIC INTERACTION", color='#F8FAFC', fontsize=10.5, fontweight='bold', pad=10)

    def _setup_dynamic_artists(self):
        # Dynamic Route Lines (Green is live dynamic route)
        self.old_route_line, = self.ax.plot([], [], color='#EF4444', linewidth=3.0, linestyle=':', alpha=0.65, zorder=4)
        self.active_route_line, = self.ax.plot([], [], color='#10B981', linewidth=3.5, linestyle='--', alpha=0.9, zorder=5)

        # Robot Artists
        self.robot_scatter = self.ax.scatter([], [], s=200, c='#10B981', marker='o', edgecolors='#FFFFFF', linewidth=2.5, zorder=13)
        self.robot_badge = self.ax.text(0, 0, "", color='#A7F3D0', fontsize=8, fontweight='bold', ha='center',
                                        bbox=dict(boxstyle="round,pad=0.2", fc='#064E3B', ec='#10B981', lw=1), zorder=15, visible=False)
        self.robot_radar = patches.Circle((0, 0), radius=16.0, color='#8B5CF6', alpha=0.35, zorder=9, visible=False)
        self.ax.add_patch(self.robot_radar)

        # Dynamic Train Artists (Exactly 4 trains)
        self.train_lines: List[plt.Line2D] = []
        self.train_scatters: List[any] = []
        self.train_badges: List[any] = []

        for _ in range(4):
            t_line, = self.ax.plot([], [], color='#38BDF8', linewidth=5.5, solid_capstyle='round', zorder=10)
            t_scat = self.ax.scatter([], [], s=120, c='#0284C7', marker='o', edgecolors='#BAE6FD', linewidth=1.8, zorder=11)
            t_badge = self.ax.text(0, 0, "", color='#38BDF8', fontsize=7.5, fontweight='bold', ha='center',
                                   bbox=dict(boxstyle="round,pad=0.2", fc='#0C4A6E', ec='#38BDF8', lw=1), zorder=12, visible=False)
            self.train_lines.append(t_line)
            self.train_scatters.append(t_scat)
            self.train_badges.append(t_badge)

        # Dynamic Anomaly Artists
        self.anomaly_scatters: List[any] = []
        self.anomaly_badges: List[any] = []
        for _ in range(4):
            a_scat = self.ax.scatter([], [], s=180, c='#EAB308', marker='^', edgecolors='#FFFFFF', linewidth=2.0, zorder=8)
            a_badge = self.ax.text(0, 0, "", color='#FFFFFF', fontsize=8, fontweight='bold', ha='center',
                                   bbox=dict(boxstyle="round,pad=0.3", fc='#1F2937', ec='#EAB308', lw=1.5), zorder=9, visible=False)
            self.anomaly_scatters.append(a_scat)
            self.anomaly_badges.append(a_badge)

        # Toast notification for click rejection
        self.toast_badge = self.ax.text(375.0, 75.0, "", color='#F87171', fontsize=9.5, fontweight='bold', ha='center',
                                        bbox=dict(boxstyle="round,pad=0.3", fc='#7F1D1D', ec='#EF4444', lw=1.5), zorder=20, visible=False)

    def show_toast(self, message: str, duration_sec: float = 3.0):
        self.toast_badge.set_text(message)
        self.toast_badge.set_visible(True)
        self.toast_text_timer = duration_sec

    def update(self, engine: DecisionEngine, dt: float = 0.05):
        """
        In-place artist updates - ultra fast 30+ FPS with zero redraw overhead.
        """
        # 1. Update Toast timer
        if self.toast_text_timer > 0:
            self.toast_text_timer -= dt
            if self.toast_text_timer <= 0:
                self.toast_badge.set_visible(False)

        # 2. Update Track Colors if Blocked or Restricted
        for tid, track in self.graph.tracks.items():
            if tid in self.track_lines:
                if track.status == TrackStatus.BLOCKED:
                    self.track_lines[tid].set_color('#EF4444')
                    self.track_lines[tid].set_linewidth(4.0)
                    self.track_lines[tid].set_linestyle('--')
                elif track.status == TrackStatus.RESTRICTED:
                    self.track_lines[tid].set_color('#F59E0B')
                    self.track_lines[tid].set_linewidth(3.0)
                    self.track_lines[tid].set_linestyle('-.')

        # 3. Update Dynamic Green Route Line
        if engine.old_route and engine.has_replanned_for_conflict:
            old_xs = [p[0] for p in engine.old_route.polyline_geometry]
            old_ys = [p[1] for p in engine.old_route.polyline_geometry]
            self.old_route_line.set_data(old_xs, old_ys)
            self.old_route_line.set_visible(True)
        else:
            self.old_route_line.set_visible(False)

        if engine.current_route and engine.robot and engine.robot.active_mission_id:
            r_xs = [p[0] for p in engine.current_route.polyline_geometry]
            r_ys = [p[1] for p in engine.current_route.polyline_geometry]
            route_color = '#10B981' if engine.current_route.is_safe else '#F59E0B'
            self.active_route_line.set_data(r_xs, r_ys)
            self.active_route_line.set_color(route_color)
            self.active_route_line.set_visible(True)
        else:
            self.active_route_line.set_visible(False)

        # 4. Update Robot with State-Based Colors
        if engine.robot:
            rob = engine.robot
            self.robot_scatter.set_offsets([[rob.x, rob.y]])
            
            # Robot Color Mapping
            if rob.status == RobotStatus.INSPECTING:
                rob_color = '#8B5CF6'  # Purple (Inspecting)
            elif rob.status == RobotStatus.CHANGING_TRACK:
                rob_color = '#06B6D4'  # Cyan (Changing track to make way for train)
            elif rob.status == RobotStatus.YIELDING_TO_TRAIN:
                rob_color = '#EAB308'  # Yellow (Yielding)
            elif rob.status == RobotStatus.WAITING_FOR_CLEARANCE:
                rob_color = '#F97316'  # Orange (Waiting for clearance)
            elif rob.status == RobotStatus.EMERGENCY_STOP:
                rob_color = '#EF4444'  # Red (Emergency safety stop)
            elif rob.status == RobotStatus.IDLE:
                rob_color = '#64748B'  # Gray (Idle)
            else:
                rob_color = '#10B981'  # Green (Normal Transit)

            self.robot_scatter.set_color(rob_color)
            self.robot_scatter.set_visible(True)

            self.robot_badge.set_position((rob.x, rob.y - 12.0))
            self.robot_badge.set_text(f"[{rob.agent_id}] {rob.status.value}")
            self.robot_badge.set_visible(True)

            if rob.status == RobotStatus.INSPECTING:
                self.robot_radar.set_center((rob.x, rob.y))
                self.robot_radar.set_visible(True)
            else:
                self.robot_radar.set_visible(False)
        else:
            self.robot_scatter.set_visible(False)
            self.robot_badge.set_visible(False)
            self.robot_radar.set_visible(False)

        # 5. Update Trains with State-Based Colors (Blue=Normal, Yellow=Slowing/Yielding, Red=Stopped)
        for i in range(len(self.train_lines)):
            if i < len(engine.trains):
                train = engine.trains[i]
                dx = math.cos(train.heading) * train.length_m
                dy = math.sin(train.heading) * train.length_m
                tail_x = train.x - dx
                tail_y = train.y - dy

                # Train Color Mapping
                if train.status in (TrainStatus.WAITING_FOR_CLEARANCE, TrainStatus.STOPPED):
                    t_color = '#EF4444'  # Red (Waiting / Stopped)
                    dot_color = '#F87171'
                elif train.status in (TrainStatus.DECELERATING, TrainStatus.YIELDING):
                    t_color = '#F59E0B'  # Yellow/Orange (Slowing / Yielding)
                    dot_color = '#FCD34D'
                else:
                    t_color = '#38BDF8'  # Blue (Normal Running)
                    dot_color = '#0284C7'

                self.train_lines[i].set_data([tail_x, train.x], [tail_y, train.y])
                self.train_lines[i].set_color(t_color)
                self.train_lines[i].set_visible(True)

                self.train_scatters[i].set_offsets([[train.x, train.y]])
                self.train_scatters[i].set_color(dot_color)
                self.train_scatters[i].set_visible(True)

                self.train_badges[i].set_position((train.x, train.y + 7.5))
                self.train_badges[i].set_text(f"[{train.agent_id}] {train.speed*3.6:.0f} km/h")
                self.train_badges[i].set_visible(True)
            else:
                self.train_lines[i].set_visible(False)
                self.train_scatters[i].set_visible(False)
                self.train_badges[i].set_visible(False)

        # 6. Update Anomalies
        anom_list = list(engine.anomalies.anomalies.values())
        for i in range(len(self.anomaly_scatters)):
            if i < len(anom_list):
                anom = anom_list[i]
                self.anomaly_scatters[i].set_offsets([[anom.x, anom.y]])
                
                if anom.severity == AnomalySeverity.CRITICAL:
                    a_color = '#EF4444'
                    a_text = f"[CRITICAL] {anom.anomaly_id} [BROKEN RAIL]"
                elif anom.severity == AnomalySeverity.HIGH:
                    a_color = '#F97316'
                    a_text = f"[HIGH] {anom.anomaly_id} [DEFECT]"
                else:
                    a_color = '#EAB308'
                    a_text = f"[DESTINATION] {anom.anomaly_id} (KM {anom.chainage_km:.2f})"

                self.anomaly_scatters[i].set_color(a_color)
                self.anomaly_scatters[i].set_visible(True)

                self.anomaly_badges[i].set_position((anom.x, anom.y + 11.0))
                self.anomaly_badges[i].set_text(a_text)
                self.anomaly_badges[i].set_visible(True)
            else:
                self.anomaly_scatters[i].set_visible(False)
                self.anomaly_badges[i].set_visible(False)

    def render(self, engine: DecisionEngine):
        """Backward compatibility wrapper."""
        self.update(engine)
