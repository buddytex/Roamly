"""
Railway Network Builder - Constructs benchmark topological and geometric railway layouts
"""
from typing import Dict, List, Tuple
from config import TrackStatus, SwitchState
from world.graph import Node, SwitchNode, TrackSegment, RailwayGraph

def build_benchmark_railway_network() -> RailwayGraph:
    r"""
    Builds a benchmark railway network featuring:
      - Mainline Upper (T01): Parallel mainline bypassing central corridor
      - Mainline Central (T02): Primary traffic line (contains KM 184.7 anomaly location)
      - Lower Track / Yard Siding (T03): Robot deployment & auxiliary track
      - Bypass & Crossovers (S11, S12, S15, S16) allowing rich routing and conflict detection.

    Layout Schematic:
      N01 (W-End T01) ─── T01a ─── S15 ─── T01b ─── S16 ─── T01c ─── N02 (E-End T01)
                                   /                 \
                             T_X15/                   \T_X16
                                 /                     \
      N03 (W-End T02) ─── T02a ─┼─────── S12 ─────────┼─────── T02b ─── N04 (E-End T02)
                                │       /
                                │ T_X12/
                                │     /
      N05 (Robot Base)─── T03a ─┴────┴──── T03b ─────────────────────── N06 (E-End T03)
    """
    g = RailwayGraph()

    # 1. Create Nodes
    # Mainline T01 Nodes (y = 50.0m)
    g.add_node(Node("N01", x=0.0, y=50.0, name="West Mainline T01 Buffer", node_type="BUFFER"))
    g.add_node(SwitchNode("S15", x=220.0, y=50.0, name="Switch S15 (T03-T01 Crossover)"))
    g.add_node(SwitchNode("S16", x=500.0, y=50.0, name="Switch S16 (T01-T02 Diverge)"))
    g.add_node(Node("N02", x=750.0, y=50.0, name="East Mainline T01 Station", node_type="STATION"))

    # Central Track T02 Nodes (y = 0.0m)
    g.add_node(Node("N03", x=0.0, y=0.0, name="West Mainline T02 Station", node_type="STATION"))
    g.add_node(SwitchNode("S12", x=350.0, y=0.0, name="Switch S12 (T03-T02 Main Crossover)"))
    g.add_node(Node("N04", x=750.0, y=0.0, name="East Mainline T02 Buffer", node_type="BUFFER"))

    # Lower Siding / Inspection Base T03 Nodes (y = -40.0m)
    g.add_node(Node("N05", x=0.0, y=-40.0, name="Robot Deployment Siding", node_type="STATION"))
    g.add_node(SwitchNode("S11", x=120.0, y=-40.0, name="Switch S11 (Siding Turnout)"))
    g.add_node(Node("N06", x=750.0, y=-40.0, name="East Maintenance Yard", node_type="BUFFER"))

    # Auxiliary Junction Points for Smooth Curved Turnouts
    g.add_node(Node("J_T02_MID", x=440.0, y=0.0, name="T02 Mid-Block Inspection Point", node_type="JUNCTION"))

    # 2. Create Track Segments
    # T01 Upper Corridor (y = 50m)
    g.add_track(TrackSegment(
        track_id="T01a",
        start_node_id="N01",
        end_node_id="S15",
        geometry=[(0.0, 50.0), (220.0, 50.0)],
        speed_limit_mps=22.0,
        chainage_start_km=180.0
    ))
    g.add_track(TrackSegment(
        track_id="T01b",
        start_node_id="S15",
        end_node_id="S16",
        geometry=[(220.0, 50.0), (500.0, 50.0)],
        speed_limit_mps=22.0,
        chainage_start_km=180.22
    ))
    g.add_track(TrackSegment(
        track_id="T01c",
        start_node_id="S16",
        end_node_id="N02",
        geometry=[(500.0, 50.0), (750.0, 50.0)],
        speed_limit_mps=22.0,
        chainage_start_km=180.50
    ))

    # T02 Central Corridor (y = 0m) - Note: Anomaly will be located at chainage KM 184.7 (approx x = 440m)
    g.add_track(TrackSegment(
        track_id="T02a",
        start_node_id="N03",
        end_node_id="S12",
        geometry=[(0.0, 0.0), (350.0, 0.0)],
        speed_limit_mps=20.0,
        chainage_start_km=184.0
    ))
    g.add_track(TrackSegment(
        track_id="T02b",
        start_node_id="S12",
        end_node_id="J_T02_MID",
        geometry=[(350.0, 0.0), (440.0, 0.0)],
        speed_limit_mps=20.0,
        chainage_start_km=184.35
    ))
    g.add_track(TrackSegment(
        track_id="T02c",
        start_node_id="J_T02_MID",
        end_node_id="N04",
        geometry=[(440.0, 0.0), (750.0, 0.0)],
        speed_limit_mps=20.0,
        chainage_start_km=184.44
    ))

    # T03 Lower Inspection Corridor (y = -40m)
    g.add_track(TrackSegment(
        track_id="T03a",
        start_node_id="N05",
        end_node_id="S11",
        geometry=[(0.0, -40.0), (120.0, -40.0)],
        speed_limit_mps=8.0,
        chainage_start_km=188.0
    ))
    g.add_track(TrackSegment(
        track_id="T03b",
        start_node_id="S11",
        end_node_id="N06",
        geometry=[(120.0, -40.0), (750.0, -40.0)],
        speed_limit_mps=8.0,
        chainage_start_km=188.12
    ))

    # Crossover Tracks / Turnouts
    # 1. Primary Direct Route Turnout: S11 (T03) -> S12 (T02) via T_X12
    g.add_track(TrackSegment(
        track_id="T_X12",
        start_node_id="S11",
        end_node_id="S12",
        geometry=[(120.0, -40.0), (200.0, -35.0), (280.0, -10.0), (350.0, 0.0)],
        speed_limit_mps=10.0,
        chainage_start_km=188.12
    ))

    # 2. Alternative Bypass Turnout: S11 (T03) -> S15 (T01) via T_X15
    g.add_track(TrackSegment(
        track_id="T_X15",
        start_node_id="S11",
        end_node_id="S15",
        geometry=[(120.0, -40.0), (160.0, -10.0), (190.0, 25.0), (220.0, 50.0)],
        speed_limit_mps=10.0,
        chainage_start_km=188.12
    ))

    # 3. Upper-to-Central Reconnection Turnout: S16 (T01) -> J_T02_MID (T02) via T_X16
    g.add_track(TrackSegment(
        track_id="T_X16",
        start_node_id="S16",
        end_node_id="J_T02_MID",
        geometry=[(500.0, 50.0), (475.0, 30.0), (455.0, 10.0), (440.0, 0.0)],
        speed_limit_mps=12.0,
        chainage_start_km=180.50
    ))

    # Configure Switches with normal vs reverse branches
    # S11: Stem=T03a, Normal=T03b, Reverse=T_X12 (or T_X15 for bypass)
    s11 = g.nodes["S11"]
    if isinstance(s11, SwitchNode):
        s11.stem_track_id = "T03a"
        s11.normal_track_id = "T03b"
        s11.reverse_track_id = "T_X12"

    # S12: Stem=T02a, Normal=T02b, Reverse=T_X12
    s12 = g.nodes["S12"]
    if isinstance(s12, SwitchNode):
        s12.stem_track_id = "T02a"
        s12.normal_track_id = "T02b"
        s12.reverse_track_id = "T_X12"

    # S15: Stem=T01a, Normal=T01b, Reverse=T_X15
    s15 = g.nodes["S15"]
    if isinstance(s15, SwitchNode):
        s15.stem_track_id = "T01a"
        s15.normal_track_id = "T01b"
        s15.reverse_track_id = "T_X15"

    # S16: Stem=T01b, Normal=T01c, Reverse=T_X16
    s16 = g.nodes["S16"]
    if isinstance(s16, SwitchNode):
        s16.stem_track_id = "T01b"
        s16.normal_track_id = "T01c"
        s16.reverse_track_id = "T_X16"

    return g
