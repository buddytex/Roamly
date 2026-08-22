"""
heatmap_decoder.py
──────────────────
Production Track Perception Heatmap Decoder & Multi-Track Segmenter.

Architecture:
1. Multi-Band Physical Seed Extraction (Near & Mid-field visual bands).
2. Peak-Centered Sub-Pixel Ridge Tracing with Adaptive Trajectory Curvature.
3. Common-Y Coordinate Synchronization with Controlled Geometric Recovery.
4. True Common-Y Physical Optical Gauge & Geometric Validation.
5. Strict Primary Ego-Track Qualification & Scoring.
6. Robust Secondary Track Extraction & Spatial Deduplication.
7. Transparent Candidate Rejection Reason & Recovery Diagnostics.
"""
import numpy as np

# ── Configurable Geometric Constants ───────────────────────────────────────
PERCEPTION_HORIZON_Y    = 0.35   # Maximum upward perception limit (0.0=top, 1.0=bottom)
MIN_GAUGE_WIDTH_NORM    = 0.018  # Minimum optical gauge width before track naturally terminates
MAX_GAUGE_WIDTH_NORM    = 0.550  # Maximum optical gauge width in near field
MIN_TRACK_GAUGE_RATIO   = 0.070  # Minimum seed gauge ratio
MAX_TRACK_GAUGE_RATIO   = 0.480  # Maximum seed gauge ratio
SEC_MERGE_DIST_NORM     = 0.055  # Spatial threshold to merge duplicate secondary tracks
SEARCH_WINDOW_PX        = 10     # Ridge tracking search radius in pixels
MAX_TRAJECTORY_DEV_PX   = 8.0    # Maximum deviation from predicted curved trajectory before extrapolation

# Short-term physical geometric prior for ego track recovery during single-rail dropouts
LAST_VALID_PRIMARY_GEOMETRY = {
    "valid": False,
    "frames_since_update": 999,
    "near_cx": 0.48,
    "near_gauge": 0.23,
    "g0": 0.20,  # Base gauge at mid-image
    "g1": 0.15   # Gauge expansion rate towards foreground
}

LAST_DEBUG_INFO = {}


def find_top_k_peaks(arr, k=6, min_val=0.020, excl_w=18):
    """
    Extracts dominant sub-pixel peaks from a 1D activation slice.
    Uses 3-point parabolic peak refinement around the argmax to prevent window drift.
    """
    a = arr.copy()
    peaks = []
    L = len(arr)
    for _ in range(k):
        max_idx = int(np.argmax(a))
        max_val = float(a[max_idx])
        if max_val < min_val:
            break

        # Sub-pixel quadratic interpolation around true local peak
        if 0 < max_idx < L - 1:
            alpha = float(arr[max_idx - 1])
            beta = float(arr[max_idx])
            gamma = float(arr[max_idx + 1])
            denom = 2.0 * (alpha - 2.0 * beta + gamma)
            if abs(denom) > 1e-5:
                delta = (alpha - gamma) / denom
                sub_x = float(max_idx + np.clip(delta, -0.5, 0.5))
            else:
                sub_x = float(max_idx)
        else:
            sub_x = float(max_idx)

        peaks.append((sub_x, max_val))

        # Spatial non-maximum suppression
        supp_start = max(0, max_idx - excl_w)
        supp_end = min(L, max_idx + excl_w + 1)
        a[supp_start:supp_end] = 0.0

    return peaks


def extract_seeds_from_band(hm_l, hm_r, band_start_norm, band_end_norm, min_conf=0.020):
    """Pairs left and right peaks within a specified vertical image band."""
    H, W = hm_l.shape
    band_start = int(H * band_start_norm)
    band_end = int(H * band_end_norm)

    bot_l = np.mean(hm_l[band_start:band_end], axis=0)
    bot_r = np.mean(hm_r[band_start:band_end], axis=0)

    excl_w = int(W * 0.040)
    peaks_l = find_top_k_peaks(bot_l, k=6, min_val=min_conf, excl_w=excl_w)
    peaks_r = find_top_k_peaks(bot_r, k=6, min_val=min_conf, excl_w=excl_w)

    seeds = []

    for lx, conf_l in peaks_l:
        # Sort plausible right peaks by confidence
        cand_rx_list = []
        for i, (rx, conf_r) in enumerate(peaks_r):
            spread = rx - lx
            if (MIN_TRACK_GAUGE_RATIO * W) <= spread <= (MAX_TRACK_GAUGE_RATIO * W):
                conf_ratio = max(conf_l, conf_r) / (min(conf_l, conf_r) + 1e-4)
                if conf_ratio <= 6.0:
                    cand_rx_list.append((rx, conf_r, conf_l + conf_r))

        cand_rx_list.sort(key=lambda x: x[2], reverse=True)

        for rx, conf_r, total_conf in cand_rx_list[:2]:  # Keep top 2 right rail pairings per left rail
            track_cx = (lx + rx) / 2.0
            dist_to_center = abs(track_cx - (W / 2.0)) / (W / 2.0)
            seeds.append({
                "start_lx": lx,
                "start_rx": rx,
                "conf_l": conf_l,
                "conf_r": conf_r,
                "conf": total_conf,
                "dist_to_center": dist_to_center,
                "gauge_px": rx - lx,
                "band": f"{band_start_norm:.2f}-{band_end_norm:.2f}"
            })

    return seeds


