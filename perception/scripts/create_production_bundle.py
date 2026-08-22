import os
import zipfile

BASE_DIR = r"C:\RailGuardAI"
PACKAGE_DIR = os.path.join(BASE_DIR, "railguard_standalone_bundle")
ZIP_PATH = os.path.join(BASE_DIR, "RailGuardAI_Production_Model_Bundle.zip")

os.makedirs(os.path.join(PACKAGE_DIR, "scripts"), exist_ok=True)
os.makedirs(os.path.join(PACKAGE_DIR, "model"), exist_ok=True)

# 1. Copy model
import shutil
shutil.copy2(os.path.join(BASE_DIR, "production_model", "rail_model.onnx"), os.path.join(PACKAGE_DIR, "model", "rail_model.onnx"))
shutil.copy2(os.path.join(BASE_DIR, "production_model", "model_card.json"), os.path.join(PACKAGE_DIR, "model", "model_card.json"))

# 2. Copy scripts
shutil.copy2(os.path.join(BASE_DIR, "scripts", "heatmap_decoder.py"), os.path.join(PACKAGE_DIR, "scripts", "heatmap_decoder.py"))
shutil.copy2(os.path.join(BASE_DIR, "scripts", "run_video_inspection.py"), os.path.join(PACKAGE_DIR, "scripts", "run_video_inspection.py"))

# 3. Create requirements.txt
with open(os.path.join(PACKAGE_DIR, "requirements.txt"), "w") as f:
    f.write("onnxruntime>=1.16.0\nopencv-python>=4.8.0\nnumpy>=1.24.0\n")

# 4. Create simple 1-command quickstart run_demo.py in root
quickstart_code = """import os
import sys
import subprocess

if __name__ == "__main__":
    video_path = sys.argv[1] if len(sys.argv) > 1 else "track.mp4"
    if not os.path.exists(video_path):
        print(f"Usage: python run_demo.py <path_to_video>")
        sys.exit(1)
    
    cmd = [
        sys.executable,
        os.path.join("scripts", "run_video_inspection.py"),
        "--video", video_path,
        "--model", os.path.join("model", "rail_model.onnx"),
        "--line-thickness", "1"
    ]
    print("Running RailGuard AI Production Inference...")
    subprocess.run(cmd)
"""
with open(os.path.join(PACKAGE_DIR, "run_demo.py"), "w") as f:
    f.write(quickstart_code)

# 5. Create README.md
readme_content = """# RailGuard AI - Production Rail Perception Bundle

High-accuracy real-time multi-track rail perception engine powered by MobileNetV3-Large + Spatial Feature Pyramid Network (FPN).

---

## 📦 What's Included:
1. `model/rail_model.onnx` — Trained production spatial heatmap ONNX model (512x288).
2. `scripts/heatmap_decoder.py` — Multi-track sub-pixel active ridge decoder.
3. `scripts/run_video_inspection.py` — Real-time video inspection runner & overlay engine.
4. `run_demo.py` — Turnkey 1-command demo runner.
5. `requirements.txt` — Minimal dependencies (`onnxruntime`, `opencv-python`, `numpy`).

---

## 🚀 How to Run:

### 1. Install Dependencies:
```bash
pip install -r requirements.txt
```

### 2. Run Video Inspection:
```bash
# Run on any video:
python scripts/run_video_inspection.py --video your_video.mp4 --model model/rail_model.onnx --line-thickness 1

# Or run the quickstart script:
python run_demo.py your_video.mp4
```

---

## 🎮 Keyboard Controls:
* **[SPACE]** — Pause / Resume playback
* **[S]** — Save high-resolution inspection snapshot
* **[Q] / [ESC]** — Exit
"""
with open(os.path.join(PACKAGE_DIR, "README.md"), "w", encoding="utf-8") as f:
    f.write(readme_content)

# 6. Zip everything up
with zipfile.ZipFile(ZIP_PATH, 'w', zipfile.ZIP_DEFLATED) as zipf:
    for root, dirs, files in os.walk(PACKAGE_DIR):
        for file in files:
            full_path = os.path.join(root, file)
            rel_path = os.path.relpath(full_path, PACKAGE_DIR)
            zipf.write(full_path, rel_path)

size_mb = os.path.getsize(ZIP_PATH) / (1024 * 1024)
print(f"Created standalone bundle: {ZIP_PATH} ({size_mb:.2f} MB)")
