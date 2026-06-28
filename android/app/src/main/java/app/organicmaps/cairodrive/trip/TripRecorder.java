package app.organicmaps.cairodrive.trip;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.util.Geo;
import app.organicmaps.sdk.util.log.CairoLog;
import java.util.Locale;

/// Accumulates a single driving trip from a stream of location samples. Tracks
/// total distance, elapsed and moving time, and max/average speed. Pure
/// in-memory state; persistence and UI live elsewhere.
///
/// GPS hygiene: jumps larger than {@link #MAX_JUMP_M} between consecutive
/// samples are treated as glitches and ignored, and sub-{@link #MIN_MOVE_M}
/// moves are treated as standstill noise so a parked car does not slowly
/// inflate the odometer.
public final class TripRecorder
{
  private static final String SUB = "trip";

  /// Distance deltas above this (metres) are discarded as GPS glitches.
  private static final double MAX_JUMP_M = 5000.0;
  /// Distance deltas below this (metres) are discarded as standstill noise.
  private static final double MIN_MOVE_M = 1.0;
  /// Speed above this (m/s, ~1.8 km/h) counts the sample's dt as "moving".
  private static final double MOVING_SPEED_MPS = 0.5;

  private boolean mRecording;
  private long mStartMs;
  private long mLastSampleMs;

  private GeoPoint mLastPoint;
  private long mLastPointTimeMs;

  private double mDistanceMeters;
  private long mMovingTimeMs;
  private double mMaxSpeedMps;

  /// Reset all state and begin recording from now.
  public void start()
  {
    mRecording = true;
    mStartMs = System.currentTimeMillis();
    mLastSampleMs = mStartMs;
    mLastPoint = null;
    mLastPointTimeMs = 0L;
    mDistanceMeters = 0.0;
    mMovingTimeMs = 0L;
    mMaxSpeedMps = 0.0;
    CairoLog.i(SUB, "trip started");
  }

  /// Feed one location sample. No-op unless recording.
  public void onLocation(double lat, double lon, double speedMps, long timeMs)
  {
    if (!mRecording)
      return;

    final GeoPoint point = new GeoPoint(lat, lon);

    if (mLastPoint != null)
    {
      final double delta = Geo.haversineMeters(mLastPoint, point);
      // Ignore GPS glitches (huge jumps) and standstill noise (tiny moves).
      if (delta <= MAX_JUMP_M && delta >= MIN_MOVE_M)
        mDistanceMeters += delta;

      final long dt = timeMs - mLastPointTimeMs;
      if (dt > 0 && speedMps > MOVING_SPEED_MPS)
        mMovingTimeMs += dt;
    }

    if (speedMps > mMaxSpeedMps)
      mMaxSpeedMps = speedMps;

    mLastPoint = point;
    mLastPointTimeMs = timeMs;
    mLastSampleMs = System.currentTimeMillis();
  }

  /// Stop recording. State is retained for read-out / summary.
  public void stop()
  {
    if (!mRecording)
      return;
    mRecording = false;
    CairoLog.i(SUB, "trip stopped: " + toSummaryString());
  }

  public boolean isRecording()
  {
    return mRecording;
  }

  public double getDistanceMeters()
  {
    return mDistanceMeters;
  }

  /// Wall-clock elapsed time. While recording, measured up to the last sample;
  /// once stopped, up to the final sample.
  public long getElapsedMs()
  {
    if (mStartMs == 0L)
      return 0L;
    final long end = mRecording ? System.currentTimeMillis() : mLastSampleMs;
    final long elapsed = end - mStartMs;
    return elapsed > 0L ? elapsed : 0L;
  }

  public long getMovingTimeMs()
  {
    return mMovingTimeMs;
  }

  public double getMaxSpeedMps()
  {
    return mMaxSpeedMps;
  }

  /// Average speed over moving time (m/s). Guards division by zero.
  public double getAvgSpeedMps()
  {
    if (mMovingTimeMs <= 0L)
      return 0.0;
    return mDistanceMeters / (mMovingTimeMs / 1000.0);
  }

  /// Human-readable one-liner, e.g. "12.4 km, 23 min, avg 32 km/h, max 78 km/h".
  @NonNull
  public String toSummaryString()
  {
    final double km = mDistanceMeters / 1000.0;
    final long minutes = Math.round(getElapsedMs() / 60000.0);
    final long avgKmh = Math.round(getAvgSpeedMps() * 3.6);
    final long maxKmh = Math.round(mMaxSpeedMps * 3.6);
    return String.format(Locale.US, "%.1f km, %d min, avg %d km/h, max %d km/h",
        km, minutes, avgKmh, maxKmh);
  }
}