def find_bottom_track_seeds(hm_l, hm_r, min_conf=0.020):
    """
    Extracts physical track seeds using dual-band discovery:
    1. Near-field band ($Y \in [0.65, 0.95]$) for ego track and primary foreground rails.
    2. Mid-field band ($Y \in [0.50, 0.80]$) for parallel sidings and switch tracks.
    """
    H, W = hm_l.shape

    # Near band (foreground)
    near_seeds = extract_seeds_from_band(hm_l, hm_r, 0.65, 0.95, min_conf=min_conf)
    # Mid band (for sidings that begin/end mid-field)
    mid_seeds = extract_seeds_from_band(hm_l, hm_r, 0.50, 0.80, min_conf=min_conf)

    all_seeds = []
    used_centers = []

    # Prioritize near seeds first, then add distinct mid seeds
    for s in near_seeds:
        cx = (s["start_lx"] + s["start_rx"]) / (2.0 * W)
        all_seeds.append(s)
        used_centers.append(cx)

    for s in mid_seeds:
        cx = (s["start_lx"] + s["start_rx"]) / (2.0 * W)
        if not any(abs(cx - existing_cx) < SEC_MERGE_DIST_NORM for existing_cx in used_centers):
            all_seeds.append(s)
            used_centers.append(cx)

    return all_seeds


def trace_single_rail_ridge(hm, start_x, start_y_norm=0.98, horizon_y=PERCEPTION_HORIZON_Y, min_val=0.004, search_window=SEARCH_WINDOW_PX, max_missed=12):
    """
    Traces a continuous rail ridge upwards toward the horizon.
    - Uses wide capture window (24 px) before initial lock-on, then tightens to search_window (10 px).
    - Uses peak-centered subpixel refinement (avoids full-window centroid pull).
    - Uses adaptive trajectory prediction (pred_x = curr_x + dx).
    - Bridges gaps (up to max_missed rows) using directional trajectory extrapolation.
    - Returns structured array of shape (N, 3): [x_norm, y_norm, is_observed].
    """
    H, W = hm.shape
    pts = []
    curr_x = float(start_x)
    dx = 0.0
    min_y_limit = int(H * horizon_y)
    start_y = min(H - 4, int(H * start_y_norm))
    missed_count = 0

    for y in range(start_y, min_y_limit, -1):
        row = hm[y]
        pred_x = curr_x + dx
        # Wide initial capture before lock-on; tight search window once tracking
        win = 24 if len(pts) == 0 else search_window
        w_start = max(0, int(round(pred_x)) - win)
        w_end = min(W, int(round(pred_x)) + win + 1)

        sub = row[w_start:w_end]
        if len(sub) == 0:
            break

        local_max_idx = int(np.argmax(sub))
        local_max_val = float(sub[local_max_idx])
        global_peak_x = float(w_start + local_max_idx)

        if local_max_val >= min_val:
            # Sub-pixel parabolic refinement strictly around the local peak (±1 pixel)
            p_idx = w_start + local_max_idx
            if 0 < p_idx < W - 1:
                v_l = float(row[p_idx - 1])
                v_c = float(row[p_idx])
                v_r = float(row[p_idx + 1])
                denom = 2.0 * (v_l - 2.0 * v_c + v_r)
                if abs(denom) > 1e-5:
                    delta = (v_l - v_r) / denom
                    refined_x = float(p_idx + np.clip(delta, -0.5, 0.5))
                else:
                    refined_x = global_peak_x
            else:
                refined_x = global_peak_x

            # Adaptive curve trajectory check (only active after lock-on)
            trajectory_dev = abs(refined_x - pred_x)
            if len(pts) > 2 and trajectory_dev > MAX_TRAJECTORY_DEV_PX:
                missed_count += 1
                if missed_count > max_missed:
                    break
                curr_x = curr_x + dx
                pts.append((curr_x / float(W), y / float(H), 0.0))  # 0.0 = extrapolated
            else:
                missed_count = 0
                step_dx = refined_x - curr_x
                dx = step_dx * 0.40 + dx * 0.60
                curr_x = refined_x
                pts.append((curr_x / float(W), y / float(H), 1.0))  # 1.0 = observed
        else:
            if len(pts) == 0:
                continue
            else:
                missed_count += 1
                if missed_count > max_missed:
                    break
                curr_x = curr_x + dx
                pts.append((curr_x / float(W), y / float(H), 0.0))  # 0.0 = extrapolated

    if len(pts) < 6:
        return np.empty((0, 3), dtype=np.float32)
    return np.array(pts, dtype=np.float32)


