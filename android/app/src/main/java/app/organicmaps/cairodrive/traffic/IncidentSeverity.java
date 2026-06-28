package app.organicmaps.cairodrive.traffic;

/// Severity of a traffic incident, with an ARGB display colour ranging from
/// green (none/minor) through yellow and orange to dark red (severe).
///
/// Maps TomTom's {@code magnitudeOfDelay} (0..4) onto these buckets via
/// {@link #fromTomTom(int)}.
public enum IncidentSeverity
{
  // Grey for unknown, then a green -> yellow -> orange -> red -> dark red gradient.
  UNKNOWN(0xFF9E9E9E),
  MINOR(0xFF4CAF50),
  MODERATE(0xFFFFEB3B),
  MAJOR(0xFFFF9800),
  SEVERE(0xFFB71C1C);

  private final int mColor;

  IncidentSeverity(int color)
  {
    mColor = color;
  }

  /// ARGB colour used to draw this incident on the map/overlay.
  public int color()
  {
    return mColor;
  }

  /// Maps TomTom's {@code magnitudeOfDelay} (0 = unknown, 1 = minor,
  /// 2 = moderate, 3 = major, 4 = undefined/severe) onto our buckets.
  /// Out-of-range values fall back to {@link #UNKNOWN}.
  public static IncidentSeverity fromTomTom(int magnitudeOrDelay)
  {
    switch (magnitudeOrDelay)
    {
    case 0: return UNKNOWN;
    case 1: return MINOR;
    case 2: return MODERATE;
    case 3: return MAJOR;
    case 4: return SEVERE;
    default: return UNKNOWN;
    }
  }
}
