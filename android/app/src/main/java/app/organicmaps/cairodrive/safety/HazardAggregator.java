package app.organicmaps.cairodrive.safety;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.util.Geo;
import app.organicmaps.sdk.util.log.CairoLog;
import java.util.ArrayList;
import java.util.List;

/// Collects road safety hazards from the Overpass API and dedupes near-coincident
/// entries so the user sees each physical hazard once even when several OSM
/// elements (e.g. a node and an enclosing area) describe it.
///
/// Dedupe rule: two hazards are considered the same when they are within
/// {@link #DEDUPE_RADIUS_M} metres of each other AND share the same
/// {@link HazardType}. When duplicates are found the earlier-seen one is kept.
public final class HazardAggregator
{
  private static final String SUB = "hazards";

  /// Two hazards within this distance (and of the same type) are treated as the
  /// same physical hazard.
  private static final double DEDUPE_RADIUS_M = 25.0;

  @NonNull
  private final OverpassHazardClient mClient;

  /// Holds pre-fetched hazards for the testing constructor; null in normal
  /// (network-backed) operation.
  private final List<Hazard> mInjected;

  /// Default wiring: a network-backed {@link OverpassHazardClient}.
  public HazardAggregator()
  {
    mClient = new OverpassHazardClient();
    mInjected = null;
  }

  /// Testing constructor: supplies a fixed hazard list, bypassing the network.
  /// The {@code collect} call dedupes this list and ignores its center/radius
  /// arguments. A null list is treated as empty.
  public HazardAggregator(List<Hazard> hazards)
  {
    mClient = new OverpassHazardClient();
    mInjected = hazards == null ? new ArrayList<>() : new ArrayList<>(hazards);
  }

  /// Collects and dedupes hazards within roughly {@code radiusMeters} of
  /// {@code center}. Returns an empty list (never null) when nothing is found.
  @NonNull
  public List<Hazard> collect(@NonNull GeoPoint center, double radiusMeters)
  {
    final List<Hazard> raw = mInjected != null
        ? new ArrayList<>(mInjected)
        : mClient.fetchAround(center, radiusMeters);

    final List<Hazard> merged = dedupe(raw);
    CairoLog.i(SUB, "hazards: raw=" + raw.size() + " merged=" + merged.size());
    return merged;
  }

  /// Merges near-coincident, same-type hazards into one. Preserves the order in
  /// which surviving hazards were first seen.
  @NonNull
  private static List<Hazard> dedupe(@NonNull List<Hazard> hazards)
  {
    final List<Hazard> kept = new ArrayList<>();
    for (final Hazard candidate : hazards)
    {
      if (candidate == null)
        continue;
      boolean duplicate = false;
      for (final Hazard existing : kept)
      {
        if (sameHazard(existing, candidate))
        {
          duplicate = true;
          break;
        }
      }
      if (!duplicate)
        kept.add(candidate);
    }
    return kept;
  }

  /// True when two hazards are the same physical hazard: within
  /// {@link #DEDUPE_RADIUS_M} metres and of the same {@link HazardType}.
  private static boolean sameHazard(@NonNull Hazard a, @NonNull Hazard b)
  {
    if (a.type != b.type)
      return false;
    return Geo.haversineMeters(a.location, b.location) < DEDUPE_RADIUS_M;
  }
}