def synchronize_and_interpolate_y(raw_l, raw_r, min_overlap_pts=6, allow_recovery=True):
    """
    Synchronizes left and right rail detections onto a strict COMMON Y GRID.
    Includes CONTROLLED GEOMETRIC RECOVERY:
    If one rail is strong (>= 24 pts, span >= 0.22) and the second rail is partially
    missing due to temporary lighting/shadow dropout, reconstructs the missing rail
    using the physical optical perspective gauge prior.
    """
    len_l = len(raw_l)
    len_r = len(raw_r)

    # 1. Check for normal direct overlap
    if len_l >= min_overlap_pts and len_r >= min_overlap_pts:
        y_l_min, y_l_max = float(raw_l[:, 1].min()), float(raw_l[:, 1].max())
        y_r_min, y_r_max = float(raw_r[:, 1].min()), float(raw_r[:, 1].max())

        y_start = min(y_l_max, y_r_max)
        y_end   = max(y_l_min, y_r_min)

        # Sufficient direct vertical overlap (>= 0.04 span)
        if (y_start - y_end) >= 0.035:
            step_y = 1.0 / 288.0
            common_y = np.arange(y_start, y_end - 1e-5, -step_y, dtype=np.float32)

            if len(common_y) >= min_overlap_pts:
                idx_l = np.argsort(raw_l[:, 1])
                idx_r = np.argsort(raw_r[:, 1])

                interp_lx = np.interp(common_y, raw_l[idx_l, 1], raw_l[idx_l, 0]).astype(np.float32)
                interp_rx = np.interp(common_y, raw_r[idx_r, 1], raw_r[idx_r, 0]).astype(np.float32)

                interp_lobs = np.interp(common_y, raw_l[idx_l, 1], raw_l[idx_l, 2]).astype(np.float32)
                interp_robs = np.interp(common_y, raw_r[idx_r, 1], raw_r[idx_r, 2]).astype(np.float32)

                synced_l = np.stack([interp_lx, common_y], axis=-1)
                synced_r = np.stack([interp_rx, common_y], axis=-1)
                mean_obs = float(np.mean((interp_lobs + interp_robs) / 2.0))

                return synced_l, synced_r, mean_obs, "ok", {"recovery": False, "missing_side": "NONE"}

    # 2. Controlled Geometric Recovery for Single-Rail Dropouts
    if not allow_recovery:
        empty = np.empty((0, 2), dtype=np.float32)
        return empty, empty, 0.0, "no_vertical_overlap", {"recovery": False, "missing_side": "NONE"}

    strong_side = None
    strong_raw = None
    weak_raw = None

    if len_l >= 24 and (float(raw_l[:, 1].max()) - float(raw_l[:, 1].min())) >= 0.22:
        strong_side = "LEFT"
        strong_raw = raw_l
        weak_raw = raw_r
    elif len_r >= 24 and (float(raw_r[:, 1].max()) - float(raw_r[:, 1].min())) >= 0.22:
        strong_side = "RIGHT"
        strong_raw = raw_r
        weak_raw = raw_l

    if strong_side is None:
        empty = np.empty((0, 2), dtype=np.float32)
        return empty, empty, 0.0, "insufficient_raw_points_for_recovery", {"recovery": False, "missing_side": "NONE"}

    # Optical Perspective Gauge Model: gauge(y) = g0 + g1 * (y - 0.50)
    # Contract smoothly towards the horizon
    g0 = LAST_VALID_PRIMARY_GEOMETRY.get("g0", 0.20)
    g1 = LAST_VALID_PRIMARY_GEOMETRY.get("g1", 0.15)

    # Anchor gauge using observed weak points if available
    if len(weak_raw) >= 4:
        idx_s = np.argsort(strong_raw[:, 1])
        idx_w = np.argsort(weak_raw[:, 1])
        w_mid_y = float(np.median(weak_raw[:, 1]))
        w_mid_x = float(np.median(weak_raw[:, 0]))
        s_at_w_y = float(np.interp(w_mid_y, strong_raw[idx_s, 1], strong_raw[idx_s, 0]))
        obs_gauge = abs(w_mid_x - s_at_w_y)
        if 0.08 <= obs_gauge <= 0.45:
            # Adjust base gauge to match local observation
            g0 = obs_gauge - g1 * (w_mid_y - 0.50)

    # Construct common grid spanning the full strong rail
    y_s_min = float(strong_raw[:, 1].min())
    y_s_max = float(strong_raw[:, 1].max())

    step_y = 1.0 / 288.0
    common_y = np.arange(y_s_max, y_s_min - 1e-5, -step_y, dtype=np.float32)

    if len(common_y) < 12:
        empty = np.empty((0, 2), dtype=np.float32)
        return empty, empty, 0.0, "recovered_span_too_short", {"recovery": False, "missing_side": "NONE"}

    idx_s = np.argsort(strong_raw[:, 1])
    interp_sx = np.interp(common_y, strong_raw[idx_s, 1], strong_raw[idx_s, 0]).astype(np.float32)
    interp_sobs = np.interp(common_y, strong_raw[idx_s, 1], strong_raw[idx_s, 2]).astype(np.float32)

    # Synthesize the complementary rail using the physical optical gauge profile
    gauge_profile = np.clip(g0 + g1 * (common_y - 0.50), MIN_GAUGE_WIDTH_NORM, MAX_GAUGE_WIDTH_NORM).astype(np.float32)

    if strong_side == "LEFT":
        synced_l = np.stack([interp_sx, common_y], axis=-1)
        interp_rx = interp_sx + gauge_profile

        # Incorporate observed weak rail points where available
        if len(weak_raw) >= 4:
            idx_w = np.argsort(weak_raw[:, 1])
            w_y_min, w_y_max = float(weak_raw[:, 1].min()), float(weak_raw[:, 1].max())
            mask_w = (common_y >= w_y_min) & (common_y <= w_y_max)
            if np.any(mask_w):
                obs_rx = np.interp(common_y[mask_w], weak_raw[idx_w, 1], weak_raw[idx_w, 0]).astype(np.float32)
                interp_rx[mask_w] = obs_rx

        synced_r = np.stack([interp_rx, common_y], axis=-1)
        mean_obs = float(np.mean(interp_sobs) * 0.55)
        missing_side = "RIGHT"
    else:
        synced_r = np.stack([interp_sx, common_y], axis=-1)
        interp_lx = interp_sx - gauge_profile

        if len(weak_raw) >= 4:
            idx_w = np.argsort(weak_raw[:, 1])
            w_y_min, w_y_max = float(weak_raw[:, 1].min()), float(weak_raw[:, 1].max())
            mask_w = (common_y >= w_y_min) & (common_y <= w_y_max)
            if np.any(mask_w):
                obs_lx = np.interp(common_y[mask_w], weak_raw[idx_w, 1], weak_raw[idx_w, 0]).astype(np.float32)
                interp_lx[mask_w] = obs_lx

        synced_l = np.stack([interp_lx, common_y], axis=-1)
        mean_obs = float(np.mean(interp_sobs) * 0.55)
        missing_side = "LEFT"

    return synced_l, synced_r, mean_obs, "ok", {"recovery": True, "missing_side": missing_side}


