package app.organicmaps.cairodrive.cameras;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.util.Geo;
import app.organicmaps.sdk.util.log.CairoLog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/// Multi-source merge engine for speed/enforcement cameras. Queries every
/// available {@link CameraSource}, merges all results, and dedupes near-coincident
/// cameras so the user sees each physical camera once even when several datasets
/// report it.
///
/// No network endpoints of its own; it orchestrates the sources (e.g.
/// {@link OverpassCameraSource} hitting the Overpass API, and an optional
/// {@link CustomDatasetCameraSource} serving a curated list).
///
/// Dedupe rule: two cameras are considered the same physical camera when they
/// are within {@link #DEDUPE_RADIUS_M} metres of each other AND their
/// {@link CameraType}s are compatible, where {@link CameraType#UNKNOWN} is a
/// wildcard that matches any type. When duplicates are found the kept camera
/// takes the more specific (non-UNKNOWN) type and the higher known maxspeed;
/// ties prefer the source listed earlier in the constructor.
public final class CameraAggregator
{
  private static final String SUB = "cameras";

  /// Two cameras within this distance (and with compatible types) are treated as
  /// the same physical camera.
  private static final double DEDUPE_RADIUS_M = 25.0;

  @NonNull
  private final List<CameraSource> mSources;

  /// Default wiring: just the always-available Overpass source. Callers can use
  /// the other constructor to add a {@link CustomDatasetCameraSource}.
  public CameraAggregator()
  {
    final List<CameraSource> sources = new ArrayList<>();
    sources.add(new OverpassCameraSource());
    mSources = sources;
  }

  /// @param sources ordered list of sources; earlier entries win dedupe ties. A
  ///                null list is treated as empty.
  public CameraAggregator(List<CameraSource> sources)
  {
    mSources = sources == null ? new ArrayList<>() : new ArrayList<>(sources);
  }

  /// Collects and dedupes cameras within roughly {@code radiusMeters} of
  /// {@code center} across all available sources. Each source is queried
  /// independently; an IOException from one source is logged and skipped so it
  /// never aborts the overall merge. Returns an empty list (never null) when
  /// nothing is found.
  @NonNull
  public List<OverpassCamera> collect(@NonNull GeoPoint center, double radiusMeters)
  {
    final List<OverpassCamera> raw = new ArrayList<>();
    int sourcesQueried = 0;

    for (final CameraSource source : mSources)
    {
      if (source == null || !source.isAvailable())
        continue;
      sourcesQueried++;
      try
      {
        final List<OverpassCamera> got = source.cameras(center, radiusMeters);
        if (got != null)
        {
          for (final OverpassCamera cam : got)
          {
            if (cam != null)
              raw.add(cam);
          }
        }
      }
      catch (IOException e)
      {
        CairoLog.w(SUB, "cameras: source=" + source.name() + " failed: " + e.getMessage());
      }
    }

    final List<OverpassCamera> merged = dedupe(raw);
    CairoLog.i(SUB, "cameras: sources=" + sourcesQueried + " raw=" + raw.size()
        + " merged=" + merged.size());
    return merged;
  }

  /// Merges near-coincident, type-compatible cameras into one. Preserves the
  /// order in which surviving cameras were first seen (source order, then
  /// per-source order).
  @NonNull
  private static List<OverpassCamera> dedupe(@NonNull List<OverpassCamera> cameras)
  {
    final List<OverpassCamera> kept = new ArrayList<>();
    for (final OverpassCamera candidate : cameras)
    {
      boolean merged = false;
      for (int i = 0; i < kept.size(); i++)
      {
        final OverpassCamera existing = kept.get(i);
        if (sameCamera(existing, candidate))
        {
          kept.set(i, combine(existing, candidate));
          merged = true;
          break;
        }
      }
      if (!merged)
        kept.add(candidate);
    }
    return kept;
  }

  /// True when two cameras are the same physical camera: within
  /// {@link #DEDUPE_RADIUS_M} metres and with compatible types (UNKNOWN matches
  /// anything).
  private static boolean sameCamera(@NonNull OverpassCamera a, @NonNull OverpassCamera b)
  {
    if (Geo.haversineMeters(a.location, b.location) > DEDUPE_RADIUS_M)
      return false;
    return typesCompatible(a.type, b.type);
  }

  private static boolean typesCompatible(@NonNull CameraType a, @NonNull CameraType b)
  {
    return a == b || a == CameraType.UNKNOWN || b == CameraType.UNKNOWN;
  }

  /// Combines a duplicate pair: keeps the more specific (non-UNKNOWN) type and
  /// the higher known maxspeed. On ties, {@code existing} (the earlier-seen, thus
  /// earlier-source/order, camera) is preferred for identity fields (id, name,
  /// location).
  @NonNull
  private static OverpassCamera combine(@NonNull OverpassCamera existing, @NonNull OverpassCamera candidate)
  {
    // Prefer the specific type; on a tie keep existing's.
    final CameraType type = existing.type != CameraType.UNKNOWN ? existing.type
                          : candidate.type;

    // Prefer the higher known (>0) maxspeed.
    final int maxspeed = Math.max(existing.maxspeedKmh, candidate.maxspeedKmh);

    // Keep identity fields from existing (earlier source wins ties), but fall
    // back to candidate's name if existing's is blank.
    final String name = !existing.name.isEmpty() ? existing.name : candidate.name;

    return new OverpassCamera(existing.id, existing.location, type, maxspeed, name);
  }
}
