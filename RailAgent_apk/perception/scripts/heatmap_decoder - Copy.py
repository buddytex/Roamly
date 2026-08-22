"""
heatmap_decoder.py
──────────────────
Production Track-Validity-Horizon Active Ridge Follower & Multi-Track Segmenter.

Enforces a strict geometric Track Validity Horizon:
1. Prevents unstable far-field / vanishing-point extrapolation.
2. Synchronizes Left, Right, and Centerline boundaries to identical Y extents.
3. Automatically terminates the track when optical gauge resolution collapses.
4. Deduplicates secondary tracks and enforces valid physical track corridors.
"""
import numpy as np

# ── Configurable Geometric Constants ───────────────────────────────────────
TRACK_HORIZON_Y         = 0.35   # Maximum upward perception limit (0.0=top, 1.0=bottom)
MIN_GAUGE_WIDTH_NORM    = 0.022  # Minimum optical gauge width before track naturally terminates
MIN_TRACK_GAUGE_RATIO   = 0.08   # Minimum bottom seed gauge ratio
MAX_TRACK_GAUGE_RATIO   = 0.48   # Maximum bottom seed gauge ratio
SEC_MERGE_DIST_NORM     = 0.08   # Spatial threshold to merge duplicate secondary tracks

LAST_DEBUG_INFO = {}

def find_top_k_peaks(arr, k=4, min_val=0.025, excl_w=20):
    """Extracts dominant sub-pixel peaks from a 1D activation slice."""
    a = arr.copy()
    peaks = []
    for _ in range(k):
        max_idx = int(np.argmax(a))
        max_val = float(a[max_idx])
        if max_val < min_val:
            break
        w_start = max(0, max_idx - 5)
        w_end = min(len(arr), max_idx + 6)
        sub = arr[w_start:w_end]
        xs = np.arange(w_start, w_end)
        w_sum = np.sum(sub)
        sub_x = float(np.sum(xs * sub) / w_sum) if w_sum > 1e-5 else float(max_idx)
        peaks.append((sub_x, max_val))
        supp_start = max(0, max_idx - excl_w)
        supp_end = min(len(a), max_idx + excl_w + 1)
        a[supp_start:supp_end] = 0.0
    return peaks

def find_bottom_track_seeds(hm_l, hm_r, min_conf=0.025):
    """Pairs left and right peaks in the lower visual band into candidate physical tracks."""
    H, W = hm_l.shape
    bot_l = np.mean(hm_l[int(H * 0.65):int(H * 0.95)], axis=0)
    bot_r = np.mean(hm_r[int(H * 0.65):int(H * 0.95)], axis=0)

    peaks_l = find_top_k_peaks(bot_l, k=4, min_val=min_conf, excl_w=int(W * 0.06))
    peaks_r = find_top_k_peaks(bot_r, k=4, min_val=min_conf, excl_w=int(W * 0.06))

    seeds = []
    used_rx = set()

    for lx, conf_l in peaks_l:
        best_rx, best_conf, best_i = None, -1.0, -1
        for i, (rx, conf_r) in enumerate(peaks_r):
            if i in used_rx:
                continue
            spread = rx - lx
            if (MIN_TRACK_GAUGE_RATIO * W) <= spread <= (MAX_TRACK_GAUGE_RATIO * W):
                total_conf = conf_l + conf_r
                if total_conf > best_conf:
                    best_conf = total_conf
                    best_rx = rx
                    best_i = i

        if best_rx is not None:
            track_cx = (lx + best_rx) / 2.0
            dist_to_center = abs(track_cx - (W / 2.0))
            seeds.append({
                "start_lx": lx,
                "start_rx": best_rx,
                "dist_to_center": dist_to_center,
                "conf": best_conf
            })
            used_rx.add(best_i)

    # Sort seeds by distance to vehicle ego center (camera forward line)
    seeds.sort(key=lambda s: s["dist_to_center"])
    return seeds

