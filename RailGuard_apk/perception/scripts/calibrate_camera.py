"""
calibrate_camera.py
───────────────────
Interactive calibration tool. Run this once for your camera/video.
You click on the physical rails in 8 frames → tool computes a
per-Y-row correction offset and saves camera_calibration.json.

Controls:
  LEFT CLICK  → add point to current rail side
  R           → switch from Left Rail to Right Rail
  N           → accept frame and move to next
  Z           → undo last point
  ESC         → abort
"""
import cv2, json, os, sys
import numpy as np

VIDEO_PATH  = r"C:\RailGuardAI\track.mp4"
MODEL_PATH  = r"C:\RailGuardAI\model_v4\rail_model.onnx"   # use v3 if v4 not ready
CALIB_OUT   = r"C:\RailGuardAI\camera_calibration.json"
OUT_W, OUT_H = 1280, 720
NUM_SAMPLES  = 16

# ── Fixed Y grid (must match training) ──────────────────────────────────────
FIXED_Y = np.linspace(1.0, 0.38, NUM_SAMPLES).astype(np.float32)

# Frames to calibrate on (spread across the video)
CAL_FRAMES = [50, 150, 250, 350, 450, 600, 750, 900]


def load_onnx():
    import onnxruntime as ort
    if os.path.exists(MODEL_PATH):
        return ort.InferenceSession(MODEL_PATH)
    # Fallback to v3
    v3 = MODEL_PATH.replace("model_v4", "model_v3")
    if os.path.exists(v3):
        print(f"[WARN] model_v4 not found, using {v3}")
        return ort.InferenceSession(v3)
    return None


def run_model(session, frame_bgr):
    MEAN = np.array([0.485, 0.456, 0.406], np.float32)
    STD  = np.array([0.229, 0.224, 0.225], np.float32)
    small = cv2.resize(frame_bgr, (256, 144))
    rgb   = cv2.cvtColor(small, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    norm  = (rgb - MEAN) / STD
    inp   = norm.transpose(2, 0, 1)[np.newaxis]
    out   = session.run(None, {session.get_inputs()[0].name: inp})[0][0]
    return out


def draw_guide(frame, left_pts, right_pts, mode, frame_no, total):
    h, w = frame.shape[:2]
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, 90), (15, 15, 20), -1)
    cv2.addWeighted(overlay, 0.8, frame, 0.2, 0, frame)

    cv2.putText(frame, f"CAMERA CALIBRATION  [{frame_no}/{total}]",
                (20, 32), cv2.FONT_HERSHEY_DUPLEX, 0.8, (255,255,255), 2)

    if mode == "left":
        status = "CLICK LEFT RAIL points (top → bottom)  |  [R] switch to Right"
        col    = (0, 230, 255)
    else:
        status = "CLICK RIGHT RAIL points (top → bottom) |  [N] next frame"
        col    = (100, 50, 255)
    cv2.putText(frame, status, (20, 68), cv2.FONT_HERSHEY_SIMPLEX, 0.55, col, 1)
    cv2.putText(frame, "[Z] undo   [ESC] abort", (w-280, 68),
                cv2.FONT_HERSHEY_SIMPLEX, 0.45, (180,180,180), 1)

    # Draw clicked left points
    for i, (px, py) in enumerate(left_pts):
        cv2.circle(frame, (px, py), 7, (0, 230, 255), -1)
        cv2.putText(frame, str(i), (px+8, py+5),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0,230,255), 1)
    if len(left_pts) >= 2:
        cv2.polylines(frame, [np.array(left_pts, np.int32)],
                      False, (0, 230, 255), 2, cv2.LINE_AA)

    # Draw clicked right points
    for i, (px, py) in enumerate(right_pts):
        cv2.circle(frame, (px, py), 7, (100, 50, 255), -1)
        cv2.putText(frame, str(i), (px+8, py+5),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.45, (100,50,255), 1)
    if len(right_pts) >= 2:
        cv2.polylines(frame, [np.array(right_pts, np.int32)],
                      False, (100, 50, 255), 2, cv2.LINE_AA)


def fit_correction(model_out, clicked_pts, side):
    """
    Compute per-Y-row X offset: clicked_x(Y) - model_x(Y).
    Returns coefficients of a degree-2 polynomial: offset = f(Y).
    """
    if len(clicked_pts) < 3:
        return None

    # Sort clicked points top→bottom by Y
    pts = sorted(clicked_pts, key=lambda p: p[1])
    c_ys = np.array([p[1] / OUT_H for p in pts], dtype=np.float32)
    c_xs = np.array([p[0] / OUT_W for p in pts], dtype=np.float32)

    # Model prediction at same Y rows (evaluate polynomial interpolation)
    if side == "left":
        m_xs = model_out[0:16]
    else:
        m_xs = model_out[16:32]
    m_ys = FIXED_Y  # model Y grid

    # Interpolate model X at the clicked Y positions
    m_xs_at_c = np.interp(c_ys, m_ys[::-1], m_xs[::-1])

    offsets = c_xs - m_xs_at_c

    # Fit degree-2 polynomial: offset = f(Y)
    coeffs = np.polyfit(c_ys, offsets, 2)
    return coeffs.tolist()