def clip_and_validate_track_pair(synced_l, synced_r, min_pts=8):
    r"""
    Performs physical gauge and geometric validation on true common-Y coordinates:
    1. Left rail must be strictly left of Right rail (X_l < X_r).
    2. Optical gauge spread must satisfy physical bounds.
    3. Gauge continuity: $|\Delta \text{spread}| \le 0.055$ per row.
    4. Tolerant perspective decay: allows up to 30% expansion per row for off-axis tracks.
    """
    if len(synced_l) < min_pts or len(synced_r) < min_pts:
        empty = np.empty((0, 2), dtype=np.float32)
        return empty, empty, {"valid": False, "reason": f"insufficient_synced_pts({len(synced_l)})"}

    N = len(synced_l)
    valid_count = 0
    prev_spread = None
    gauge_values = []
    failure_reason = "ok"

    for i in range(N):
        lx, ly = synced_l[i]
        rx, ry = synced_r[i]

        spread = rx - lx

        # 1. Ordering and minimum optical gauge
        if spread < MIN_GAUGE_WIDTH_NORM:
            failure_reason = f"gauge_below_min({spread:.3f}<{MIN_GAUGE_WIDTH_NORM})"
            break

        # 2. Maximum physical gauge limit
        if spread > MAX_GAUGE_WIDTH_NORM:
            failure_reason = f"gauge_above_max({spread:.3f}>{MAX_GAUGE_WIDTH_NORM})"
            break

        # 3. Smooth gauge continuity
        if prev_spread is not None:
            delta_spread = abs(spread - prev_spread)
            if delta_spread > 0.055:
                failure_reason = f"gauge_jump({delta_spread:.3f}>0.055)"
                break

            # 4. Tolerant perspective decay
            if spread > (prev_spread * 1.30 + 0.020):
                failure_reason = f"perspective_expansion_too_large({spread:.3f}>{prev_spread * 1.30 + 0.020:.3f})"
                break

        prev_spread = spread
        gauge_values.append(spread)
        valid_count += 1

    if valid_count < min_pts:
        empty = np.empty((0, 2), dtype=np.float32)
        return empty, empty, {"valid": False, "reason": f"{failure_reason}_at_pt{valid_count}"}

    final_l = synced_l[:valid_count]
    final_r = synced_r[:valid_count]

    metrics = {
        "valid": True,
        "points": valid_count,
        "bottom_gauge": float(gauge_values[0]),
        "min_gauge": float(np.min(gauge_values)),
        "max_gauge": float(np.max(gauge_values)),
        "mean_gauge": float(np.mean(gauge_values)),
        "gauge_std": float(np.std(gauge_values)),
        "reason": "valid"
    }

    return final_l, final_r, metrics


def compute_track_candidate_score(cand_meta):
    """
    Calculates a deterministic physical ego-track primary score.
    """
    if not cand_meta["is_valid"]:
        return -999.0

    near_cx = cand_meta["near_cx"]
    center_offset = abs(near_cx - 0.5)
    center_score = max(0.0, 1.0 - 2.8 * center_offset)

    y_min = cand_meta["y_min"]
    y_max = cand_meta["y_max"]
    coverage_y = max(0.0, y_max - y_min)
    max_possible_span = 1.0 - PERCEPTION_HORIZON_Y
    coverage_score = min(1.0, coverage_y / max_possible_span)

    pts = cand_meta["points"]
    length_score = min(1.0, pts / 60.0)

    conf_score = min(1.0, cand_meta["conf"] / 1.5)
    obs_score = cand_meta["obs_ratio"]

    bg = cand_meta["bottom_gauge"]
    if 0.12 <= bg <= 0.42:
        gauge_score = 1.0
    else:
        gauge_score = max(0.1, 1.0 - abs(bg - 0.25) * 4.0)

    raw_l_len = len(cand_meta["raw_l"])
    raw_r_len = len(cand_meta["raw_r"])
    max_raw = max(raw_l_len, raw_r_len, 1)
    imbalance = abs(raw_l_len - raw_r_len) / float(max_raw)
    
    # Imbalance penalty applies only if recovery was not used
    imbalance_penalty = 0.0 if cand_meta.get("is_recovered", False) else 0.35 * (imbalance ** 2)
    fragment_penalty = 0.45 if (coverage_y < 0.18 or pts < 20) else 0.0
    far_field_penalty = 0.50 if y_max < 0.80 else 0.0

    total_score = (
        0.35 * center_score +
        0.25 * coverage_score +
        0.15 * length_score +
        0.10 * conf_score +
        0.05 * obs_score +
        0.10 * gauge_score -
        imbalance_penalty -
        fragment_penalty -
        far_field_penalty
    )

    return float(total_score)


