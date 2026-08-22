"""
Interactive Simulator - Matplotlib Animation Engine with Live Kinematics, Fast-Forwarding, and Map Clicks
"""
import time
import random
import matplotlib.pyplot as plt
import matplotlib.animation as animation
from matplotlib.gridspec import GridSpec
from config import DEFAULT_SIMULATION_SPEED, SIMULATION_SPEED_PRESETS, AnomalyType, TRAIN_DEFAULT_SPEED_MPS, MissionStatus
from entities.train import Train
from server.decision_engine import DecisionEngine
from visualization.map_renderer import MapRenderer
from visualization.hud_overlay import HUDOverlay

class InteractiveSimulator:
    """
    Real-time interactive Digital Twin simulation with fast-forwarded physics and click interaction.
    """
    def __init__(self, engine: DecisionEngine):
        self.engine = engine
        self.is_paused = False
        self.dt_base = 0.04  # 25 updates/sec base
        self.speed_idx = SIMULATION_SPEED_PRESETS.index(DEFAULT_SIMULATION_SPEED) if DEFAULT_SIMULATION_SPEED in SIMULATION_SPEED_PRESETS else 2
        self.sim_speed_multiplier = SIMULATION_SPEED_PRESETS[self.speed_idx]
        self.hud_render_counter = 0

        # Setup aesthetic figure
        plt.style.use('dark_background')
        self.fig = plt.figure(figsize=(16, 8.5), facecolor='#070A13')
        self.fig.canvas.manager.set_window_title("RAILGUARD AI - Autonomous Railway Incident Response Platform")

        gs = GridSpec(1, 2, width_ratios=[3.2, 1.2], figure=self.fig, wspace=0.12)
        self.ax_map = self.fig.add_subplot(gs[0])
        self.ax_hud = self.fig.add_subplot(gs[1])

        self.map_renderer = MapRenderer(self.ax_map, engine.graph)
        self.hud_overlay = HUDOverlay(self.ax_hud)
        self.hud_overlay.set_speed_multiplier(self.sim_speed_multiplier)

        # Connect user interaction handlers
        self.fig.canvas.mpl_connect('key_press_event', self._on_key_press)
        self.fig.canvas.mpl_connect('button_press_event', self._on_mouse_click)

    def _on_mouse_click(self, event):
        """Handles left-click on the railway map to place an anomaly."""
        if event.inaxes != self.ax_map:
            return
        if event.button == 1 and event.xdata is not None and event.ydata is not None:
            click_x = float(event.xdata)
            click_y = float(event.ydata)

            # Check if robot already has an active mission in progress
            if self.engine.robot and self.engine.robot.active_mission_id:
                active_m = self.engine.missions.get_mission(self.engine.robot.active_mission_id)
                if active_m and active_m.status not in (MissionStatus.COMPLETED, MissionStatus.FAILED, MissionStatus.ABORTED):
                    self.map_renderer.show_toast(f"MISSION {active_m.mission_id} ACTIVE - CANNOT DISPATCH NEW", duration_sec=2.5)
                    return

            # Snap to railway network
            anomaly = self.engine.report_anomaly_at_coordinates(click_x, click_y, max_dist_m=25.0)
            if anomaly:
                self.engine.logger.log("USER", f"Interactive map click at ({click_x:.1f}, {click_y:.1f}) snapped to Anomaly {anomaly.anomaly_id} on {anomaly.track_id}")
            else:
                self.map_renderer.show_toast("SELECT A LOCATION ON THE RAILWAY", duration_sec=2.5)

    def _on_key_press(self, event):
        if event.key == ' ':
            self.is_paused = not self.is_paused
            state = "PAUSED" if self.is_paused else "RESUMED"
            self.engine.logger.log("SIM", f"Simulation {state}")

        elif event.key == 'up':
            if self.speed_idx < len(SIMULATION_SPEED_PRESETS) - 1:
                self.speed_idx += 1
                self.sim_speed_multiplier = SIMULATION_SPEED_PRESETS[self.speed_idx]
                self.hud_overlay.set_speed_multiplier(self.sim_speed_multiplier)
                self.engine.logger.log("SIM", f"Simulation speed increased to {self.sim_speed_multiplier:.0f}×")

        elif event.key == 'down':
            if self.speed_idx > 0:
                self.speed_idx -= 1
                self.sim_speed_multiplier = SIMULATION_SPEED_PRESETS[self.speed_idx]
                self.hud_overlay.set_speed_multiplier(self.sim_speed_multiplier)
                self.engine.logger.log("SIM", f"Simulation speed decreased to {self.sim_speed_multiplier:.0f}×")

        elif event.key in ('a', 'A'):
            # Manually trigger anomaly at central track
            self.engine.report_anomaly_at_coordinates(440.0, 0.0)

        elif event.key in ('n', 'N'):
            # Force immediate route recalculation
            self.engine.force_recalculate_route()

        elif event.key in ('t', 'T'):
            # Spawn a new autonomous train
            new_id = f"TR{len(self.engine.trains)+1:02d}"
            track_choices = ["T01a", "T02a", "T01c", "T02c"]
            start_track = random.choice(track_choices)
            speed_choices = [10.0, 12.5, 14.0, 16.5] # 36 to 60 km/h
            sp = random.choice(speed_choices)
            t_new = Train(new_id, self.engine.graph, start_track, initial_distance_m=10.0, initial_speed_mps=sp)
            self.engine.register_train(t_new)

        elif event.key in ('c', 'C'):
            # Conflict injection test
            if self.engine.robot:
                self.engine.logger.log("TEST", "Conflict injection triggered: spawning opposing train near robot route")
                t_conf = Train(f"TR_TEST", self.engine.graph, "T02a", initial_distance_m=120.0, initial_speed_mps=15.0)
                t_conf.set_route(["T02a", "T02b", "T02c"])
                self.engine.register_train(t_conf)

        elif event.key in ('q', 'Q', 'escape'):
            plt.close(self.fig)

    def _update_frame(self, frame_idx):
        if not self.is_paused:
            # Advance simulation in fast-forwarded sub-steps
            effective_dt = self.dt_base * self.sim_speed_multiplier
            sub_steps = 4 if self.sim_speed_multiplier >= 10.0 else 2
            sub_dt = effective_dt / sub_steps

            for _ in range(sub_steps):
                # 1. Decision Server cycle
                self.engine.step(sub_dt)

                # 2. Advance simulated physical entities
                if self.engine.robot:
                    self.engine.robot.step(sub_dt)

                for train in self.engine.trains:
                    train.step(sub_dt, other_trains=self.engine.trains, sim_time=self.engine.sim_time_sec)

        # 3. Update Visual Artists in-place
        self.map_renderer.update(self.engine, dt=self.dt_base)

        # 4. Update HUD panel periodically (every 2 frames for maximum efficiency)
        self.hud_render_counter += 1
        if self.hud_render_counter % 2 == 0:
            self.hud_overlay.render(self.engine)

        return []

    def run(self):
        """Starts real-time simulation animation loop."""
        self.anim = animation.FuncAnimation(
            self.fig,
            self._update_frame,
            interval=35,
            blit=False,
            cache_frame_data=False
        )
        self.fig.subplots_adjust(left=0.05, right=0.98, top=0.93, bottom=0.08, wspace=0.12)
        plt.show()