def trace_single_rail_ridge(hm, start_x, horizon_y=TRACK_HORIZON_Y, min_val=0.010, search_window=8):
    """
    Traces a continuous rail ridge strictly up to the validity horizon.
    Allows skipping uninitialized rows at the bottom and tolerates short gaps
    via directional extrapolation.
    """
    H, W = hm.shape
    pts = []
    curr_x = float(start_x)
    dx = 0.0
    min_y_limit = int(H * horizon_y)
    missed_count = 0

    for y in range(H - 4, min_y_limit, -1):
        row = hm[y]
        pred_x = curr_x + dx
        w_start = max(0, int(round(pred_x)) - search_window)
        w_end = min(W, int(round(pred_x)) + search_window + 1)

        sub = row[w_start:w_end]
        if len(sub) == 0:
            break

        local_max_idx = np.argmax(sub)
        local_max_val = sub[local_max_idx]

        if local_max_val >= min_val:
            xs = np.arange(w_start, w_end)
            w_sum = np.sum(sub)
            if w_sum > 1e-5:
                sub_x = np.sum(xs * sub) / w_sum
                
                # Check for sudden lateral jumps (continuity check)
                if len(pts) > 0 and abs(sub_x - curr_x) > 6.0:
                    missed_count += 1
                    if missed_count > 3:
                        break
                    curr_x = curr_x + dx
                else:
                    missed_count = 0
                    dx = (sub_x - curr_x) * 0.35 + dx * 0.65
                    curr_x = sub_x
                    pts.append((curr_x / float(W), y / float(H)))
            else:
                if len(pts) == 0:
                    continue
                missed_count += 1
                if missed_count > 3:
                    break
                curr_x = curr_x + dx
        else:
            if len(pts) == 0:
                # Skip empty/low-confidence rows at the bottom before we establish track tracking
                continue
            else:
                # Keep tracking through short dropout gaps by extrapolating last trajectory
                missed_count += 1
                if missed_count > 3:
                    break
                curr_x = curr_x + dx

    if len(pts) < 6:
        return np.empty((0, 2), dtype=np.float32)
    return np.array(pts, dtype=np.float32)

def clip_and_validate_track_pair(raw_l, raw_r, horizon_y=TRACK_HORIZON_Y, min_pts=8):
    """
    Synchronizes Left & Right rails:
    1. Truncates to identical Y levels.
    2. Detects gauge collapse / crossing and stops there.
    3. Enforces physical validity up to the horizon.
    """
    if len(raw_l) < min_pts or len(raw_r) < min_pts:
        empty = np.empty((0, 2), dtype=np.float32)
        return empty, empty

    # Both arrays step backwards in Y (from bottom to top)
    min_len = min(len(raw_l), len(raw_r))
    l_pts = raw_l[:min_len].copy()
    r_pts = raw_r[:min_len].copy()

    # Step through from bottom upward, verifying physical gauge
    valid_count = 0
    prev_spread = None
    for i in range(min_len):
        lx, ly = l_pts[i]
        rx, ry = r_pts[i]

        spread = rx - lx
        if spread < MIN_GAUGE_WIDTH_NORM:
            break  # Track has visually converged into the vanishing point; stop here

        # Enforce gauge continuity and perspective contraction constraints
        if prev_spread is not None:
            if abs(spread - prev_spread) > 0.04:
                break
            if spread > (prev_spread * 1.15 + 0.01):
                break

        prev_spread = spread
        valid_count += 1

    if valid_count < min_pts:
        empty = np.empty((0, 2), dtype=np.float32)
        return empty, empty

    return l_pts[:valid_count], r_pts[:valid_count]

