package com.roamly.app.location

/**
 * =========================================================================
 * TINKERSPACE LOCATION CONFIGURATION
 * =========================================================================
 *
 * Developers & Hackathon Presenters:
 * To configure the live GPS coordinates for the Tinkerspace demonstration,
 * edit the latitude and longitude constants below.
 *
 * Defaults:
 * - STOP 1 (Outdoor Zone): Outside Tinkerspace Building
 *   Lat: 10.05428, Lon: 76.35408, Radius: 35.0 meters
 *
 * - STOP 2 (Indoor Zone): Back Inside Tinkerspace Building
 *   Lat: 10.05415, Lon: 76.35395, Radius: 25.0 meters
 *
 * Required GPS Accuracy:
 *   Readings with accuracy worse than [REQUIRED_ACCURACY_METERS] are filtered
 *   to avoid false boundary triggers indoors.
 *
 * NOTE: For stage presentations, rehearsing, or testing in low-GPS environments,
 * the UI provides a clearly labelled "Demo: Simulate Arrival" fallback button.
 * =========================================================================
 */
object TinkerspaceLocationConfig {

    // --- STOP 1: OUTDOOR ARRIVAL ZONE ---
    const val DEFAULT_OUTDOOR_LATITUDE: Double = 10.05428
    const val DEFAULT_OUTDOOR_LONGITUDE: Double = 76.35408
    const val DEFAULT_OUTDOOR_RADIUS_METERS: Float = 35.0f

    // --- STOP 2: INDOOR RETURN ZONE ---
    const val DEFAULT_INDOOR_LATITUDE: Double = 10.05415
    const val DEFAULT_INDOOR_LONGITUDE: Double = 76.35395
    const val DEFAULT_INDOOR_RADIUS_METERS: Float = 25.0f

    // --- ACCURACY THRESHOLD ---
    const val REQUIRED_ACCURACY_METERS: Float = 50.0f

    // Mutable runtime coordinates if presenters configure them dynamically
    var outdoorLatitude: Double = DEFAULT_OUTDOOR_LATITUDE
    var outdoorLongitude: Double = DEFAULT_OUTDOOR_LONGITUDE
    var outdoorRadiusMeters: Float = DEFAULT_OUTDOOR_RADIUS_METERS

    var indoorLatitude: Double = DEFAULT_INDOOR_LATITUDE
    var indoorLongitude: Double = DEFAULT_INDOOR_LONGITUDE
    var indoorRadiusMeters: Float = DEFAULT_INDOOR_RADIUS_METERS

    /**
     * Resets runtime coordinates to project defaults.
     */
    fun resetToDefaults() {
        outdoorLatitude = DEFAULT_OUTDOOR_LATITUDE
        outdoorLongitude = DEFAULT_OUTDOOR_LONGITUDE
        outdoorRadiusMeters = DEFAULT_OUTDOOR_RADIUS_METERS

        indoorLatitude = DEFAULT_INDOOR_LATITUDE
        indoorLongitude = DEFAULT_INDOOR_LONGITUDE
        indoorRadiusMeters = DEFAULT_INDOOR_RADIUS_METERS
    }
}
