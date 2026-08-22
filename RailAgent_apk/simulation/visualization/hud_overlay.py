"""
HUD Overlay - Renders Live Tactical Telemetry, Train Reservations, Dynamic Route State & Traffic Status
"""
import time
import math
import matplotlib.pyplot as plt
from config import RobotStatus, TrainStatus, TrackStatus, AnomalySeverity, MissionStatus, SafetyLevel
from server.decision_engine import DecisionEngine

class HUDOverlay:
    """
    Renders structured tactical heads-up display panels next to the railway map.
    """
    def __init__(self, ax: plt.Axes):
        self.ax = ax
        self.sim_speed_multiplier = 10.0

    def set_speed_multiplier(self, mult: float):
        self.sim_speed_multiplier = mult

    def render(self, engine: DecisionEngine):
        self.ax.clear()
        self.ax.set_facecolor('#070A13')
        self.ax.axis('off')

        y_pos = 0.97
        line_height = 0.036

        def draw_header(title: str, color='#38BDF8'):
            nonlocal y_pos
            self.ax.text(0.02, y_pos, title, color=color, fontsize=8.8, fontweight='bold', va='top')
            y_pos -= line_height * 1.05

        def draw_kv(key: str, val: str, val_color='#E2E8F0'):
            nonlocal y_pos
            self.ax.text(0.04, y_pos, f"{key}:", color='#94A3B8', fontsize=7.5, va='top')
            self.ax.text(0.46, y_pos, str(val), color=val_color, fontsize=7.5, fontweight='bold', va='top')
            y_pos -= line_height

        # Panel 1: Active Mission & Target Anomaly
        draw_header(">> ACTIVE MISSION", color='#38BDF8')
        if engine.robot and engine.robot.active_mission_id:
            m = engine.missions.get_mission(engine.robot.active_mission_id)
            if m:
                draw_kv("Mission", m.mission_id, '#38BDF8')
                draw_kv("Target Anomaly", m.anomaly_id, '#FCD34D')
                draw_kv("Target Location", f"{m.target_track_id} @ KM {m.target_chainage_km:.2f}")
                m_color = '#10B981' if m.status.value == 'COMPLETED' else ('#8B5CF6' if m.status.value == 'INSPECTING' else '#F59E0B')
                draw_kv("Status", m.status.value, m_color)
        else:
            draw_kv("Mission", "STANDBY (Click map to set destination)", '#64748B')
            y_pos -= line_height * 0.9

        y_pos -= line_height * 0.2

        # Panel 2: Autonomous Robot RG01 Telemetry
        draw_header(">> ROBOT (RG01)", color='#10B981')
        if engine.robot:
            rob = engine.robot
            rob_eta = 0.0
            rob_rem_dist = 0.0
            target_dest = "--"
            if engine.robot.active_mission_id:
                m = engine.missions.get_mission(engine.robot.active_mission_id)
                if m:
                    target_dest = f"{m.anomaly_id} ({m.target_track_id})"
                    
            if engine.current_route and rob.status in (RobotStatus.TRANSIT, RobotStatus.CHANGING_TRACK, RobotStatus.YIELDING_TO_TRAIN, RobotStatus.WAITING_FOR_CLEARANCE, RobotStatus.REPLANNING):
                rob_rem_dist = max(0.0, engine.current_route.total_distance_m - rob.distance_along_track_m)
                eff_v = max(1.5, rob.speed if rob.speed > 0 else rob.max_speed_mps)
                rob_eta = rob_rem_dist / eff_v

            status_str = rob.status.value
            if rob.status == RobotStatus.CHANGING_TRACK:
                status_col = '#06B6D4'  # Cyan
            elif rob.status == RobotStatus.TRANSIT:
                status_col = '#10B981'  # Green
            elif rob.status in (RobotStatus.YIELDING_TO_TRAIN, RobotStatus.WAITING_FOR_CLEARANCE):
                status_col = '#F59E0B'  # Yellow/Orange
            elif rob.status == RobotStatus.INSPECTING:
                status_col = '#8B5CF6'  # Purple
            else:
                status_col = '#94A3B8'
            
            draw_kv("Destination", target_dest, '#38BDF8')
            draw_kv("Current Track", f"{rob.current_track_id} @ {rob.distance_along_track_m:.1f}m")
            draw_kv("Speed", f"{rob.speed*3.6:.1f} km/h (Set: {rob.target_speed*3.6:.1f})")
            draw_kv("Status", status_str, status_col)
            
            action_col = '#06B6D4' if "MAKING WAY" in engine.current_action else ('#FCD34D' if "WAIT" in engine.current_action or "YIELD" in engine.current_action else '#10B981')
            draw_kv("Current Action", engine.current_action, action_col)
            draw_kv("Distance Rem", f"{rob_rem_dist:.0f} m" if rob_rem_dist > 0 else "--")
            draw_kv("ETA", f"{rob_eta:.0f} s" if rob_eta > 0 else "--", '#38BDF8')

        y_pos -= line_height * 0.2

        # Panel 3: Dynamic Route (Time-Aware A*)
        draw_header(">> ROUTE (TIME-AWARE A*)", color='#A78BFA')
        if engine.current_route and engine.robot and engine.robot.active_mission_id:
            route_status = "SAFE" if engine.current_route.is_safe else "CAUTION (Conflict on path)"
            r_col = '#10B981' if engine.current_route.is_safe else '#F59E0B'
            draw_kv("Route Mode", "Dynamic (Live A*)", '#A78BFA')
            draw_kv("Route Status", route_status, r_col)
            draw_kv("Total Distance", f"{engine.current_route.total_distance_m:.0f} m")
            draw_kv("Est. Travel Time", f"{engine.current_route.estimated_time_sec:.0f} s")
        else:
            draw_kv("Route", "IDLE (Awaiting Anomaly Destination)", '#64748B')
            y_pos -= line_height * 0.9

        y_pos -= line_height * 0.2

        # Panel 4: Railway Traffic (Exactly 4 Trains)
        draw_header(">> TRAFFIC (4 ACTIVE TRAINS)", color='#F59E0B')
        for train in engine.trains[:4]:
            t_tel = train.get_telemetry()
            next_str = f"-> {t_tel.next_track_id}" if t_tel.next_track_id else "End"
            if train.status == TrainStatus.WAITING_FOR_CLEARANCE:
                reason = t_tel.wait_reason if t_tel.wait_reason else "Clearance"
                if len(reason) > 22:
                    reason = reason[:20] + ".."
                draw_kv(f"{train.agent_id} ({train.current_track_id})", f"WAITING ({reason})", '#EF4444')
            elif train.status in (TrainStatus.DECELERATING, TrainStatus.YIELDING):
                draw_kv(f"{train.agent_id} ({train.current_track_id})", f"SLOWING ({train.speed*3.6:.0f} km/h) {next_str}", '#F59E0B')
            else:
                draw_kv(f"{train.agent_id} ({train.current_track_id})", f"{train.speed*3.6:.0f} km/h | {next_str} [SAFE]", '#38BDF8')

        y_pos -= line_height * 0.2

        # Panel 5: Safety Hierarchy & Train Priority
        draw_header(">> SAFETY & PRIORITY HIERARCHY", color='#EF4444')
        if engine.robot and engine.robot.status == RobotStatus.CHANGING_TRACK:
            draw_kv("Robot Safety", "CHANGING TRACK (Clearing Mainline)", '#06B6D4')
            draw_kv("Priority Action", engine.current_action, '#38BDF8')
        elif engine.robot and engine.robot.status == RobotStatus.YIELDING_TO_TRAIN:
            draw_kv("Robot Safety", "YIELDING TO TRAIN (Train Priority)", '#F59E0B')
            draw_kv("Blocking Train", engine.blocking_train_id or "TR01", '#EF4444')
        elif engine.robot and engine.robot.status == RobotStatus.WAITING_FOR_CLEARANCE:
            draw_kv("Robot Safety", "WAITING FOR CLEARANCE", '#F59E0B')
            draw_kv("Blocking Train", engine.blocking_train_id or "TR01", '#EF4444')
            clr_sec = engine.get_expected_clearance_sec()
            draw_kv("Est. Clearance", f"{clr_sec:.1f} s" if clr_sec > 0 else "Pending", '#FCD34D')
        else:
            draw_kv("Robot Safety", "NORMAL (Collision-Free)", '#10B981')
            draw_kv("Train Traffic", "CONTINUOUS PRIORITY", '#38BDF8')

        y_pos -= line_height * 0.2

        # Panel 6: Simulation Fast-Forward Controls & Clock
        draw_header(f">> SIMULATION ({self.sim_speed_multiplier:.0f}x Speed)", color='#38BDF8')
        sim_hrs = int(engine.sim_time_sec // 3600)
        sim_mins = int((engine.sim_time_sec % 3600) // 60)
        sim_secs = int(engine.sim_time_sec % 60)
        draw_kv("Simulation Time", f"{sim_hrs:02d}:{sim_mins:02d}:{sim_secs:02d}", '#38BDF8')
        draw_kv("Real Time", time.strftime("%H:%M:%S", time.localtime()), '#94A3B8')

        y_pos -= line_height * 0.2

        # Panel 7: Decision Engine Audit Ticker
        draw_header(">> PI DECISION AUDIT LOG", color='#E2E8F0')
        recent_logs = engine.logger.get_recent_logs(4)
        for log_entry in recent_logs:
            display_str = log_entry if len(log_entry) < 46 else log_entry[:43] + "..."
            color = '#94A3B8'
            if "Conflict predicted" in log_entry or "BLOCKED" in log_entry or "CRITICAL" in log_entry:
                color = '#FCA5A5'
            elif "Safe alternative found" in log_entry or "Changing track" in log_entry or "MAKING WAY" in log_entry:
                color = '#67E8F9'
            elif "COMPLETED" in log_entry or "SAFE" in log_entry or "REROUTED" in log_entry or "cleared" in log_entry or "Resuming" in log_entry:
                color = '#A7F3D0'
            self.ax.text(0.03, y_pos, display_str, color=color, fontsize=7.0, va='top', fontfamily='monospace')
            y_pos -= line_height * 0.8