def extract_multi_tracks_from_heatmap(heatmap, horizon_y=PERCEPTION_HORIZON_Y, min_conf=0.020, min_pts=8):
    """
    Decodes physical tracks from model heatmaps with full common-Y synchronization,
    controlled single-rail dropout recovery, and decoupled candidate classification.
    """
    global LAST_DEBUG_INFO, LAST_VALID_PRIMARY_GEOMETRY
    H, W = heatmap.shape[1], heatmap.shape[2]
    hm_l = heatmap[0].copy()
    hm_r = heatmap[1].copy()

    # 1. Dual-band seed extraction
    seeds = find_bottom_track_seeds(hm_l, hm_r, min_conf=min_conf)

    if not seeds:
        empty = np.empty((0, 2), dtype=np.float32)
        LAST_DEBUG_INFO = {
            "raw_candidates_count": 0,
            "clipped_candidates_count": 0,
            "primary_candidates_count": 0,
            "final_primary_count": 0,
            "secondary_candidates_count": 0,
            "final_secondary_count": 0,
            "rejected_candidates_count": 0,
            "primary_raw_pts_l": 0,
            "primary_raw_pts_r": 0,
            "primary_raw_points_l": [],
            "primary_raw_points_r": [],
            "primary_valid_pts_l": 0,
            "primary_valid_pts_r": 0,
            "primary_min_y": 0.0,
            "primary_max_y": 0.0,
            "horizon_y": horizon_y,
            "secondaries_info": [],
            "candidate_diagnostics": []
        }
        return (empty, empty), []

    # 2. Trace and evaluate ALL candidate seeds
    evaluated_candidates = []

    for idx, s in enumerate(seeds):
        raw_l = trace_single_rail_ridge(hm_l, s["start_lx"], start_y_norm=0.96, horizon_y=horizon_y, min_val=min_conf * 0.25)
        raw_r = trace_single_rail_ridge(hm_r, s["start_rx"], start_y_norm=0.96, horizon_y=horizon_y, min_val=min_conf * 0.25)

        # Synchronize onto common Y grid with controlled geometric recovery
        synced_l, synced_r, obs_ratio, sync_status, rec_meta = synchronize_and_interpolate_y(
            raw_l, raw_r, min_overlap_pts=min_pts, allow_recovery=True
        )

        l_span = f"Y:{raw_l[:,1].min():.2f}-{raw_l[:,1].max():.2f}" if len(raw_l) > 0 else "None"
        r_span = f"Y:{raw_r[:,1].min():.2f}-{raw_r[:,1].max():.2f}" if len(raw_r) > 0 else "None"

        if sync_status != "ok":
            evaluated_candidates.append({
                "candidate_id": idx,
                "seed": s,
                "raw_l": raw_l,
                "raw_r": raw_r,
                "valid_l": np.empty((0, 2)),
                "valid_r": np.empty((0, 2)),
                "is_valid": False,
                "points": 0,
                "near_cx": float((s["start_lx"] + s["start_rx"]) / (2.0 * W)),
                "y_min": 0.0,
                "y_max": 0.0,
                "conf": float(s["conf"]),
                "obs_ratio": 0.0,
                "bottom_gauge": float(s["gauge_px"] / W),
                "is_recovered": False,
                "primary_score": -999.0,
                "rejection_reason": f"SYNC_FAILED: {sync_status} (L_pts={len(raw_l)} {l_span}, R_pts={len(raw_r)} {r_span})"
            })
            continue

        # Physical optical gauge validation
        valid_l, valid_r, gauge_meta = clip_and_validate_track_pair(synced_l, synced_r, min_pts=min_pts)
        is_valid = gauge_meta["valid"]

        near_cx = float((s["start_lx"] + s["start_rx"]) / (2.0 * W))
        y_min = float(valid_l[:, 1].min()) if is_valid and len(valid_l) > 0 else 0.0
        y_max = float(valid_l[:, 1].max()) if is_valid and len(valid_l) > 0 else 0.0
        if is_valid and len(valid_l) > 0:
            near_cx = float((valid_l[0, 0] + valid_r[0, 0]) / 2.0)

        cand_meta = {
            "candidate_id": idx,
            "seed": s,
            "raw_l": raw_l,
            "raw_r": raw_r,
            "valid_l": valid_l,
            "valid_r": valid_r,
            "is_valid": is_valid,
            "points": len(valid_l) if is_valid else 0,
            "near_cx": near_cx,
            "y_min": y_min,
            "y_max": y_max,
            "conf": float(s["conf"]),
            "obs_ratio": obs_ratio,
            "bottom_gauge": gauge_meta.get("bottom_gauge", float(s["gauge_px"] / W)),
            "gauge_meta": gauge_meta,
            "is_recovered": rec_meta["recovery"],
            "missing_side": rec_meta["missing_side"],
            "rejection_reason": "none" if is_valid else f"GAUGE_FAILED: {gauge_meta.get('reason')}"
        }

        cand_meta["primary_score"] = compute_track_candidate_score(cand_meta)
        evaluated_candidates.append(cand_meta)

