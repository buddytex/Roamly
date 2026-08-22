import os
import cv2
import numpy as np
import onnxruntime as ort
import shutil

BASE_DIR = r"C:\RailGuardAI"
MODEL_PATH = os.path.join(BASE_DIR, "production_model", "rail_model.onnx")
VIDEO_PATH = os.path.join(BASE_DIR, "track.mp4")
OUTPUT_DIR = os.path.join(BASE_DIR, "rail_samples", "visual_showcase")
os.makedirs(OUTPUT_DIR, exist_ok=True)

import sys
sys.path.insert(0, os.path.join(BASE_DIR, "scripts"))
from heatmap_decoder import extract_multi_tracks_from_heatmap
from run_video_inspection import draw_rail_overlay, draw_hud

sess = ort.InferenceSession(MODEL_PATH)
inp_name = sess.get_inputs()[0].name
inp_shape = sess.get_inputs()[0].shape # [batch, 3, 288, 512]
inp_h, inp_w = inp_shape[2], inp_shape[3]

MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD  = np.array([0.229, 0.224, 0.225], dtype=np.float32)

cap = cv2.VideoCapture(VIDEO_PATH)
test_frames = [100, 150, 300, 450, 500]

saved_paths = []

for frame_idx in test_frames:
    cap.set(cv2.CAP_PROP_POS_FRAMES, frame_idx)
    ret, raw_frame = cap.read()
    if not ret:
        continue

    # Resize raw to 1280x720
    disp_frame = cv2.resize(raw_frame, (1280, 720))
    raw_copy = disp_frame.copy()

    # Preprocess
    rgb = cv2.resize(disp_frame, (inp_w, inp_h))
    rgb = cv2.cvtColor(rgb, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    norm = ((rgb - MEAN) / STD).transpose(2, 0, 1)[np.newaxis, :, :, :]

    # Inference
    heatmap = sess.run(None, {inp_name: norm.astype(np.float32)})[0][0]
    primary_track, secondary_tracks = extract_multi_tracks_from_heatmap(heatmap)

    # Draw Thin 1px Laser Overlay
    annotated = disp_frame.copy()
    draw_rail_overlay(annotated, primary_track, secondary_tracks, thickness=1)
    draw_hud(annotated, 59.9, 14.5, primary_track, secondary_tracks, frame_idx, 1000, "RailHeatmapNet (Production)")

    # Side-by-side comparison: Left = Raw, Right = Annotated
    divider = np.ones((720, 6, 3), dtype=np.uint8) * 200
    side_by_side = np.hstack([cv2.resize(raw_copy, (640, 360)), cv2.resize(annotated, (640, 360))])

    # Save outputs
    annotated_path = os.path.join(OUTPUT_DIR, f"inspection_frame_{frame_idx}.jpg")
    sbs_path = os.path.join(OUTPUT_DIR, f"side_by_side_frame_{frame_idx}.jpg")

    cv2.imwrite(annotated_path, annotated)
    cv2.imwrite(sbs_path, side_by_side)

    prim_l, prim_r = primary_track
    print(f"Frame {frame_idx:4d} -> Primary Left Pts: {len(prim_l)} | Right Pts: {len(prim_r)} | Sec Tracks: {len(secondary_tracks)}")
    saved_paths.append(annotated_path)

cap.release()
print("\nShowcase generation complete! Output saved to:", OUTPUT_DIR)
