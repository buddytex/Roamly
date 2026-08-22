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
    cv2.putText(frame, "RAILGUARD AI - REAL-TIME MULTI-TRACK PERCEPTION", (20, 30),
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
                overlay = frame.copy()
                cv2.fillPoly(overlay, [poly_pts], (220, 160, 40)) # Amber tint
                cv2.addWeighted(overlay, 0.12, frame, 0.88, 0, frame)

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
        overlay = frame.copy()
        cv2.fillPoly(overlay, [poly_pts], (40, 190, 80)) # Emerald green
        cv2.addWeighted(overlay, 0.15, frame, 0.85, 0, frame)

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
    parser.add_argument("--alpha", type=float, default=0.25, help="Temporal smoothing filter weight (lower = smoother)")
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

    # Exponential smoothing and tracking state
    smooth_l = None
    smooth_r = None
    alpha = args.alpha

    tracked_primary_x = None
    primary_lost_frames = 0
    last_valid_primary = None
    switch_counter = 0
    switch_target_cx = None

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
                    primary_track, secondary_tracks = heatmap_decoder.extract_multi_tracks_from_heatmap(
                        raw_out, horizon_y=0.35, min_conf=0.025
                    )

                    # ── Temporal Primary Track Association & Hysteresis Removed ──
                    track_list = []
                    if len(primary_track[0]) >= 4 and len(primary_track[1]) >= 4:
                        track_list.append(primary_track)
                    for sec in secondary_tracks:
                        if len(sec[0]) >= 4 and len(sec[1]) >= 4:
                            track_list.append(sec)

                    primary_idx = None

                    if track_list:
                        # Make a fresh decision each frame based on the score: length - 2.0 * distance to center
                        best_idx = max(range(len(track_list)), key=lambda i: (len(track_list[i][0]) / 32.0) - 2.0 * abs(((track_list[i][0][0, 0] + track_list[i][1][0, 0]) / 2.0) - 0.5))
                        best_cx = (track_list[best_idx][0][0, 0] + track_list[best_idx][1][0, 0]) / 2.0
                        
                        tracked_primary_x = best_cx
                        primary_lost_frames = 0
                        primary_idx = best_idx
                    else:
                        # Keep temporary dropout handling active
                        primary_lost_frames += 1
                        if primary_lost_frames <= 5 and last_valid_primary is not None:
                            primary_track = last_valid_primary
                            secondary_tracks = []
                        else:
                            primary_track = (np.empty((0, 2), dtype=np.float32), np.empty((0, 2), dtype=np.float32))
                            secondary_tracks = []
                            tracked_primary_x = None

                    if primary_idx is not None:
                        primary_track = track_list[primary_idx]
                        secondary_tracks = [track_list[i] for i in range(len(track_list)) if i != primary_idx]

                    if len(primary_track[0]) >= 4 and len(primary_track[1]) >= 4:
                         last_valid_primary = primary_track

                    # Temporal smoothing for heatmap model output
                    prim_l, prim_r = primary_track
                    if len(prim_l) >= 4 and len(prim_r) >= 4:
                        num_smooth_pts = 32
                        smooth_y_grid = np.linspace(1.0, 0.35, num_smooth_pts, dtype=np.float32)
                        
                        idx_l = np.argsort(prim_l[:, 1])
                        idx_r = np.argsort(prim_r[:, 1])
                        prim_l_sorted = prim_l[idx_l]
                        prim_r_sorted = prim_r[idx_r]
                        
                        curr_l_x = np.interp(smooth_y_grid, prim_l_sorted[:, 1], prim_l_sorted[:, 0]).astype(np.float32)
                        curr_r_x = np.interp(smooth_y_grid, prim_r_sorted[:, 1], prim_r_sorted[:, 0]).astype(np.float32)
                        
                        cur_l = np.stack([curr_l_x, smooth_y_grid], axis=-1)
                        cur_r = np.stack([curr_r_x, smooth_y_grid], axis=-1)
                        
                        if smooth_l is None or len(smooth_l) != num_smooth_pts:
                            smooth_l = cur_l.copy()
                            smooth_r = cur_r.copy()
                        else:
                            smooth_l = smooth_l + alpha * (cur_l - smooth_l)
                            smooth_r = smooth_r + alpha * (cur_r - smooth_r)
                            
                        primary_track = (smooth_l, smooth_r)
                    else:
                        smooth_l = None
                        smooth_r = None
                    
                    # Optional heatmap debug overlay on bottom left
                    if getattr(args, 'show_heatmap', False):
                        hm_vis = np.zeros((model_inp_h, model_inp_w, 3), dtype=np.uint8)
                        hm_vis[:, :, 0] = (np.clip(raw_out[0], 0, 1) * 255).astype(np.uint8) # Blue (Left)
                        hm_vis[:, :, 2] = (np.clip(raw_out[1], 0, 1) * 255).astype(np.uint8) # Red (Right)
                        hm_vis = cv2.resize(hm_vis, (256, 144))
                        frame[OUT_H-164:OUT_H-20, 20:276] = cv2.addWeighted(
                            frame[OUT_H-164:OUT_H-20, 20:276], 0.3, hm_vis, 0.7, 0)
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
                            print(f"  [DIAGNOSTIC] Horizon Y threshold: {args.horizon:.2f}")
                            print(f"  [DIAGNOSTIC] Candidates:")
                            print(f"    Raw seeds: {info['raw_candidates_count']}")
                            print(f"    Candidates after horizon clipping: {info['clipped_candidates_count']}")
                            print(f"    Primary candidates: {info['primary_candidates_count']}")
                            print(f"    Final primary: {info['final_primary_count']}")
                            print(f"    Secondary candidates: {info['secondary_candidates_count']}")
                            print(f"    Final secondary: {info['final_secondary_count']}")
                            print(f"    Rejected far-field candidates: {info['rejected_far_field_candidates_count']}")
                            print(f"  [DIAGNOSTIC] Primary track:")
                            print(f"    Raw point count: Left: {info['primary_raw_pts_l']}, Right: {info['primary_raw_pts_r']}")
                            print(f"    Valid point count: Left: {info['primary_valid_pts_l']}, Right: {info['primary_valid_pts_r']}")
                            print(f"    Minimum Y: {info['primary_min_y']:.2f}")
                            print(f"    Maximum Y: {info['primary_max_y']:.2f}")
                            print(f"    Horizon Y: {info['horizon_y']:.2f}")
                            
                            for s_info in info.get('secondaries_info', []):
                                print(f"  [DIAGNOSTIC] Secondary track ID {s_info['id']}: "
                                      f"Point count: {s_info['points']}, "
                                      f"Y range: {s_info['y_range']}, "
                                      f"Confidence: {s_info['confidence']:.3f}, "
                                      f"Status: {s_info['status']}")
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
