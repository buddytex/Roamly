"""
Sensor Simulation & Noise Injection (GPS, IMU, Rail Perception)
"""
import random
import math
from typing import Tuple

class SensorSimulator:
    """
    Simulates onboard Android sensor readings with realistic noise, drift, and dropouts.
    """
    def __init__(
        self,
        gps_noise_std_m: float = 0.5,
        imu_heading_drift_deg: float = 0.1,
        perception_dropout_rate: float = 0.0
    ):
        self.gps_noise_std_m = gps_noise_std_m
        self.imu_heading_drift_deg = imu_heading_drift_deg
        self.perception_dropout_rate = perception_dropout_rate
        self.accumulated_imu_drift = 0.0
        self.gps_dropout_active = False

    def sample_gps(self, true_x: float, true_y: float) -> Tuple[float, float, bool]:
        """
        Returns (noisy_x, noisy_y, valid_fix).
        """
        if self.gps_dropout_active or random.random() < 0.001:
            return (true_x, true_y, False)
        
        noise_x = random.gauss(0.0, self.gps_noise_std_m)
        noise_y = random.gauss(0.0, self.gps_noise_std_m)
        return (true_x + noise_x, true_y + noise_y, True)

    def sample_imu_heading(self, true_heading_rad: float, dt: float) -> float:
        """
        Simulates gyroscope integration drift over time.
        """
        drift_step = math.radians(self.imu_heading_drift_deg) * (random.random() - 0.5) * dt
        self.accumulated_imu_drift += drift_step
        return (true_heading_rad + self.accumulated_imu_drift) % (2 * math.pi)

    def sample_rail_perception(self) -> Tuple[bool, float, int]:
        """
        Simulates RailGuard AI ONNX output.
        Returns: (primary_detected, primary_confidence, secondary_count)
        """
        if random.random() < self.perception_dropout_rate:
            return (False, 0.0, 0)
        
        conf = min(0.99, max(0.70, random.gauss(0.95, 0.03)))
        return (True, conf, 1)
