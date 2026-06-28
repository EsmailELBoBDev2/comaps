package app.organicmaps.cairodrive.traffic;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.net.HttpJson;
import app.organicmaps.cairodrive.util.Geo;
import app.organicmaps.sdk.util.log.CairoLog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// Multi-source traffic merge engine.
///
/// Queries every AVAILABLE {@link TrafficSource} for a bounding box, merges all
/// incidents into one list, then DEDUPES near-duplicates that different
/// providers report for the same event: two incidents are considered the same
/// when their locations are within {@link #DEDUPE_RADIUS_M} metres AND they
/// share a category (or, when category is missing, a severity). The kept
/// incident is the one with the higher severity; ties keep the one seen first
/// (i.e. the earlier source in priority order).
///
/// All sources are OPT-IN; with no keys configured this returns an empty list
/// and the offline-first app is unaffected.
public final class TrafficAggregator
{
  private static final String SUB = "traffic";

  /// Incidents closer than this (metres) are candidates for dedupe.
  private static final double DEDUPE_RADIUS_M = 50.0;

  @NonNull
  private final List<TrafficSource> mSources;

  /// Wires the default source order [TomTom, HERE].
  public TrafficAggregator()
  {
    this(Arrays.asList(new TomTomTrafficSource(), new HereTrafficSource()));
  }

  /// Explicit-sources constructor for testability.
  public TrafficAggregator(@NonNull List<TrafficSource> sources)
  {
    mSources = new ArrayList<>(sources);
  }

  /// Collects and dedupes incidents across all available sources for the given
  /// bbox ({@code minLon,minLat,maxLon,maxLat}). Per-source failures are logged
  /// (scrubbed) and skipped so one bad provider never blocks the others.
  @NonNull
  public List<TrafficIncident> collect(double minLon, double minLat, double maxLon, double maxLat)
  {
    final List<TrafficIncident> raw = new ArrayList<>();
    int queried = 0;

    for (TrafficSource source : mSources)
    {
      if (!source.isAvailable())
        continue;

      queried++;
      try
      {
        final List<TrafficIncident> incidents = source.fetchInBbox(minLon, minLat, maxLon, maxLat);
        if (incidents != null)
          raw.addAll(incidents);
      }
      catch (IOException e)
      {
        CairoLog.w(SUB, "traffic: source=" + source.name() + " failed: "
            + HttpJson.scrubUrl(String.valueOf(e.getMessage())));
      }
      catch (RuntimeException e)
      {
        // Defensive: a malformed response can surface as a JSON RuntimeException.
        CairoLog.w(SUB, "traffic: source=" + source.name() + " error: "
            + HttpJson.scrubUrl(String.valueOf(e.getMessage())));
      }
    }

    final List<TrafficIncident> merged = dedupe(raw);
    CairoLog.i(SUB, "traffic: sources=" + queried + " raw=" + raw.size() + " merged=" + merged.size());
    return merged;
  }

  /// Collapses near-duplicate incidents. For each raw incident, if an already
  /// kept incident is a duplicate ({@link #sameIncident}) we keep whichever has
  /// the higher severity (ties keep the earlier one); otherwise it is added.
  @NonNull
  private static List<TrafficIncident> dedupe(@NonNull List<TrafficIncident> raw)
  {
    final List<TrafficIncident> kept = new ArrayList<>();
    for (TrafficIncident incident : raw)
    {
      int dupIndex = -1;
      for (int i = 0; i < kept.size(); i++)
      {
        if (sameIncident(kept.get(i), incident))
        {
          dupIndex = i;
          break;
        }
      }

      if (dupIndex < 0)
      {
        kept.add(incident);
      }
      else if (incident.severity.ordinal() > kept.get(dupIndex).severity.ordinal())
      {
        // Strictly higher severity wins; ties keep the earlier (existing) one.
        kept.set(dupIndex, incident);
      }
    }
    return kept;
  }

  /// True when two incidents are the same event: within
  /// {@link #DEDUPE_RADIUS_M} metres AND sharing a category (or, when either
  /// category is missing, sharing a severity).
  private static boolean sameIncident(@NonNull TrafficIncident a, @NonNull TrafficIncident b)
  {
    if (Geo.haversineMeters(a.location, b.location) >= DEDUPE_RADIUS_M)
      return false;

    final boolean haveCategories = !a.category.isEmpty() && !b.category.isEmpty();
    if (haveCategories)
      return a.category.equals(b.category);

    return a.severity == b.severity;
  }
}