def extract_multi_tracks_from_heatmap(heatmap, horizon_y=TRACK_HORIZON_Y, min_conf=0.025, min_pts=8):
    """
    Decodes physical tracks with strict Track Validity Horizon clipping.
    """
    global LAST_DEBUG_INFO
    H, W = heatmap.shape[1], heatmap.shape[2]
    hm_l = heatmap[0].copy()
    hm_r = heatmap[1].copy()



    seeds = find_bottom_track_seeds(hm_l, hm_r, min_conf=min_conf)

    # Fallback to strongest lower band argmax if no discrete seed was matched
    if not seeds:
        start_lx = np.argmax(np.mean(hm_l[int(H * 0.65):int(H * 0.95)], axis=0))
        start_rx = np.argmax(np.mean(hm_r[int(H * 0.65):int(H * 0.95)], axis=0))
        if start_rx > start_lx and (start_rx - start_lx) >= (MIN_TRACK_GAUGE_RATIO * W):
            seeds = [{"start_lx": float(start_lx), "start_rx": float(start_rx), "dist_to_center": 0.0, "conf": 1.0}]

    if not seeds:
        empty = np.empty((0, 2), dtype=np.float32)
        LAST_DEBUG_INFO = {
            "raw_candidates_count": 0,
            "clipped_candidates_count": 0,
            "primary_candidates_count": 0,
            "final_primary_count": 0,
            "secondary_candidates_count": 0,
            "final_secondary_count": 0,
            "rejected_far_field_candidates_count": 0,
            "primary_raw_pts_l": 0,
            "primary_raw_pts_r": 0,
            "primary_raw_points_l": [],
            "primary_raw_points_r": [],
            "primary_valid_pts_l": 0,
            "primary_valid_pts_r": 0,
            "primary_min_y": 0.0,
            "primary_max_y": 0.0,
            "horizon_y": horizon_y,
            "secondaries_info": []
        }
        return (empty, empty), []

    # 1. Primary Ego Track (closest to vehicle center)
    primary_seed = seeds[0]
    raw_prim_l = trace_single_rail_ridge(hm_l, primary_seed["start_lx"], horizon_y=horizon_y)
    raw_prim_r = trace_single_rail_ridge(hm_r, primary_seed["start_rx"], horizon_y=horizon_y)

    prim_l, prim_r = clip_and_validate_track_pair(raw_prim_l, raw_prim_r, horizon_y=horizon_y, min_pts=min_pts)
    primary_track = (prim_l, prim_r)

    # 2. Secondary Tracks with deduplication
    secondary_tracks = []
    used_cx = []
    if len(prim_l) > 0:
        primary_cx = float((prim_l[-1, 0] + prim_r[-1, 0]) / 2.0)
        used_cx.append(primary_cx)

    rejected_far_field = 0
    secondaries_info = []

    for idx, s in enumerate(seeds[1:]):
        seed_cx = (s["start_lx"] + s["start_rx"]) / (2.0 * W)
        # Check if too close to primary or already existing secondary track
        if any(abs(seed_cx - cx) < SEC_MERGE_DIST_NORM for cx in used_cx):
            continue

        raw_sec_l = trace_single_rail_ridge(hm_l, s["start_lx"], horizon_y=horizon_y)
        raw_sec_r = trace_single_rail_ridge(hm_r, s["start_rx"], horizon_y=horizon_y)
        sec_l, sec_r = clip_and_validate_track_pair(raw_sec_l, raw_sec_r, horizon_y=horizon_y, min_pts=min_pts)

        is_valid = len(sec_l) >= min_pts and len(sec_r) >= min_pts
        if is_valid:
            secondary_tracks.append((sec_l, sec_r))
            track_cx = float((sec_l[-1, 0] + sec_r[-1, 0]) / 2.0)
            used_cx.append(track_cx)
            
            y_range = f"{float(sec_l[:, 1].min()):.2f} - {float(sec_l[:, 1].max()):.2f}" if len(sec_l) > 0 else "N/A"
            secondaries_info.append({
                "id": idx + 1,
                "points": len(sec_l),
                "y_range": y_range,
                "confidence": float(s["conf"]),
                "status": "Active"
            })
        else:
            rejected_far_field += 1
            if len(raw_sec_l) > 0 or len(raw_sec_r) > 0:
                y_range = "N/A"
                if len(sec_l) > 0:
                    y_range = f"{float(sec_l[:, 1].min()):.2f} - {float(sec_l[:, 1].max()):.2f}"
                secondaries_info.append({
                    "id": idx + 1,
                    "points": len(sec_l),
                    "y_range": y_range,
                    "confidence": float(s["conf"]),
                    "status": "Rejected"
                })

    primary_min_y = float(prim_l[:, 1].min()) if len(prim_l) > 0 else 0.0
    primary_max_y = float(prim_l[:, 1].max()) if len(prim_l) > 0 else 0.0

    LAST_DEBUG_INFO = {
        "raw_candidates_count": len(seeds),
        "clipped_candidates_count": (1 if len(prim_l) > 0 else 0) + len(secondary_tracks),
        "primary_candidates_count": 1,
        "final_primary_count": 1 if len(prim_l) > 0 else 0,
        "secondary_candidates_count": len(seeds) - 1,
        "final_secondary_count": len(secondary_tracks),
        "rejected_far_field_candidates_count": rejected_far_field,
        "primary_raw_pts_l": len(raw_prim_l),
        "primary_raw_pts_r": len(raw_prim_r),
        "primary_raw_points_l": raw_prim_l.tolist() if len(raw_prim_l) > 0 else [],
        "primary_raw_points_r": raw_prim_r.tolist() if len(raw_prim_r) > 0 else [],
        "primary_valid_pts_l": len(prim_l),
        "primary_valid_pts_r": len(prim_r),
        "primary_min_y": primary_min_y,
        "primary_max_y": primary_max_y,
        "horizon_y": horizon_y,
        "secondaries_info": secondaries_info
    }

    return primary_track, secondary_tracks