def is_candidate_in_primary_exclusion_corridor(cand, primary_track, lateral_margin=0.040):
    """
    Evaluates whether a secondary candidate lies inside or too close to the primary track corridor.
    Calculates exact curve-to-curve centerline distance, rail distance, and rail crossing.
    """
    prim_l, prim_r = primary_track
    if len(prim_l) < 6 or len(prim_r) < 6:
        return False, "ok"

    cand_l, cand_r = cand["valid_l"], cand["valid_r"]
    if len(cand_l) < 4 or len(cand_r) < 4:
        return True, "TOO_FEW_POINTS"

    y_p_min, y_p_max = float(prim_l[:, 1].min()), float(prim_l[:, 1].max())
    y_c_min, y_c_max = float(cand_l[:, 1].min()), float(cand_l[:, 1].max())

    y_start = min(y_p_max, y_c_max)
    y_end   = max(y_p_min, y_c_min)

    if (y_start - y_end) < 0.030:
        # Minimal vertical overlap; check near-field center offset if available
        if abs(cand["near_cx"] - primary_track[0][0, 0]) < 0.08:
            return True, "PRIMARY_CORRIDOR_OVERLAP"
        return False, "ok"

    step_y = 1.0 / 288.0
    common_y = np.arange(y_start, y_end - 1e-5, -step_y, dtype=np.float32)
    if len(common_y) < 6:
        return False, "ok"

    idx_pl = np.argsort(prim_l[:, 1])
    idx_pr = np.argsort(prim_r[:, 1])
    idx_cl = np.argsort(cand_l[:, 1])
    idx_cr = np.argsort(cand_r[:, 1])

    p_lx = np.interp(common_y, prim_l[idx_pl, 1], prim_l[idx_pl, 0])
    p_rx = np.interp(common_y, prim_r[idx_pr, 1], prim_r[idx_pr, 0])
    c_lx = np.interp(common_y, cand_l[idx_cl, 1], cand_l[idx_cl, 0])
    c_rx = np.interp(common_y, cand_r[idx_cr, 1], cand_r[idx_cr, 0])

    p_cx = (p_lx + p_rx) / 2.0
    c_cx = (c_lx + c_rx) / 2.0

    # 1. Primary Duplicate Check: both rails are very close to primary rails
    mean_dl = float(np.mean(np.abs(c_lx - p_lx)))
    mean_dr = float(np.mean(np.abs(c_rx - p_rx)))
    mean_dc = float(np.mean(np.abs(c_cx - p_cx)))

    if mean_dl < 0.045 and mean_dr < 0.045:
        return True, f"PRIMARY_DUPLICATE(dl={mean_dl:.3f},dr={mean_dr:.3f})"

    if mean_dc < 0.075:
        return True, f"PRIMARY_CENTERLINE_OVERLAP(dc={mean_dc:.3f})"

    # 2. Rail Crossing / Gauge Penetration Check
    # Secondary rails must not cross into the primary gauge
    crosses_inside = np.any((c_lx > (p_lx - 0.015)) & (c_lx < (p_rx + 0.015))) or \
                     np.any((c_rx > (p_lx - 0.015)) & (c_rx < (p_rx + 0.015)))
    if crosses_inside:
        return True, "CROSSES_PRIMARY_CORRIDOR"

    return False, "ok"


def are_secondary_candidates_duplicates(cand_a, cand_b, merge_dist_threshold=0.048):
    """
    Evaluates whether two secondary candidates represent the same physical railway track
    using full curve-to-curve distance over their common Y span.
    """
    a_l, a_r = cand_a["valid_l"], cand_a["valid_r"]
    b_l, b_r = cand_b["valid_l"], cand_b["valid_r"]

    if len(a_l) < 4 or len(b_l) < 4:
        return False

    y_a_min, y_a_max = float(a_l[:, 1].min()), float(a_l[:, 1].max())
    y_b_min, y_b_max = float(b_l[:, 1].min()), float(b_l[:, 1].max())

    y_start = min(y_a_max, y_b_max)
    y_end   = max(y_a_min, y_b_min)

    if (y_start - y_end) < 0.035:
        # Minimal vertical overlap; check near_cx
        return abs(cand_a["near_cx"] - cand_b["near_cx"]) < 0.040

    step_y = 1.0 / 288.0
    common_y = np.arange(y_start, y_end - 1e-5, -step_y, dtype=np.float32)
    if len(common_y) < 6:
        return False

    idx_al = np.argsort(a_l[:, 1])
    idx_ar = np.argsort(a_r[:, 1])
    idx_bl = np.argsort(b_l[:, 1])
    idx_br = np.argsort(b_r[:, 1])

    a_lx = np.interp(common_y, a_l[idx_al, 1], a_l[idx_al, 0])
    a_rx = np.interp(common_y, a_r[idx_ar, 1], a_r[idx_ar, 0])
    b_lx = np.interp(common_y, b_l[idx_bl, 1], b_l[idx_bl, 0])
    b_rx = np.interp(common_y, b_r[idx_br, 1], b_r[idx_br, 0])

    mean_dist = float(np.mean((np.abs(a_lx - b_lx) + np.abs(a_rx - b_rx)) / 2.0))
    return mean_dist < merge_dist_threshold