def main():
    print("=" * 60)
    print("RAILGUARD AI — CAMERA CALIBRATION TOOL")
    print("=" * 60)
    print(f"Video  : {VIDEO_PATH}")
    print(f"Output : {CALIB_OUT}")
    print()

    session = load_onnx()
    if session is None:
        print("[WARN] No ONNX model found. Calibration will be geometry-only.")

    cap = cv2.VideoCapture(VIDEO_PATH)
    if not cap.isOpened():
        print(f"ERROR: Cannot open {VIDEO_PATH}")
        sys.exit(1)

    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    cal_frames   = [f for f in CAL_FRAMES if f < total_frames]
    print(f"Calibration frames: {cal_frames}")
    print("\nInstructions:")
    print("  1. Click 4+ points on the LEFT rail (any order, tool sorts by Y)")
    print("  2. Press [R] to switch to RIGHT rail")
    print("  3. Click 4+ points on the RIGHT rail")
    print("  4. Press [N] to confirm and go to the next frame")
    print("  5. After all frames, calibration is saved automatically\n")

    all_l_coeffs = []
    all_r_coeffs = []

    cv2.namedWindow("RailGuard Calibration", cv2.WINDOW_NORMAL)
    cv2.resizeWindow("RailGuard Calibration", OUT_W, OUT_H)

    click_buf = {"left": [], "right": []}
    mode      = ["left"]   # mutable for closure
    cur_frame = [None]
    model_out = [None]

    def on_mouse(event, x, y, flags, param):
        if event == cv2.EVENT_LBUTTONDOWN:
            click_buf[mode[0]].append((x, y))

    cv2.setMouseCallback("RailGuard Calibration", on_mouse)

    for fi, frame_no in enumerate(cal_frames):
        cap.set(cv2.CAP_PROP_POS_FRAMES, frame_no)
        ret, raw = cap.read()
        if not ret:
            continue
        frame_orig = cv2.resize(raw, (OUT_W, OUT_H))

        click_buf["left"].clear()
        click_buf["right"].clear()
        mode[0] = "left"

        if session is not None:
            model_out[0] = run_model(session, frame_orig)

        while True:
            disp = frame_orig.copy()

            # Overlay model prediction if available
            if model_out[0] is not None:
                mo = model_out[0]
                l_xs, r_xs = mo[0:16], mo[16:32]
                l_ys, r_ys = mo[32:48], mo[48:64]
                lp = [(int(x*OUT_W), int(y*OUT_H)) for x, y in zip(l_xs, l_ys)]
                rp = [(int(x*OUT_W), int(y*OUT_H)) for x, y in zip(r_xs, r_ys)]
                cv2.polylines(disp, [np.array(lp, np.int32)], False, (60,60,200), 2)
                cv2.polylines(disp, [np.array(rp, np.int32)], False, (60,60,200), 2)
                cv2.putText(disp, "Blue = current model prediction",
                            (20, OUT_H-20), cv2.FONT_HERSHEY_SIMPLEX,
                            0.5, (60,60,200), 1)

            draw_guide(disp, click_buf["left"], click_buf["right"],
                       mode[0], fi+1, len(cal_frames))
            cv2.imshow("RailGuard Calibration", disp)
            key = cv2.waitKey(30) & 0xFF

            if key == 27:   # ESC
                print("Calibration aborted.")
                cap.release(); cv2.destroyAllWindows(); sys.exit(0)
            elif key == ord('r') or key == ord('R'):
                mode[0] = "right"
            elif key == ord('z') or key == ord('Z'):
                if click_buf[mode[0]]:
                    click_buf[mode[0]].pop()
            elif key == ord('n') or key == ord('N'):
                if len(click_buf["left"]) < 2 or len(click_buf["right"]) < 2:
                    print(f"  Frame {frame_no}: Need ≥2 points on each rail. Keep clicking.")
                    continue
                # Compute correction
                if model_out[0] is not None:
                    lc = fit_correction(model_out[0], click_buf["left"],  "left")
                    rc = fit_correction(model_out[0], click_buf["right"], "right")
                else:
                    lc = rc = None
                if lc: all_l_coeffs.append(lc)
                if rc: all_r_coeffs.append(rc)
                print(f"  Frame {frame_no}: ✓  L={len(click_buf['left'])}pts  "
                      f"R={len(click_buf['right'])}pts")
                break

    cap.release()
    cv2.destroyAllWindows()

    if not all_l_coeffs and not all_r_coeffs:
        print("No calibration data collected.")
        return

    # Average the polynomial coefficients across all frames
    avg_l = np.mean(all_l_coeffs, axis=0).tolist() if all_l_coeffs else [0, 0, 0]
    avg_r = np.mean(all_r_coeffs, axis=0).tolist() if all_r_coeffs else [0, 0, 0]

    calib = {
        "description": "Per-Y-row X offset correction. offset_x = polyval(coeffs, y_norm)",
        "fixed_y_grid": np.linspace(1.0, 0.38, NUM_SAMPLES).tolist(),
        "left_offset_coeffs":  avg_l,
        "right_offset_coeffs": avg_r,
        "frames_used": len(all_l_coeffs),
    }
    with open(CALIB_OUT, "w") as f:
        json.dump(calib, f, indent=2)

    print(f"\n✅  Calibration saved → {CALIB_OUT}")
    print(f"    Left  offset poly: {[round(c,5) for c in avg_l]}")
    print(f"    Right offset poly: {[round(c,5) for c in avg_r]}")
    print("\nRun the inspection app — it will auto-load the calibration file.")


if __name__ == "__main__":
    main()
