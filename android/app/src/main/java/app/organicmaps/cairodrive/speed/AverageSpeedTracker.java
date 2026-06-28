package app.organicmaps.cairodrive.speed;

import app.organicmaps.sdk.util.log.CairoLog;

/// Tracks the running average speed across an Egyptian average-speed enforcement
/// zone (محور): cameras at both ends measure the time you take over a fixed
/// section, so what matters is your AVERAGE over the whole section, not your
/// instantaneous speed. This integrates distance and elapsed time from GPS
/// samples while inside a zone and compares the average against the limit.
public final class AverageSpeedTracker
{
  private static final String SUB = "AverageSpeedTracker";
  private static final double MPS_TO_KMH = 3.6;

  private boolean mInZone = false;
  private int mLimitKmh = 0;
  private double mDistanceMeters = 0.0;
  private double mTimeSeconds = 0.0;
  private long mLastSampleMs = 0L;
  private boolean mHasLastSample = false;

  /// Starts a new section with the posted average-speed limit. Resets any
  /// accumulated distance/time from a previous zone.
  public void enterZone(int limitKmh)
  {
    mInZone = true;
    mLimitKmh = Math.max(0, limitKmh);
    mDistanceMeters = 0.0;
    mTimeSeconds = 0.0;
    mLastSampleMs = 0L;
    mHasLastSample = false;
    CairoLog.i(SUB, "enter zone, limit " + mLimitKmh + " km/h");
  }

  /// Integrates one GPS sample into the section average. {@code timeMs} is an
  /// absolute timestamp (e.g. {@code System.currentTimeMillis()} or the fix
  /// time); the delta against the previous sample is used. The first sample in
  /// a zone only establishes the baseline timestamp. Non-positive deltas and
  /// bad speeds are ignored so a duplicate or out-of-order fix cannot corrupt
  /// the average.
  public void onSample(double speedMps, long timeMs)
  {
    if (!mInZone)
      return;

    if (Double.isNaN(speedMps) || speedMps < 0.0)
      speedMps = 0.0;

    if (!mHasLastSample)
    {
      mHasLastSample = true;
      mLastSampleMs = timeMs;
      return;
    }

    final long dtMs = timeMs - mLastSampleMs;
    if (dtMs <= 0L)
      return;

    final double dtSeconds = dtMs / 1000.0;
    mDistanceMeters += speedMps * dtSeconds;
    mTimeSeconds += dtSeconds;
    mLastSampleMs = timeMs;
  }

  /// Average speed over the section so far, in km/h. Returns 0 before any
  /// elapsed time has accumulated.
  public double getAverageKmh()
  {
    if (mTimeSeconds <= 0.0)
      return 0.0;
    return (mDistanceMeters / mTimeSeconds) * MPS_TO_KMH;
  }

  /// True when the running average has exceeded the zone limit.
  public boolean isOverAverage()
  {
    return mInZone && mLimitKmh > 0 && getAverageKmh() > mLimitKmh;
  }

  public int getLimitKmh()
  {
    return mLimitKmh;
  }

  public boolean inZone()
  {
    return mInZone;
  }

  /// Ends the current section. The average remains readable until the next
  /// {@link #enterZone(int)}.
  public void exitZone()
  {
    if (mInZone)
      CairoLog.i(SUB, "exit zone, avg " + Math.round(getAverageKmh()) + " km/h, limit " + mLimitKmh);
    mInZone = false;
    mHasLastSample = false;
  }
}
