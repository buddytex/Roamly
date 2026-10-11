package com.roamly.app.location

import android.content.Context

/**
 * Stores the Outside Tinkerspace GPS destination configuration.
 *
 * Destination coordinates are captured from the real device location
 * when the user explicitly calibrates a destination.
 *
 * The default coordinates are retained for reference but are not
 * considered calibrated until the user confirms a real GPS fix.
 */
object TinkerspaceLocationConfig {

    private const val PREFS_NAME = "roamly_tinkerspace_location"

    private const val KEY_OUTDOOR_LATITUDE = "outdoor_latitude"
    private const val KEY_OUTDOOR_LONGITUDE = "outdoor_longitude"
    private const val KEY_OUTDOOR_RADIUS = "outdoor_radius"
    private const val KEY_OUTDOOR_CALIBRATED = "outdoor_calibrated"

    private const val KEY_INDOOR_LATITUDE = "indoor_latitude"
    private const val KEY_INDOOR_LONGITUDE = "indoor_longitude"
    private const val KEY_INDOOR_RADIUS = "indoor_radius"
    private const val KEY_INDOOR_CALIBRATED = "indoor_calibrated"

    // Existing project coordinates, retained as uncalibrated defaults.
    const val DEFAULT_OUTDOOR_LATITUDE = 10.05428
    const val DEFAULT_OUTDOOR_LONGITUDE = 76.35408

    const val DEFAULT_INDOOR_LATITUDE = 10.05415
    const val DEFAULT_INDOOR_LONGITUDE = 76.35395

    // Requested geofence radius.
    const val DEFAULT_GEOFENCE_RADIUS_METERS = 10.0f

    // Require a location fix with accuracy suitable for the test.
    const val REQUIRED_ACCURACY_METERS = 10.0f

    var outdoorLatitude: Double = DEFAULT_OUTDOOR_LATITUDE
        private set

    var outdoorLongitude: Double = DEFAULT_OUTDOOR_LONGITUDE
        private set

    var outdoorRadiusMeters: Float = DEFAULT_GEOFENCE_RADIUS_METERS
        private set

    var outdoorCalibrated: Boolean = false
        private set

    var indoorLatitude: Double = DEFAULT_INDOOR_LATITUDE
        private set

    var indoorLongitude: Double = DEFAULT_INDOOR_LONGITUDE
        private set

    var indoorRadiusMeters: Float = DEFAULT_GEOFENCE_RADIUS_METERS
        private set

    var indoorCalibrated: Boolean = false
        private set

    /**
     * Loads saved configuration from persistent local storage.
     */
    fun load(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        outdoorLatitude = prefs.getFloat(
            KEY_OUTDOOR_LATITUDE,
            DEFAULT_OUTDOOR_LATITUDE.toFloat()
        ).toDouble()

        outdoorLongitude = prefs.getFloat(
            KEY_OUTDOOR_LONGITUDE,
            DEFAULT_OUTDOOR_LONGITUDE.toFloat()
        ).toDouble()

        outdoorRadiusMeters = prefs.getFloat(
            KEY_OUTDOOR_RADIUS,
            DEFAULT_GEOFENCE_RADIUS_METERS
        )

        outdoorCalibrated = prefs.getBoolean(
            KEY_OUTDOOR_CALIBRATED,
            false
        )

        indoorLatitude = prefs.getFloat(
            KEY_INDOOR_LATITUDE,
            DEFAULT_INDOOR_LATITUDE.toFloat()
        ).toDouble()

        indoorLongitude = prefs.getFloat(
            KEY_INDOOR_LONGITUDE,
            DEFAULT_INDOOR_LONGITUDE.toFloat()
        ).toDouble()

        indoorRadiusMeters = prefs.getFloat(
            KEY_INDOOR_RADIUS,
            DEFAULT_GEOFENCE_RADIUS_METERS
        )

        indoorCalibrated = prefs.getBoolean(
            KEY_INDOOR_CALIBRATED,
            false
        )
    }

    /**
     * Saves the real current device location as the outdoor destination.
     */
    fun calibrateOutdoor(
        context: Context,
        latitude: Double,
        longitude: Double
    ) {
        require(latitude in -90.0..90.0) {
            "Invalid latitude"
        }

        require(longitude in -180.0..180.0) {
            "Invalid longitude"
        }

        outdoorLatitude = latitude
        outdoorLongitude = longitude
        outdoorRadiusMeters = DEFAULT_GEOFENCE_RADIUS_METERS
        outdoorCalibrated = true

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_OUTDOOR_LATITUDE, latitude.toFloat())
            .putFloat(KEY_OUTDOOR_LONGITUDE, longitude.toFloat())
            .putFloat(KEY_OUTDOOR_RADIUS, outdoorRadiusMeters)
            .putBoolean(KEY_OUTDOOR_CALIBRATED, true)
            .apply()
    }

    /**
     * Saves the real current device location as the indoor destination.
     * This is optional and can be calibrated separately.
     */
    fun calibrateIndoor(
        context: Context,
        latitude: Double,
        longitude: Double
    ) {
        require(latitude in -90.0..90.0) {
            "Invalid latitude"
        }

        require(longitude in -180.0..180.0) {
            "Invalid longitude"
        }

        indoorLatitude = latitude
        indoorLongitude = longitude
        indoorRadiusMeters = DEFAULT_GEOFENCE_RADIUS_METERS
        indoorCalibrated = true

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_INDOOR_LATITUDE, latitude.toFloat())
            .putFloat(KEY_INDOOR_LONGITUDE, longitude.toFloat())
            .putFloat(KEY_INDOOR_RADIUS, indoorRadiusMeters)
            .putBoolean(KEY_INDOOR_CALIBRATED, true)
            .apply()
    }

    /**
     * Resets both destinations to the original defaults.
     * The defaults will require calibration again.
     */
    fun resetToDefaults(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        prefs.edit().clear().apply()

        outdoorLatitude = DEFAULT_OUTDOOR_LATITUDE
        outdoorLongitude = DEFAULT_OUTDOOR_LONGITUDE
        outdoorRadiusMeters = DEFAULT_GEOFENCE_RADIUS_METERS
        outdoorCalibrated = false

        indoorLatitude = DEFAULT_INDOOR_LATITUDE
        indoorLongitude = DEFAULT_INDOOR_LONGITUDE
        indoorRadiusMeters = DEFAULT_GEOFENCE_RADIUS_METERS
        indoorCalibrated = false
    }
}