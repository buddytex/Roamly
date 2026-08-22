# RAILguard AI
### Autonomous Railway Inspection, Hazard Classification & Real-Time Incident Response Platform

[![Architecture](https://img.shields.io/badge/Architecture-Distributed_Multi--Agent-blue.svg)](docs/architecture/)
[![Simulation](https://img.shields.io/badge/Simulation-Digital_Twin_Verified-success.svg)](simulation/)
[![Perception](https://img.shields.io/badge/Perception-ONNX_HeatmapNet-orange.svg)](perception/)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)

---

## 1. Executive Summary

**RAILguard AI** is a distributed, agent-based autonomous railway inspection and safety ecosystem. The platform continuously monitors railway corridors, classifies infrastructural hazards (e.g., broken rails, track buckling, obstacles, fastener failures), dynamically plans collision-free transit routes, coordinates with moving rolling stock, and executes real-time incident-response mitigations (such as track blocking and train rerouting).

> [!IMPORTANT]
> **Physical Prototype vs. Future Production System**:
> - **Current Physical Prototype**: A 4-wheel omni-wheel mobile robot equipped with a smartphone (primary onboard compute/perception), ESP32 (low-level motor/servo hardware interface), and environmental sensors (IMU, GPS, LiDAR).
> - **Future Production Product**: A rail-mounted autonomous railway inspection and maintenance vehicle navigating directly on live track networks.
> - The software architecture, communication contracts, multi-agent coordination, and digital twin are designed from first principles for the **future rail-mounted product**, using the omni robot solely for early empirical validation.

---

## 2. End-to-End System Architecture

```
                                  [ ONBOARD ROBOT ]
                          Smartphone (Camera + LiDAR + IMU + GPS)
                                        │
                                        ▼
                             Onboard Perception Agent
                     (ONNX HeatmapNet + Defect Classifier)
                                        │
                                 Wi-Fi / HTTP
                                        │
                                        ▼
                           [ CENTRAL RAILWAY NODE ]
                                Raspberry Pi 4
                                        │
    ┌───────────────────────────────────┼───────────────────────────────────┐
    ▼                                   ▼                                   ▼
Central Coordinator Agent       Mission Planning Agent            Railway Safety Agent
(State Orchestration)           (Lifecycle & Priority)            (Spatio-Temporal TTC)
    │                                   │                                   │
    └───────────────────────────────────┼───────────────────────────────────┘
                                        ▼
                               Path Planning Agent
                           (Time-Aware Dynamic A*)
                                        │
                                        ▼
                                  Traffic Agent
                          (4-Train Kinematic Simulator)
                                        │
               ┌────────────────────────┴────────────────────────┐
               ▼                                                 ▼
      [ PHYSICAL ROBOT ]                                 [ LAPTOP CLIENT ]
    Smartphone ──► ESP32                                 Live Digital Twin
 (Kinematics ──► Motors)                              (Matplotlib / Telemetry)
```

---

## 3. Distributed Roles & Responsibilities

### A. Smartphone / Onboard Intelligence (`android/`)
- **Main Processing Computer**: Houses the high-level onboard agent.
- **Perception Pipeline**: Ingests camera frames at $512 \times 288$, executes ONNX HeatmapNet inference, decodes primary and secondary rail geometries, and detects anomalies.
- **Sensor Fusion**: Aggregates phone IMU, GPS, and LiDAR point clouds.
- **Hardware Bridge**: Relays target velocity commands to the ESP32 and receives motor/encoder status.

### B. ESP32 Microcontroller (`esp32/`)
- **Low-Level Hardware Controller**: Drives 4 omni-wheel motors and MG996R servo mechanisms.
- **Telemetry Provider**: Reads encoder feedback, current draw, and battery voltage.
- **Safety Interlock**: Implements hardware watchdogs that halt motors if heartbeat with phone is lost.

### C. Raspberry Pi Central Node (`pi/`)
- **Single Source of Truth**: Runs Ubuntu 22 and hosts the central multi-agent system.
- **Multi-Agent Roster**:
  1. **Central Coordinator Agent**: Orchestrates railway state, manages active missions, and handles track blocking.
  2. **Mission Planning Agent**: Triages reported anomalies and dispatches inspection orders.
  3. **Path Planning Agent**: Calculates Time-Aware A* routes factoring in track geometry and train reservation windows.
  4. **Railway Safety Agent**: Deterministically verifies time-to-collision (TTC) and minimum safe headway margins.
  5. **Traffic Agent**: Simulates 4 persistent trains across 3 corridors, managing dynamic switch diversion and overtaking.

### D. Laptop Visualization Client (`simulation/`)
- **Passive Monitoring Terminal**: Connects to the Pi via Wi-Fi/LAN.
- **Real-Time Digital Twin**: Renders tracks, switches, train positions, robot trajectory, and live agent decision audit logs.

---

## 4. Repository Structure

```text
RAILguardAI/
├── android/                   # Android Studio Kotlin Project (Camera, ONNX, LiDAR, IMU)
├── esp32/                     # ESP32 Arduino / C++ Firmware (Motor control, Telemetry)
├── pi/                        # Raspberry Pi Central Server & Multi-Agent Framework
│   ├── server/                # HTTP API Server & Coordinator Engine
│   ├── agents/                # Coordinator, Mission Planning, Path Planning, Safety, Traffic
│   ├── railway/               # Railway Graph Topology & World Model
│   └── communication/         # Inter-node Communication Handlers
├── simulation/                # Railway Digital Twin Simulation
│   ├── core/                  # Kinematics, Rolling Stock Entities, Robot Physics
│   ├── text_sim/              # Deterministic Headless CLI Snapshot Runner
│   └── visualization/         # Matplotlib Real-Time Interactive Twin & HUD
├── perception/                # AI & Computer Vision Models
│   ├── models/                # Production ONNX Heatmap Models
│   ├── scripts/               # Model Training, Evaluation, and Dataset Pipelines
│   └── notebooks/             # Google Colab Training Notebooks
├── shared/                    # Shared Data Contracts & Protocols
│   ├── schemas/               # Telemetry, Anomaly, and Mission Schemas
│   └── protocols/             # Phone <-> Pi and Phone <-> ESP32 Specifications
├── configs/                   # Global System Configuration & Network Limits
├── docs/                      # Technical Documentation & Hardware Guides
├── tests/                     # Automated Test Suites (8 Scenarios, Collision Prevention)
├── scripts/                   # Utility Scripts & Launchers
├── .gitignore                 # Universal Git Ignore Specification
└── README.md                  # Project Documentation
```

---

## 5. Two-Developer Git Workflow

To maintain a clean, conflict-free codebase, all development is conducted via dedicated feature branches and Pull Requests:

```text
main (Protected, stable, demo-ready)
 │
 ├── Developer 1 (Naveen)
 │    ├── naveen/perception       (Heatmap models, track/anomaly detection)
 │    ├── naveen/simulation       (Digital twin, traffic model, fast-forward)
 │    └── naveen/agents           (A* path planner, coordinator, safety agent)
 │
 └── Developer 2 (Teammate)
      ├── teammate/robot          (Chassis, physical assembly, motor wiring)
      ├── teammate/esp32          (Firmware, servo control, hardware telemetry)
      ├── teammate/pi             (Pi Ubuntu deployment, HTTP server setup)
      └── teammate/integration    (Phone <-> Pi <-> ESP32 integration)
```

---

## 6. Verification & Test Execution

Run the complete suite of deterministic tests from the project root:

```bash
# 1. Run all 8 multi-agent railway scenarios
python -m tests.test_text_scenarios

# 2. Run standalone headless text simulation
python -m simulation.text_sim.sim_text

# 3. Run collision prevention suite (Tests A to H)
python -m tests.test_collision_prevention

# 4. Run 4-train simplified traffic model tests
python -m tests.test_simplified_traffic_model
```
