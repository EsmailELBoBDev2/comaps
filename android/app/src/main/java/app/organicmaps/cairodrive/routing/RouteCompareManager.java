package app.organicmaps.cairodrive.routing;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.sdk.util.log.CairoLog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// The Waze-style route comparer.
///
/// Queries every available {@link RouteProvider}, tolerating per-provider
/// failures (a single network error must not sink the others), merges the
/// results, drops near-duplicate routes, and ranks what remains by marking the
/// fastest (min duration) and shortest (min distance) alternatives.
public final class RouteCompareManager
{
  private static final String SUB = "route";

  /// Number of evenly-spaced points sampled from each polyline for the
  /// duplicate-overlap heuristic.
  private static final int SAMPLE_COUNT = 24;

  /// Rounding applied to sampled lat/lon (3 decimals ~= 110m grid) so that
  /// routes following the same roads bucket into the same cells.
  private static final int SAMPLE_DECIMALS = 3;

  /// Fraction of shared sampled cells above which two routes are considered
  /// duplicates.
  private static final double DUPLICATE_OVERLAP = 0.70;

  @NonNull
  private final List<RouteProvider> mProviders;

  /// Default wiring: Magic Lane + TomTom + Mapbox.
  public RouteCompareManager()
  {
    this(Arrays.asList(new MagicLaneRouter(), new TomTomRouter(), new MapboxRouter()));
  }

  /// Testable constructor accepting an explicit provider list.
  public RouteCompareManager(@NonNull List<RouteProvider> providers)
  {
    mProviders = new ArrayList<>(providers);
  }

  /// Queries all available providers, dedupes, ranks, and returns routes
  /// sorted fastest-first.
  @NonNull
  public List<OnlineRoute> compare(@NonNull GeoPoint from, @NonNull GeoPoint to)
  {
    final List<OnlineRoute> collected = new ArrayList<>();
    int queried = 0;

    for (final RouteProvider provider : mProviders)
    {
      if (!provider.isAvailable())
        continue;
      queried++;
      try
      {
        final List<OnlineRoute> routes = provider.route(from, to);
        if (routes != null)
          collected.addAll(routes);
      }
      catch (IOException e)
      {
        // Tolerate one provider failing; keep querying the rest.
        CairoLog.w(SUB, "provider " + provider.name() + " failed: " + e.getMessage());
      }
      catch (RuntimeException e)
      {
        CairoLog.e(SUB, "provider " + provider.name() + " errored", e);
      }
    }

    final List<OnlineRoute> deduped = dedupe(collected);
    rank(deduped);
    deduped.sort(Comparator.comparingDouble(r -> r.durationSeconds));

    CairoLog.i(SUB, "compare: " + queried + " providers, " + collected.size()
        + " routes, " + deduped.size() + " after dedupe");
    return deduped;
  }

  /// Drops routes whose sampled geometry overlaps an already-kept route by
  /// more than {@link #DUPLICATE_OVERLAP}.
  @NonNull
  private static List<OnlineRoute> dedupe(@NonNull List<OnlineRoute> routes)
  {
    final List<OnlineRoute> kept = new ArrayList<>();
    final List<Set<String>> keptCells = new ArrayList<>();

    for (final OnlineRoute candidate : routes)
    {
      final Set<String> cells = sampleCells(candidate);
      boolean duplicate = false;
      for (final Set<String> existing : keptCells)
      {
        if (overlap(cells, existing) >= DUPLICATE_OVERLAP)
        {
          duplicate = true;
          break;
        }
      }
      if (!duplicate)
      {
        kept.add(candidate);
        keptCells.add(cells);
      }
    }
    return kept;
  }

  /// Samples up to SAMPLE_COUNT evenly-spaced points from the polyline and
  /// rounds them into grid cells used for overlap comparison.
  @NonNull
  private static Set<String> sampleCells(@NonNull OnlineRoute route)
  {
    final Set<String> cells = new HashSet<>();
    final List<GeoPoint> poly = route.polyline;
    final int n = poly.size();
    if (n == 0)
      return cells;

    final int samples = Math.min(SAMPLE_COUNT, n);
    for (int i = 0; i < samples; i++)
    {
      final int idx = samples == 1 ? 0 : (int) ((long) i * (n - 1) / (samples - 1));
      cells.add(cell(poly.get(idx)));
    }
    return cells;
  }

  @NonNull
  private static String cell(@NonNull GeoPoint p)
  {
    return round(p.lat) + "," + round(p.lon);
  }

  private static double round(double v)
  {
    final double f = Math.pow(10, SAMPLE_DECIMALS);
    return Math.round(v * f) / f;
  }

  /// Jaccard-style overlap: shared cells over the smaller cell set, so a short
  /// route fully contained in a longer one still counts as a duplicate.
  private static double overlap(@NonNull Set<String> a, @NonNull Set<String> b)
  {
    if (a.isEmpty() || b.isEmpty())
      return 0;
    int shared = 0;
    final Set<String> smaller = a.size() <= b.size() ? a : b;
    final Set<String> larger = smaller == a ? b : a;
    for (final String c : smaller)
      if (larger.contains(c))
        shared++;
    return (double) shared / smaller.size();
  }

  /// Marks the min-duration route fastest and the min-distance route shortest.
  private static void rank(@NonNull List<OnlineRoute> routes)
  {
    OnlineRoute fastest = null;
    OnlineRoute shortest = null;
    for (final OnlineRoute r : routes)
    {
      r.isFastest = false;
      r.isShortest = false;
      if (fastest == null || r.durationSeconds < fastest.durationSeconds)
        fastest = r;
      if (shortest == null || r.distanceMeters < shortest.distanceMeters)
        shortest = r;
    }
    if (fastest != null)
      fastest.isFastest = true;
    if (shortest != null)
      shortest.isShortest = true;
  }
}