def extract_multi_tracks_from_heatmap(heatmap, horizon_y=PERCEPTION_HORIZON_Y, min_conf=0.020, min_pts=8):
    """
    Decodes physical tracks from model heatmaps with strict Primary Ego isolation,
    dynamic primary exclusion corridor, and curve-to-curve secondary deduplication.
    """
    global LAST_DEBUG_INFO, LAST_VALID_PRIMARY_GEOMETRY
    H, W = heatmap.shape[1], heatmap.shape[2]
    hm_l = heatmap[0].copy()
    hm_r = heatmap[1].copy()

    # 1. Dual-band seed extraction
    seeds = find_bottom_track_seeds(hm_l, hm_r, min_conf=min_conf)

    if not seeds:
        empty = np.empty((0, 2), dtype=np.float32)
        LAST_DEBUG_INFO = {
            "raw_candidates_count": 0,
            "clipped_candidates_count": 0,
            "primary_candidates_count": 0,
            "final_primary_count": 0,
            "secondary_candidates_count": 0,
            "final_secondary_count": 0,
            "rejected_candidates_count": 0,
            "primary_raw_pts_l": 0,
            "primary_raw_pts_r": 0,
            "primary_raw_points_l": [],
            "primary_raw_points_r": [],
            "primary_valid_pts_l": 0,
            "primary_valid_pts_r": 0,
            "primary_min_y": 0.0,
            "primary_max_y": 0.0,
            "horizon_y": horizon_y,
            "secondaries_info": [],
            "candidate_diagnostics": []
        }
        return (empty, empty), []

    # 2. Trace and evaluate ALL candidate seeds
    evaluated_candidates = []

    for idx, s in enumerate(seeds):
        raw_l = trace_single_rail_ridge(hm_l, s["start_lx"], start_y_norm=0.96, horizon_y=horizon_y, min_val=min_conf * 0.25)
        raw_r = trace_single_rail_ridge(hm_r, s["start_rx"], start_y_norm=0.96, horizon_y=horizon_y, min_val=min_conf * 0.25)

        # Synchronize onto common Y grid with controlled geometric recovery
        synced_l, synced_r, obs_ratio, sync_status, rec_meta = synchronize_and_interpolate_y(
            raw_l, raw_r, min_overlap_pts=min_pts, allow_recovery=True
        )

        l_span = f"Y:{raw_l[:,1].min():.2f}-{raw_l[:,1].max():.2f}" if len(raw_l) > 0 else "None"
        r_span = f"Y:{raw_r[:,1].min():.2f}-{raw_r[:,1].max():.2f}" if len(raw_r) > 0 else "None"

        if sync_status != "ok":
            evaluated_candidates.append({
                "candidate_id": idx,
                "seed": s,
                "raw_l": raw_l,
                "raw_r": raw_r,
                "valid_l": np.empty((0, 2)),
                "valid_r": np.empty((0, 2)),
                "is_valid": False,
                "points": 0,
                "near_cx": float((s["start_lx"] + s["start_rx"]) / (2.0 * W)),
                "y_min": 0.0,
                "y_max": 0.0,
                "conf": float(s["conf"]),
                "obs_ratio": 0.0,
                "bottom_gauge": float(s["gauge_px"] / W),
                "is_recovered": False,
                "primary_score": -999.0,
                "rejection_reason": f"SYNC_FAILED: {sync_status} (L_pts={len(raw_l)} {l_span}, R_pts={len(raw_r)} {r_span})"
            })
            continue

        # Physical optical gauge validation
        valid_l, valid_r, gauge_meta = clip_and_validate_track_pair(synced_l, synced_r, min_pts=min_pts)
        is_valid = gauge_meta["valid"]

        near_cx = float((s["start_lx"] + s["start_rx"]) / (2.0 * W))
        y_min = float(valid_l[:, 1].min()) if is_valid and len(valid_l) > 0 else 0.0
        y_max = float(valid_l[:, 1].max()) if is_valid and len(valid_l) > 0 else 0.0
        if is_valid and len(valid_l) > 0:
            near_cx = float((valid_l[0, 0] + valid_r[0, 0]) / 2.0)

        cand_meta = {
            "candidate_id": idx,
            "seed": s,
            "raw_l": raw_l,
            "raw_r": raw_r,
            "valid_l": valid_l,
            "valid_r": valid_r,
            "is_valid": is_valid,
            "points": len(valid_l) if is_valid else 0,
            "near_cx": near_cx,
            "y_min": y_min,
            "y_max": y_max,
            "conf": float(s["conf"]),
            "obs_ratio": obs_ratio,
            "bottom_gauge": gauge_meta.get("bottom_gauge", float(s["gauge_px"] / W)),
            "gauge_meta": gauge_meta,
            "is_recovered": rec_meta["recovery"],
            "missing_side": rec_meta["missing_side"],
            "rejection_reason": "none" if is_valid else f"GAUGE_FAILED: {gauge_meta.get('reason')}"
        }

        cand_meta["primary_score"] = compute_track_candidate_score(cand_meta)
        evaluated_candidates.append(cand_meta)

    # 3. Separate candidates into Primary Ego Candidates vs Secondary Candidates
    ego_candidates = []
    raw_secondary_candidates = []
    rejected_candidates = []

    for c in evaluated_candidates:
        if not c["is_valid"]:
            rejected_candidates.append(c)
            continue

        raw_l_len = len(c["raw_l"])
        raw_r_len = len(c["raw_r"])
        max_raw = max(raw_l_len, raw_r_len, 1)
        imbalance = abs(raw_l_len - raw_r_len) / float(max_raw)

        # Primary Ego Track Criteria:
        # - Extends into foreground (y_max >= 0.80)
        # - Centered in vehicle corridor (0.28 <= near_cx <= 0.72)
        # - Longitudinal coverage >= 0.20 span and points >= 24
        # - Left/Right point balance < 0.75 (unless recovered from valid geometric prior)
        is_ego_qualified = (
            c["y_max"] >= 0.80 and
            0.28 <= c["near_cx"] <= 0.72 and
            (c["y_max"] - c["y_min"]) >= 0.20 and
            c["points"] >= 24 and
            (imbalance < 0.75 or c["is_recovered"])
        )

        if is_ego_qualified:
            ego_candidates.append(c)
        else:
            raw_secondary_candidates.append(c)

    # Sort ego candidates deterministically by primary score
    ego_candidates.sort(key=lambda c: c["primary_score"], reverse=True)

    primary_track = (np.empty((0, 2), dtype=np.float32), np.empty((0, 2), dtype=np.float32))
    raw_prim_l = np.empty((0, 3), dtype=np.float32)
    raw_prim_r = np.empty((0, 3), dtype=np.float32)

    # 4. SELECT AND FREEZE PRIMARY EGO TRACK
    if ego_candidates:
        primary_cand = ego_candidates[0]
        primary_track = (primary_cand["valid_l"], primary_cand["valid_r"])
        raw_prim_l = primary_cand["raw_l"]
        raw_prim_r = primary_cand["raw_r"]

        # Update physical geometric prior for ego track
        pl, pr = primary_track
        if len(pl) >= 20:
            gauges = pr[:, 0] - pl[:, 0]
            ys = pl[:, 1]
            p_fit = np.polyfit(ys - 0.50, gauges, 1)
            LAST_VALID_PRIMARY_GEOMETRY["valid"] = True
            LAST_VALID_PRIMARY_GEOMETRY["frames_since_update"] = 0
            LAST_VALID_PRIMARY_GEOMETRY["near_cx"] = float((pl[0, 0] + pr[0, 0]) / 2.0)
            LAST_VALID_PRIMARY_GEOMETRY["near_gauge"] = float(gauges[0])
            LAST_VALID_PRIMARY_GEOMETRY["g0"] = float(p_fit[1])
            LAST_VALID_PRIMARY_GEOMETRY["g1"] = float(p_fit[0])
    else:
        LAST_VALID_PRIMARY_GEOMETRY["frames_since_update"] += 1
        if LAST_VALID_PRIMARY_GEOMETRY["frames_since_update"] > 10:
            LAST_VALID_PRIMARY_GEOMETRY["valid"] = False

    # 5. SECONDARY CANDIDATE FILTERING & PRIMARY EXCLUSION CORRIDOR
    # Exclude ALL other ego candidate copies from secondary processing
    for c in ego_candidates[1:]:
        c["rejection_reason"] = "PRIMARY_DUPLICATE_SEED"
        rejected_candidates.append(c)

    filtered_secondary_candidates = []

    for c in raw_secondary_candidates:
        # Minimum Quality Policy:
        # - Points >= 24 (or >= 18 if vertical span >= 0.12 and conf >= 0.16)
        # - Vertical Y span >= 0.10
        # - Confidence >= 0.10
        y_span = c["y_max"] - c["y_min"]
        if c["points"] < 24 and not (c["points"] >= 18 and y_span >= 0.12 and c["conf"] >= 0.16):
            c["rejection_reason"] = f"TOO_FEW_POINTS({c['points']}pts)"
            rejected_candidates.append(c)
            continue

        if y_span < 0.10:
            c["rejection_reason"] = f"TOO_SHORT_Y_SPAN({y_span:.2f})"
            rejected_candidates.append(c)
            continue

        if c["conf"] < 0.10:
            c["rejection_reason"] = f"LOW_CONFIDENCE({c['conf']:.3f})"
            rejected_candidates.append(c)
            continue

        # Dynamic Primary Exclusion Corridor Validation
        if len(primary_track[0]) > 0:
            is_inside_prim, prim_rej_reason = is_candidate_in_primary_exclusion_corridor(c, primary_track)
            if is_inside_prim:
                c["rejection_reason"] = f"PRIMARY_EXCLUSION: {prim_rej_reason}"
                rejected_candidates.append(c)
                continue

        filtered_secondary_candidates.append(c)

    # 6. CURVE-TO-CURVE SECONDARY DEDUPLICATION
    # Sort secondary candidates deterministically by quality score (points * conf)
    filtered_secondary_candidates.sort(key=lambda c: c["points"] * c["conf"], reverse=True)

    final_secondary_candidates = []

    for c in filtered_secondary_candidates:
        # Check against already accepted secondaries
        is_dup = False
        for accepted in final_secondary_candidates:
            if are_secondary_candidates_duplicates(c, accepted):
                is_dup = True
                c["rejection_reason"] = f"SECONDARY_DUPLICATE(with ID #{accepted['candidate_id']+1})"
                rejected_candidates.append(c)
                break

        if not is_dup:
            final_secondary_candidates.append(c)

    # Cap maximum secondary tracks to top 2
    final_secondary_candidates = final_secondary_candidates[:2]

    secondary_tracks = []
    secondaries_info = []

    for c in final_secondary_candidates:
        secondary_tracks.append((c["valid_l"], c["valid_r"]))
        secondaries_info.append({
            "id": c["candidate_id"] + 1,
            "points": c["points"],
            "y_range": f"{c['y_min']:.2f} - {c['y_max']:.2f}",
            "confidence": c["conf"],
            "near_cx": c["near_cx"],
            "is_recovered": c["is_recovered"],
            "status": "Active"
        })

    prim_l, prim_r = primary_track
    has_primary = len(prim_l) > 0

    LAST_DEBUG_INFO = {
        "raw_candidates_count": len(seeds),
        "clipped_candidates_count": (1 if has_primary else 0) + len(secondary_tracks),
        "primary_candidates_count": len(ego_candidates),
        "final_primary_count": 1 if has_primary else 0,
        "secondary_candidates_count": len(final_secondary_candidates),
        "final_secondary_count": len(secondary_tracks),
        "rejected_candidates_count": len(rejected_candidates),
        "primary_raw_pts_l": len(raw_prim_l),
        "primary_raw_pts_r": len(raw_prim_r),
        "primary_raw_points_l": raw_prim_l[:, :2].tolist() if len(raw_prim_l) > 0 else [],
        "primary_raw_points_r": raw_prim_r[:, :2].tolist() if len(raw_prim_r) > 0 else [],
        "primary_valid_pts_l": len(prim_l),
        "primary_valid_pts_r": len(prim_r),
        "primary_min_y": float(prim_l[:, 1].min()) if has_primary else 0.0,
        "primary_max_y": float(prim_l[:, 1].max()) if has_primary else 0.0,
        "horizon_y": horizon_y,
        "secondaries_info": secondaries_info,
        "candidate_diagnostics": evaluated_candidates
    }

    return primary_track, secondary_tracks

