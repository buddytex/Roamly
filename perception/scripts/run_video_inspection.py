import os
import sys
import time
import glob
import json
import argparse
import numpy as np
import cv2
import onnxruntime as ort

sys.path.insert(0, os.path.dirname(__file__))
try:
    from heatmap_decoder import extract_ego_track_from_heatmap, smooth_rail_points
except ImportError:
    pass

INPUT_W = 256
INPUT_H = 144
OUT_W = 1280
OUT_H = 720
NUM_POINTS = 16
DISPLAY_TRACK_LENGTH_PX = 400

_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
_STD  = np.array([0.229, 0.224, 0.225], dtype=np.float32)

def preprocess(frame_bgr: np.ndarray, inp_w: int = INPUT_W, inp_h: int = INPUT_H) -> np.ndarray:
    resized = cv2.resize(frame_bgr, (inp_w, inp_h), interpolation=cv2.INTER_LINEAR)
    rgb = cv2.cvtColor(resized, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    norm = (rgb - _MEAN) / _STD
    chw = norm.transpose(2, 0, 1)
    return chw[np.newaxis, :, :, :]

def snap_and_regularize(raw_pts, edge_map, search_w=12, degree=3):
    h, w = edge_map.shape
    snapped_xs = []
    ys = []
    
    for x, y in raw_pts:
        y_idx = min(max(int(y), 0), h - 1)
        x_idx = min(max(int(x), 0), w - 1)
        ys.append(y_idx)
        
        # Horizontal search window around the model's initialization
        x_start = max(x_idx - search_w, 0)
        x_end = min(x_idx + search_w + 1, w)
        
        profile = edge_map[y_idx, x_start:x_end]
        if len(profile) > 0:
            best_offset = np.argmax(profile)
            new_x = x_start + best_offset
            snapped_xs.append(new_x)
        else:
            snapped_xs.append(x_idx)
            
    # Fit polynomial to regularize and smooth out noise (RANSAC-like shape constraint)
    ys = np.array(ys, dtype=np.float32)
    snapped_xs = np.array(snapped_xs, dtype=np.float32)
    
    coeffs = np.polyfit(ys, snapped_xs, degree)
    regularized_xs = np.polyval(coeffs, ys)
    
    # Scale back to normalized coordinate range [0.0, 1.0]
    pts_normalized = np.stack([regularized_xs / w, ys / h], axis=-1)
    return pts_normalized

def clip_track_by_path_length(pts_normalized, max_len_px, w=1280, h=720):
    if pts_normalized is None or len(pts_normalized) < 2:
        return pts_normalized
    
    # Convert points to pixel coordinates
    pts_px = pts_normalized * np.array([w, h], dtype=np.float32)
    
    # Calculate cumulative distance
    diffs = np.diff(pts_px, axis=0)
    dists = np.sqrt(np.sum(diffs ** 2, axis=1))
    cum_dists = np.concatenate(([0.0], np.cumsum(dists)))
    
    # Keep points within the max length
    keep_indices = np.where(cum_dists <= max_len_px)[0]
    
    if len(keep_indices) == 0:
        return pts_normalized[:1]
        
    last_idx = keep_indices[-1]
    
    if last_idx < len(pts_normalized) - 1:
        p_prev = pts_normalized[last_idx]
        p_next = pts_normalized[last_idx + 1]
        
        d_prev = cum_dists[last_idx]
        d_next = cum_dists[last_idx + 1]
        segment_len = d_next - d_prev
        
        if segment_len > 1e-5:
            frac = (max_len_px - d_prev) / segment_len
            p_interp = p_prev + frac * (p_next - p_prev)
            clipped_pts = np.vstack([pts_normalized[keep_indices], p_interp])
        else:
            clipped_pts = pts_normalized[keep_indices]
    else:
        clipped_pts = pts_normalized[keep_indices]
        
    return clipped_pts

def draw_hud(frame, fps, latency_ms, primary_track, secondary_tracks, frame_idx, total_frames, model_name="MobileNetV3-v3", horizon_y=None, display_length_px=300):
    h, w = frame.shape[:2]
    
    # Semi-transparent top HUD bar
    hud_bg = frame.copy()
    cv2.rectangle(hud_bg, (0, 0), (w, 85), (20, 20, 24), -1)
    cv2.addWeighted(hud_bg, 0.75, frame, 0.25, 0, frame)
    
    # Header Title
    cv2.putText(frame, "RAILGUARD AI - REAL-TIME RAIL PERCEPTION", (20, 30),
                cv2.FONT_HERSHEY_DUPLEX, 0.75, (255, 255, 255), 2, cv2.LINE_AA)
    
    # Metrics
    tot_str = str(total_frames) if total_frames > 0 else "--"
    sec_count = len(secondary_tracks) if secondary_tracks else 0
    metrics_str = f"FPS: {fps:.1f} | Latency: {latency_ms:.1f}ms | Primary Track: 1 | Secondary Tracks: {sec_count} | Model: {model_name} | LENGTH: {display_length_px}px"
    cv2.putText(frame, metrics_str, (20, 65),
                cv2.FONT_HERSHEY_SIMPLEX, 0.50, (0, 230, 255), 1, cv2.LINE_AA)
    
    # Track Status Badge (Top Right)
    prim_l, prim_r = primary_track if primary_track else ([], [])
    is_locked = len(prim_l) >= 4 and len(prim_r) >= 4
    status_text = "EGO LOCKED" if is_locked else "SEARCHING"
    status_color = (40, 200, 80) if is_locked else (40, 140, 255)
    cv2.rectangle(frame, (w - 190, 15), (w - 20, 60), status_color, -1)
    cv2.putText(frame, status_text, (w - 175, 42),
                cv2.FONT_HERSHEY_DUPLEX, 0.55, (0, 0, 0), 2, cv2.LINE_AA)

    # Bottom Legend
    cv2.rectangle(frame, (20, h - 50), (450, h - 15), (20, 20, 24), -1)
    cv2.circle(frame, (35, h - 32), 6, (0, 235, 255), -1)
    cv2.putText(frame, "PRIMARY EGO", (50, h - 27), cv2.FONT_HERSHEY_SIMPLEX, 0.42, (255, 255, 255), 1, cv2.LINE_AA)
    cv2.circle(frame, (175, h - 32), 6, (255, 200, 50), -1)
    cv2.putText(frame, "SECONDARY TRACKS", (190, h - 27), cv2.FONT_HERSHEY_SIMPLEX, 0.42, (255, 255, 255), 1, cv2.LINE_AA)

def draw_rail_overlay(frame, primary_track, secondary_tracks=None, thickness=1, horizon_y=0.40, show_horizon=True, display_length_px=300):
    h, w = frame.shape[:2]

    overlay = frame.copy()
    has_overlay = False

    # ── 1. Draw Secondary (Adjacent / Siding) Tracks ─────────────────────────
    if secondary_tracks:
        for idx, (sec_l, sec_r) in enumerate(secondary_tracks):
            if len(sec_l) >= 2 and len(sec_r) >= 2:
                sec_l = clip_track_by_path_length(sec_l, display_length_px, w, h)
                sec_r = clip_track_by_path_length(sec_r, display_length_px, w, h)

                l_px = np.array([[int(p[0] * w), int(p[1] * h)] for p in sec_l], dtype=np.int32)
                r_px = np.array([[int(p[0] * w), int(p[1] * h)] for p in sec_r], dtype=np.int32)

                min_len = min(len(l_px), len(r_px))
                poly_pts = np.vstack([l_px[:min_len], r_px[:min_len][::-1]])
                cv2.fillPoly(overlay, [poly_pts], (220, 160, 40)) # Amber tint
                has_overlay = True

                cv2.polylines(frame, [l_px], isClosed=False, color=(255, 200, 50), thickness=max(1, thickness), lineType=cv2.LINE_AA)
                cv2.polylines(frame, [r_px], isClosed=False, color=(255, 200, 50), thickness=max(1, thickness), lineType=cv2.LINE_AA)

                mid_pt = l_px[0] # Near-field foreground anchor
                cv2.putText(frame, f"SECONDARY TRACK #{idx+1}", (max(10, mid_pt[0] - 25), max(30, mid_pt[1] - 8)),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.38, (255, 210, 80), 1, cv2.LINE_AA)

    # ── 2. Draw Primary (Ego) Track (Active Train Driving Corridor) ──────────
    prim_l, prim_r = primary_track if primary_track else ([], [])
    if len(prim_l) >= 2 and len(prim_r) >= 2:
        prim_l = clip_track_by_path_length(prim_l, display_length_px, w, h)
        prim_r = clip_track_by_path_length(prim_r, display_length_px, w, h)

        l_pixels = np.array([[int(pt[0] * w), int(pt[1] * h)] for pt in prim_l], dtype=np.int32)
        r_pixels = np.array([[int(pt[0] * w), int(pt[1] * h)] for pt in prim_r], dtype=np.int32)

        min_len = min(len(l_pixels), len(r_pixels))
        l_c = l_pixels[:min_len]
        r_c = r_pixels[:min_len]

        poly_pts = np.vstack([l_c, r_c[::-1]])
        cv2.fillPoly(overlay, [poly_pts], (40, 190, 80)) # Emerald green
        has_overlay = True

    if has_overlay:
        cv2.addWeighted(overlay, 0.16, frame, 0.84, 0, frame)

    if len(prim_l) >= 2 and len(prim_r) >= 2:

        # Centerline (Geometric Midpoint of paired left and right rails)
        c_pixels = ((l_c.astype(np.float32) + r_c.astype(np.float32)) / 2.0).astype(np.int32)
        for i in range(len(c_pixels) - 1):
            if i % 2 == 0:
                cv2.line(frame, tuple(c_pixels[i]), tuple(c_pixels[i+1]), (120, 240, 90), 1, cv2.LINE_AA)

        # Left Rail — Ultra-crisp Neon Yellow (Thin)
        cv2.polylines(frame, [l_c], isClosed=False, color=(0, 235, 255), thickness=thickness, lineType=cv2.LINE_AA)
        # Right Rail — Ultra-crisp Neon Magenta (Thin)
        cv2.polylines(frame, [r_c], isClosed=False, color=(180, 60, 255), thickness=thickness, lineType=cv2.LINE_AA)

        # Primary Track Label — Anchored in Near-field Foreground (pt 0)
        tag_pt = l_c[0]
        cv2.putText(frame, "PRIMARY EGO TRACK", (max(10, tag_pt[0] - 20), max(30, tag_pt[1] - 12)),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.50, (0, 255, 255), 1, cv2.LINE_AA)
class SecondaryTracker:
    """
    Temporal secondary track tracker.
    - Requires >= 2 consecutive confirmed frames before a track becomes ACTIVE.
    - Associates candidate tracks by curve geometry distance.
    - Holds tracks in LOST state for up to 3 missed frames before expiring.
    - Applies smooth EMA to active secondary tracks.
    """
    def __init__(self, max_tracks=2, confirm_frames=2, max_missed=3, match_thresh=0.060):
        self.max_tracks = max_tracks
        self.confirm_frames = confirm_frames
        self.max_missed = max_missed
        self.match_thresh = match_thresh
        self.tracks = {}
        self.next_id = 1

    def update(self, detected_secondaries):
        # 1. Age existing tracks
        for tid in list(self.tracks.keys()):
            self.tracks[tid]["missed"] += 1
            if self.tracks[tid]["missed"] > self.max_missed:
                del self.tracks[tid]
            else:
                self.tracks[tid]["state"] = "LOST"

        # 2. Match detected candidates with existing tracks by curve distance
        matched_dets = set()
        for tid, trk in self.tracks.items():
            best_det_idx = -1
            best_dist = 999.0
            for idx, (dl, dr) in enumerate(detected_secondaries):
                if idx in matched_dets or len(dl) < 4:
                    continue
                dist = self._calc_curve_dist(trk["left"], trk["right"], dl, dr)
                if dist < best_dist and dist < self.match_thresh:
                    best_dist = dist
                    best_det_idx = idx

            if best_det_idx >= 0:
                dl, dr = detected_secondaries[best_det_idx]
                matched_dets.add(best_det_idx)
                trk["hits"] += 1
                trk["missed"] = 0
                if trk["hits"] >= self.confirm_frames:
                    trk["state"] = "ACTIVE"
                # EMA smooth
                trk["left"] = self._smooth_rail(trk["left"], dl, alpha=0.35)
                trk["right"] = self._smooth_rail(trk["right"], dr, alpha=0.35)
                trk["near_cx"] = float((trk["left"][0, 0] + trk["right"][0, 0]) / 2.0)

        # 3. Create new tracks for unmatched detections
        for idx, (dl, dr) in enumerate(detected_secondaries):
            if idx not in matched_dets and len(dl) >= 6:
                if len(self.tracks) < self.max_tracks:
                    tid = self.next_id
                    self.next_id += 1
                    self.tracks[tid] = {
                        "id": tid,
                        "left": dl.copy(),
                        "right": dr.copy(),
                        "hits": 1,
                        "missed": 0,
                        "state": "ACTIVE" if self.confirm_frames <= 1 else "UNCONFIRMED",
                        "near_cx": float((dl[0, 0] + dr[0, 0]) / 2.0)
                    }

        # 4. Return active confirmed secondary tracks
        active_secondaries = []
        for tid, trk in sorted(self.tracks.items()):
            if trk["state"] in ("ACTIVE", "LOST") and trk["hits"] >= self.confirm_frames:
                active_secondaries.append((trk["left"], trk["right"]))

        return active_secondaries

    def _calc_curve_dist(self, al, ar, bl, br):
        y_start = min(float(al[:, 1].max()), float(bl[:, 1].max()))
        y_end   = max(float(al[:, 1].min()), float(bl[:, 1].min()))
        if (y_start - y_end) < 0.030:
            return abs(float((al[0, 0] + ar[0, 0]) / 2.0) - float((bl[0, 0] + br[0, 0]) / 2.0))
        step = 1.0 / 288.0
        grid_y = np.arange(y_start, y_end - 1e-5, -step, dtype=np.float32)
        if len(grid_y) < 4:
            return 999.0
        idx_al = np.argsort(al[:, 1])
        idx_ar = np.argsort(ar[:, 1])
        idx_bl = np.argsort(bl[:, 1])
        idx_br = np.argsort(br[:, 1])
        alx = np.interp(grid_y, al[idx_al, 1], al[idx_al, 0])
        arx = np.interp(grid_y, ar[idx_ar, 1], ar[idx_ar, 0])
        blx = np.interp(grid_y, bl[idx_bl, 1], bl[idx_bl, 0])
        brx = np.interp(grid_y, br[idx_br, 1], br[idx_br, 0])
        return float(np.mean((np.abs(alx - blx) + np.abs(arx - brx)) / 2.0))

    def _smooth_rail(self, prev_r, new_r, alpha=0.35):
        if len(prev_r) == len(new_r) and np.allclose(prev_r[:, 1], new_r[:, 1]):
            smoothed = prev_r.copy()
            smoothed[:, 0] = prev_r[:, 0] * (1.0 - alpha) + new_r[:, 0] * alpha
            return smoothed
        return new_r.copy()


def main():
    parser = argparse.ArgumentParser(description="RailGuard AI - Real-Time Video Rail Perception")
    parser.add_argument("--video", type=str, default="", help="Path to input video file or camera index (e.g. 0)")
    parser.add_argument("--folder", type=str, default="", help="Path to folder of images to process sequentially as a video")
    parser.add_argument("--model", type=str,
        default=r"C:\RailGuardAI\production_model\rail_model.onnx",
        help="Path to production ONNX model (falls back to rail_heatmap_512x288 / model_v5_heatmap if missing)")
    parser.add_argument("--output", type=str, default=r"C:\RailGuardAI\rail_samples\inspected_output.mp4", help="Path to save annotated video")
    parser.add_argument("--no-display", action="store_true", help="Run in headless mode without showing UI window")
    parser.add_argument("--show-heatmap", action="store_true", help="Render heatmap overlay on HUD")
    parser.add_argument("--line-thickness", type=int, default=1, help="Rail overlay line thickness in pixels (default: 1)")
    parser.add_argument("--x-offset", type=float, default=0.0, help="Horizontal coordinate offset for perspective calibration")
    parser.add_argument("--x-scale", type=float, default=1.0, help="Horizontal coordinate width scale factor for perspective calibration")
    parser.add_argument("--alpha", type=float, default=0.25, help="Temporal smoothing weight for legacy V1-V4 models (V5 heatmap path is unsmoothed)")
    parser.add_argument("--horizon", type=float, default=0.52, help="Y-coordinate of the horizon vanishing point")
    parser.add_argument("--no-snap", action="store_true", help="Disable classic CV edge snapping and polynomial regularization")
    parser.add_argument("--snap-w", type=int, default=12, help="Horizontal edge search window size in pixels")
    parser.add_argument("--snap-deg", type=int, default=3, help="Polynomial degree for track regularization")
    parser.add_argument("--max-frames", type=int, default=0, help="Maximum number of frames to process (0 = all)")
    parser.add_argument("--display-length", type=int, default=400, help="Visible track length in pixels along the path (default: 400)")
    args = parser.parse_args()

    global DISPLAY_TRACK_LENGTH_PX
    DISPLAY_TRACK_LENGTH_PX = args.display_length

    print("=" * 60)
    print("RAILGUARDAI - PRODUCTION REAL-TIME INSPECTION RUNNER")
    print("=" * 60)
    print(f"Model : {args.model}")

    # Auto-fallback: if production model missing, try rail_heatmap_512x288, then model_v5_heatmap, then v3
    if not os.path.exists(args.model):
        for candidate in [
            r"C:\RailGuardAI\rail_heatmap_512x288.onnx",
            r"C:\RailGuardAI\model_v5_heatmap\rail_model.onnx",
            r"C:\RailGuardAI\model_v3\rail_model.onnx"
        ]:
            if os.path.exists(candidate):
                print(f"[INFO] Using available model: {candidate}")
                args.model = candidate
                break
        else:
            print(f"ERROR: Model file not found: {args.model}")
            return

    # Load ONNX Session
    session = ort.InferenceSession(args.model)
    input_name = session.get_inputs()[0].name
    inp_shape = session.get_inputs()[0].shape
    model_inp_h = inp_shape[2] if isinstance(inp_shape[2], int) else 288
    model_inp_w = inp_shape[3] if isinstance(inp_shape[3], int) else 512
    print(f"Model Input Resolution: {model_inp_w}x{model_inp_h}")

    # Auto-detect fixed Y grid (model_v4 uses fixed Y rows, not predicted Y)
    fixed_y_grid = None
    y_grid_path = os.path.join(os.path.dirname(args.model), "fixed_y_grid.npy")
    if os.path.exists(y_grid_path):
        fixed_y_grid = np.load(y_grid_path)
        print(f"[INFO] Loaded fixed Y grid from {y_grid_path} ({len(fixed_y_grid)} rows)")
    else:
        print("[INFO] No fixed_y_grid.npy found — using predicted Y coords from model output.")

    # Auto-load camera calibration if present
    calib_path = os.path.join(os.path.dirname(os.path.dirname(args.model)),
                               "camera_calibration.json")
    calib = None
    if os.path.exists(calib_path):
        with open(calib_path) as f:
            calib = json.load(f)
        print(f"[CAL] Loaded camera calibration: {calib_path}")
    else:
        print(f"[CAL] No calibration file found at {calib_path}")
        print(f"      Run scripts/calibrate_camera.py to create one.")

    is_folder = bool(args.folder and os.path.isdir(args.folder))
    image_files = []
    cap = None

    if is_folder:
        image_files = sorted(glob.glob(os.path.join(args.folder, "*.png")) + glob.glob(os.path.join(args.folder, "*.jpg")))
        print(f"Loaded {len(image_files)} image frames from folder: {args.folder}")
        if not image_files:
            print("ERROR: No images found in folder.")
            return
        total_frames = len(image_files)
        fps_in = 25.0
    else:
        video_src = args.video if args.video else "0"
        src = int(video_src) if video_src.isdigit() else video_src
        cap = cv2.VideoCapture(src)
        if not cap.isOpened():
            print(f"ERROR: Could not open video source: {video_src}")
            return
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT)) if not str(src).isdigit() else 0
        fps_in = cap.get(cv2.CAP_PROP_FPS) or 30.0

    print(f"Target Output: {OUT_W}x{OUT_H} @ {fps_in:.1f} FPS (Total frames: {total_frames})")
    print(f"Save Path    : {args.output}")

    # Output Video Writer dimensions depend on show-heatmap flag (1920x720 in diagnostic mode, otherwise 1280x720)
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    out_w = 1920 if args.show_heatmap else OUT_W
    out_h = 720
    out_writer = cv2.VideoWriter(args.output, fourcc, fps_in, (out_w, out_h))

    frame_idx = 0
    fps_display = 0.0
    fps_counter = 0
    fps_timer = time.time()

    # Temporal smoothing state is retained only for legacy V1-V4 models.
    # Model V5 heatmap output is intentionally rendered directly from
    # heatmap_decoder without temporal primary association/hysteresis.
    smooth_l = None
    smooth_r = None
    alpha = args.alpha
    secondary_tracker = SecondaryTracker(max_tracks=2, confirm_frames=2, max_missed=3)

    print("\nStarting real-time playback...")
    print("Keyboard shortcuts: [SPACE] = Pause/Resume | [S] = Snapshot | [ESC/Q] = Exit\n")

    paused = False

    while True:
        if args.max_frames > 0 and frame_idx >= args.max_frames:
            print(f"\nReached max-frames limit of {args.max_frames}.")
            break
        if not paused:
            if is_folder:
                if frame_idx >= len(image_files):
                    print("\nReached end of folder frames.")
                    break
                raw_frame = cv2.imread(image_files[frame_idx])
            else:
                ret, raw_frame = cap.read()
                if not ret:
                    print("\nReached end of video.")
                    break

            if raw_frame is None:
                continue

            frame = cv2.resize(raw_frame, (OUT_W, OUT_H), interpolation=cv2.INTER_LINEAR)
            frame_clean = frame.copy()
            frame_idx += 1
            t0 = time.time()

            # Preprocess & Run ONNX Model
            # Per-frame state: never reuse a V5 primary from a previous frame.
            primary_track = ([], [])
            secondary_tracks = []
            is_heatmap = ("heatmap" in args.model.lower() or "v5" in args.model.lower())
            if frame_idx < fps_in:
                primary_track = ([], [])
                secondary_tracks = []
                latency_ms = 0.0
            else:
                inp = preprocess(frame, model_inp_w, model_inp_h)
                raw_out = session.run(None, {input_name: inp})[0][0]
                latency_ms = (time.time() - t0) * 1000

                # Check if model outputs spatial heatmaps (shape: (2, H, W))
                is_heatmap = (raw_out.ndim == 3 and raw_out.shape[0] == 2)

                if is_heatmap:
                    # ── SPATIAL HEATMAP MULTI-TRACK DECODING (Model V5) ──────────
                    import heatmap_decoder

                    # MODEL V5 — DIRECT HEATMAP DECODER OUTPUT
                    # ============================================================
                    #
                    # The heatmap decoder is the ONLY component that decides
                    # which candidate is PRIMARY for the current frame.
                    #
                    # Removed from the V5 path:
                    #   - track_list rebuilding
                    #   - center-distance scoring
                    #   - per-frame primary re-selection
                    #   - temporal primary association
                    #   - switch hysteresis
                    #   - 5-frame primary dropout persistence
                    #   - last_valid_primary restoration
                    #   - EMA temporal smoothing
                    #
                    # This is intentional. A secondary/siding track must not
                    # replace the decoder's primary simply because it happens
                    # to score closer to the image center in one frame.
                    #
                    # The decoder's primary should represent the ego/moving
                    # track. The renderer receives that result directly.
                    # ============================================================

                    decoded_primary, decoded_secondaries = (
                        heatmap_decoder.extract_multi_tracks_from_heatmap(
                            raw_out,
                            horizon_y=0.35,
                            min_conf=0.020
                        )
                    )

                    # Trust the decoder's current-frame decision directly
                    primary_track = decoded_primary
                    secondary_tracks = secondary_tracker.update(decoded_secondaries)

                    # Validate the CURRENT frame
                    if not (
                        isinstance(primary_track, (tuple, list))
                        and len(primary_track) == 2
                        and len(primary_track[0]) >= 4
                        and len(primary_track[1]) >= 4
                    ):
                        primary_track = (
                            np.empty((0, 2), dtype=np.float32),
                            np.empty((0, 2), dtype=np.float32)
                        )
                        secondary_tracks = []
                        smooth_l = None
                        smooth_r = None
                    else:
                        # EMA Temporal Smoothing on Validated Primary Ego-Track
                        prim_l, prim_r = primary_track
                        if args.alpha > 0 and len(prim_l) >= 4 and len(prim_r) >= 4:
                            # Re-sample smoothly on common Y grid
                            num_smooth_pts = min(len(prim_l), 32)
                            y_min = float(prim_l[:, 1].min())
                            y_max = float(prim_l[:, 1].max())
                            smooth_y_grid = np.linspace(y_max, y_min, num_smooth_pts, dtype=np.float32)

                            idx_l = np.argsort(prim_l[:, 1])
                            idx_r = np.argsort(prim_r[:, 1])
                            prim_l_sorted = prim_l[idx_l]
                            prim_r_sorted = prim_r[idx_r]

                            curr_l_x = np.interp(smooth_y_grid, prim_l_sorted[:, 1], prim_l_sorted[:, 0]).astype(np.float32)
                            curr_r_x = np.interp(smooth_y_grid, prim_r_sorted[:, 1], prim_r_sorted[:, 0]).astype(np.float32)

                            cur_l = np.stack([curr_l_x, smooth_y_grid], axis=-1)
                            cur_r = np.stack([curr_r_x, smooth_y_grid], axis=-1)

                            if smooth_l is None or len(smooth_l) != num_smooth_pts or abs(float(smooth_l[0, 0]) - float(cur_l[0, 0])) > 0.12:
                                smooth_l = cur_l.copy()
                                smooth_r = cur_r.copy()
                            else:
                                smooth_l = smooth_l + args.alpha * (cur_l - smooth_l)
                                smooth_r = smooth_r + args.alpha * (cur_r - smooth_r)

                            primary_track = (smooth_l, smooth_r)

                else:
                    # ── LEGACY 16-POINT REGRESSION (Models V1–V4) ────────────────
                    l_x_full = np.clip(raw_out[0:16], 0.0, 1.0)
                    r_x_full = np.clip(raw_out[16:32], 0.0, 1.0)

                    if fixed_y_grid is not None:
                        l_y_full = fixed_y_grid
                        r_y_full = fixed_y_grid
                        l_valid = raw_out[64:80] > 0.5
                        r_valid = raw_out[80:96] > 0.5
                        if l_valid.sum() < 2: l_valid[:] = True
                        if r_valid.sum() < 2: r_valid[:] = True
                    else:
                        l_y_full = np.clip(raw_out[32:48], 0.0, 1.0)
                        r_y_full = np.clip(raw_out[48:64], 0.0, 1.0)
                        l_valid = r_valid = np.ones(16, dtype=bool)

                    if args.x_offset != 0.0 or args.x_scale != 1.0:
                        y_factor = np.clip((l_y_full - args.horizon) / (1.0 - args.horizon), 0.0, 1.0)
                        cal_offset = args.x_offset * y_factor
                        mid = (l_x_full + r_x_full) / 2.0
                        half_w = (r_x_full - l_x_full) / 2.0
                        cal_mid = mid + cal_offset
                        cal_half_w = half_w * args.x_scale
                        l_x_full = np.clip(cal_mid - cal_half_w, 0.0, 1.0)
                        r_x_full = np.clip(cal_mid + cal_half_w, 0.0, 1.0)

                    cur_l = np.stack([l_x_full, l_y_full], axis=-1)
                    cur_r = np.stack([r_x_full, r_y_full], axis=-1)

                    if smooth_l is None:
                        smooth_l = cur_l.copy()
                        smooth_r = cur_r.copy()
                    else:
                        smooth_l = smooth_l + alpha * (cur_l - smooth_l)
                        smooth_r = smooth_r + alpha * (cur_r - smooth_r)

                    render_l = smooth_l[l_valid]
                    render_r = smooth_r[r_valid]

                    if not args.no_snap:
                        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
                        gray_blurred = cv2.GaussianBlur(gray, (5, 5), 0)
                        sobelx = cv2.Sobel(gray_blurred, cv2.CV_32F, 1, 0, ksize=3)
                        edge_map = np.abs(sobelx)

                        smooth_l_pixels = np.array([[pt[0] * OUT_W, pt[1] * OUT_H] for pt in render_l])
                        smooth_r_pixels = np.array([[pt[0] * OUT_W, pt[1] * OUT_H] for pt in render_r])

                        smooth_l_render = snap_and_regularize(smooth_l_pixels, edge_map, args.snap_w, args.snap_deg)
                        smooth_r_render = snap_and_regularize(smooth_r_pixels, edge_map, args.snap_w, args.snap_deg)
                    else:
                        smooth_l_render = render_l
                        smooth_r_render = render_r
                    primary_track = (smooth_l_render, smooth_r_render)
                    secondary_tracks = []

            # Calculate FPS
            fps_counter += 1
            if time.time() - fps_timer >= 0.5:
                fps_display = fps_counter / (time.time() - fps_timer)
                fps_counter = 0
                fps_timer = time.time()

            # Determine model description string for HUD
            if is_heatmap or "heatmap" in args.model.lower():
                model_desc = "RailHeatmapNet-v5 (Multi-Track FPN)"
            elif "v4" in args.model.lower():
                model_desc = "MobileNetV3-v4"
            elif "v3" in args.model.lower():
                model_desc = "MobileNetV3-v3"
            else:
                model_desc = "MobileNetV3-v2"
            if not args.no_snap and not is_heatmap:
                model_desc += " + Snapping"

            # Render Overlays
            draw_rail_overlay(frame, primary_track, secondary_tracks, thickness=args.line_thickness, horizon_y=args.horizon, display_length_px=DISPLAY_TRACK_LENGTH_PX)
            draw_hud(frame, fps_display, latency_ms, primary_track, secondary_tracks, frame_idx, total_frames, model_desc, horizon_y=args.horizon, display_length_px=DISPLAY_TRACK_LENGTH_PX)

            frame_to_write = frame
            if args.show_heatmap:
                # Always construct a 1920x720 composite layout when show_heatmap is true
                panel_a = cv2.resize(frame_clean, (640, 360))

                # Default empty placeholders for panels
                panel_b = np.zeros((360, 640, 3), dtype=np.uint8)
                cv2.putText(panel_b, "RAW LEFT HEATMAP (INACTIVE)", (15, 25), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 0, 255), 1, cv2.LINE_AA)

                panel_c = np.zeros((360, 640, 3), dtype=np.uint8)
                cv2.putText(panel_c, "RAW RIGHT HEATMAP (INACTIVE)", (15, 25), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 0, 255), 1, cv2.LINE_AA)

                panel_d = np.zeros((360, 640, 3), dtype=np.uint8)
                cv2.putText(panel_d, "DECODED RIDGE POINTS (INACTIVE)", (15, 25), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 0, 255), 1, cv2.LINE_AA)

                panel_e = cv2.resize(frame, (640, 360))
                cv2.putText(panel_e, "FINAL OUTPUT PREVIEW", (15, 25), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 255, 255), 1, cv2.LINE_AA)

                panel_f = np.zeros((360, 640, 3), dtype=np.uint8)
                cv2.putText(panel_f, "DIAGNOSTICS (INACTIVE)", (20, 35), cv2.FONT_HERSHEY_DUPLEX, 0.55, (255, 255, 255), 1, cv2.LINE_AA)

                if frame_idx >= fps_in and is_heatmap and 'raw_out' in locals():
                    # Fill Panel B: Left Heatmap (Channel 0)
                    hm_l_norm = np.clip(raw_out[0] * 255.0, 0, 255).astype(np.uint8)
                    panel_b_color = cv2.applyColorMap(hm_l_norm, cv2.COLORMAP_JET)
                    panel_b = cv2.resize(panel_b_color, (640, 360))
                    cv2.putText(panel_b, "RAW LEFT RAIL HEATMAP (CH 0)", (15, 25), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 255, 255), 1, cv2.LINE_AA)

                    # Fill Panel C: Right Heatmap (Channel 1)
                    hm_r_norm = np.clip(raw_out[1] * 255.0, 0, 255).astype(np.uint8)
                    panel_c_color = cv2.applyColorMap(hm_r_norm, cv2.COLORMAP_JET)
                    panel_c = cv2.resize(panel_c_color, (640, 360))
                    cv2.putText(panel_c, "RAW RIGHT RAIL HEATMAP (CH 1)", (15, 25), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 255, 255), 1, cv2.LINE_AA)

                    # Fill Panel D: Decoded raw rails
                    panel_d_frame = frame_clean.copy()
                    try:
                        import heatmap_decoder
                        info = heatmap_decoder.LAST_DEBUG_INFO
                        if info:
                            raw_pts_l = info.get("primary_raw_points_l", [])
                            raw_pts_r = info.get("primary_raw_points_r", [])
                            h_d, w_d = panel_d_frame.shape[:2]
                            for pt in raw_pts_l:
                                cv2.circle(panel_d_frame, (int(pt[0] * w_d), int(pt[1] * h_d)), 3, (0, 255, 255), -1)
                            for pt in raw_pts_r:
                                cv2.circle(panel_d_frame, (int(pt[0] * w_d), int(pt[1] * h_d)), 3, (255, 0, 255), -1)
                    except Exception as e:
                        pass
                    panel_d = cv2.resize(panel_d_frame, (640, 360))
                    cv2.putText(panel_d, "DECODED RAW RIDGE POINTS (UNPAIRED)", (15, 25), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 255, 255), 1, cv2.LINE_AA)

                    # Fill Panel F: Diagnostics summary text
                    panel_f = np.zeros((360, 640, 3), dtype=np.uint8)
                    cv2.rectangle(panel_f, (10, 10), (630, 350), (20, 20, 24), -1)
                    cv2.putText(panel_f, "DIAGNOSTICS & PERFORMANCE SUMMARY", (20, 35), cv2.FONT_HERSHEY_DUPLEX, 0.55, (255, 255, 255), 1, cv2.LINE_AA)
                    try:
                        import heatmap_decoder
                        info = heatmap_decoder.LAST_DEBUG_INFO
                        lines = [
                            f"Inference Latency : {latency_ms:.1f} ms",
                            f"Throughput        : {fps_display:.1f} FPS",
                            f"Raw Candidate Seeds: {info.get('raw_candidates_count', 0)}",
                            f"Active Tracks     : Primary: {info.get('final_primary_count', 0)}, Secondary: {info.get('final_secondary_count', 0)}",
                            f"Rejected Far-Field: {info.get('rejected_far_field_candidates_count', 0)}",
                            f"Tracer Points     : Left: {info.get('primary_raw_pts_l', 0)}, Right: {info.get('primary_raw_pts_r', 0)}",
                            f"Valid Paired Pts  : Left: {info.get('primary_valid_pts_l', 0)}, Right: {info.get('primary_valid_pts_r', 0)}",
                            f"Visual Path Limit : {DISPLAY_TRACK_LENGTH_PX} px",
                        ]
                        for idx, line in enumerate(lines):
                            cv2.putText(panel_f, line, (20, 75 + idx * 30), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 230, 255), 1, cv2.LINE_AA)
                    except Exception as e:
                        cv2.putText(panel_f, f"Error: {e}", (20, 75), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 0, 255), 1, cv2.LINE_AA)

                # Assemble Panels A-F into composite 2x3 layout
                top_row = np.hstack([panel_a, panel_b, panel_c])
                bottom_row = np.hstack([panel_d, panel_e, panel_f])
                composite_frame = np.vstack([top_row, bottom_row])
                frame_to_write = composite_frame

            # Save to output file
            out_writer.write(frame_to_write)

            if frame_idx % 50 == 0 or frame_idx == 1:
                prim_l, prim_r = primary_track
                lx_str = f"{prim_l[0,0]:.3f}" if len(prim_l) > 0 else "None"
                rx_str = f"{prim_r[0,0]:.3f}" if len(prim_r) > 0 else "None"
                print(f"Frame [{frame_idx:4d}/{total_frames}] -> Latency: {latency_ms:4.1f}ms | Processing: {fps_display:5.1f} FPS | Primary Pts: {len(prim_l)} | Sec Tracks: {len(secondary_tracks)} | L_x0: {lx_str} R_x0: {rx_str}")
                if frame_idx < fps_in:
                    print("  [INFO] Intro screen active - skipping validation.")
                
                # Detailed diagnostic logging for spatial heatmap model (FPN)
                if is_heatmap and frame_idx >= fps_in:
                    try:
                        import heatmap_decoder
                        info = heatmap_decoder.LAST_DEBUG_INFO
                        if info:
                            print(f"  [DIAGNOSTIC] Perception Horizon Y: {info.get('horizon_y', 0.35):.2f}")
                            print(f"  [DIAGNOSTIC] Candidates Summary:")
                            print(f"    Raw seeds: {info.get('raw_candidates_count', 0)}")
                            print(f"    Total Active Tracks: {info.get('clipped_candidates_count', 0)}")
                            print(f"    Primary Ego Candidates: {info.get('primary_candidates_count', 0)} (Final: {info.get('final_primary_count', 0)})")
                            print(f"    Secondary Candidates: {info.get('secondary_candidates_count', 0)} (Final: {info.get('final_secondary_count', 0)})")
                            print(f"    Rejected Candidates: {info.get('rejected_candidates_count', 0)}")
                            
                            cands = info.get('candidate_diagnostics', [])
                            for c in cands:
                                print(f"    Candidate #{c['candidate_id']+1}: Valid={c['is_valid']}, Pts={c['points']}, Y={c['y_min']:.2f}-{c['y_max']:.2f}, near_cx={c['near_cx']:.3f}, Score={c['primary_score']:.2f}, Reason={c['rejection_reason']}")

                            print(f"  [DIAGNOSTIC] Primary track:")
                            print(f"    Raw point count: Left: {info['primary_raw_pts_l']}, Right: {info['primary_raw_pts_r']}")
                            print(f"    Valid point count: Left: {info['primary_valid_pts_l']}, Right: {info['primary_valid_pts_r']}")
                            print(f"    Y Range: {info['primary_min_y']:.2f} - {info['primary_max_y']:.2f}")
                            
                            for s_info in info.get('secondaries_info', []):
                                print(f"  [DIAGNOSTIC] Active Secondary Track ID {s_info['id']}: "
                                      f"Points: {s_info['points']}, "
                                      f"Y: {s_info['y_range']}, "
                                      f"near_cx: {s_info['near_cx']:.3f}, "
                                      f"Conf: {s_info['confidence']:.3f}")
                    except Exception as e:
                        print(f"  [DIAGNOSTIC ERROR] Failed to print diagnostic stats: {e}")

        # Display UI Window (if not headless)
        if not args.no_display:
            cv2.imshow("RailGuard AI - Real-Time Rail Perception", frame_to_write)
            key = cv2.waitKey(1 if not paused else 30) & 0xFF
            if key == 27 or key == ord('q'):
                print("Exit requested by user.")
                break
            elif key == ord(' '):
                paused = not paused
                print("PAUSED" if paused else "RESUMED")
            elif key == ord('s'):
                snap_path = f"rail_samples/snapshot_frame_{frame_idx}.png"
                cv2.imwrite(snap_path, frame)
                print(f"Saved snapshot to {snap_path}")
            elif key == ord('+') or key == ord('='):
                DISPLAY_TRACK_LENGTH_PX += 50
                print(f"DISPLAY_TRACK_LENGTH_PX increased to {DISPLAY_TRACK_LENGTH_PX} px")
            elif key == ord('-') or key == ord('_'):
                DISPLAY_TRACK_LENGTH_PX = max(50, DISPLAY_TRACK_LENGTH_PX - 50)
                print(f"DISPLAY_TRACK_LENGTH_PX decreased to {DISPLAY_TRACK_LENGTH_PX} px")

    if cap:
        cap.release()
    out_writer.release()
    if not args.no_display:
        cv2.destroyAllWindows()

    print(f"\nProcessing complete! Annotated video saved to: {args.output}")

if __name__ == "__main__":
    main()
