package app.organicmaps.cairodrive.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.net.HttpJson;
import app.organicmaps.cairodrive.util.Geo;
import app.organicmaps.sdk.util.log.CairoLog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/// Multi-source online place search with MERGE + DEDUPE aggregation.
///
/// Previously this manager did auto-FAILOVER: it walked providers in priority
/// order and returned the FIRST non-empty result set. Per product requirement,
/// that is now REPLACED by AGGREGATION -- every available provider is queried
/// (Google, HERE, Geoapify, LocationIQ, TomTom, Mapbox, OpenRouteService), all
/// results are pooled, then near-duplicate places (same spot reported by more
/// than one backend) are collapsed to a single entry. This yields the
/// Google-Maps-style "results just appear" behaviour without a provider chip.
///
/// Dedupe rule: two {@link OnlinePlace} are the same place when
/// {@code Geo.haversineMeters(a.location, b.location) < DEDUPE_RADIUS_M} AND
/// {@code Geo.namesMatch(a.name, b.name)}. When duplicates are found we keep the
/// one from the higher-priority provider (see {@link #PRIORITY}).
///
/// All providers are OPT-IN; with no keys configured this returns an empty list
/// and the offline-first app is unaffected. Per-provider failures are caught,
/// logged as a scrubbed one-liner, and skipped so one bad backend can't sink the
/// whole search.
public final class OnlineSearchManager
{
  private static final String SUB = "search";

  /// Two results within this distance AND with matching names are merged.
  private static final double DEDUPE_RADIUS_M = 60.0;

  /// Hard cap on the returned, merged list to keep the UI bounded.
  private static final int MAX_RESULTS = 30;

  /// Provider priority, highest first. Used both to pick the survivor when
  /// deduping and to give the returned list a stable order. Lower index = higher
  /// priority. Unknown providers sort after all known ones.
  private static final List<String> PRIORITY = Arrays.asList(
      "Google", "HERE", "Geoapify", "Mapbox", "OpenRouteService", "LocationIQ", "TomTom");

  @NonNull
  private final List<SearchProvider> mProviders;

  /// Wires ALL providers. Construction order is irrelevant to results -- the
  /// merge/sort uses {@link #PRIORITY} -- but is kept in priority order for
  /// readability.
  public OnlineSearchManager()
  {
    this(Arrays.asList(
        new GooglePlacesProvider(),
        new HereProvider(),
        new GeoapifyProvider(),
        new MapboxGeocoder(),
        new OpenRouteServiceGeocoder(),
        new LocationIqProvider(),
        new TomTomSearchProvider()));
  }

  /// Explicit-providers constructor for testability.
  public OnlineSearchManager(@NonNull List<SearchProvider> providers)
  {
    mProviders = new ArrayList<>(providers);
  }

  /// Queries EVERY available provider, pools their results, then merges and
  /// dedupes near-identical places (see class doc). Returns the merged list in a
  /// stable order (provider priority, then name), capped at {@link #MAX_RESULTS}.
  ///
  /// This replaces the old first-non-empty failover with aggregation.
  @NonNull
  public List<OnlinePlace> search(@NonNull String query, @Nullable GeoPoint near)
  {
    if (query.trim().isEmpty())
      return Collections.emptyList();

    int availableCount = 0;
    final List<OnlinePlace> raw = new ArrayList<>();

    for (SearchProvider provider : mProviders)
    {
      if (!provider.isAvailable())
        continue;

      availableCount++;
      try
      {
        final List<OnlinePlace> results = provider.search(query, near);
        if (results != null)
          raw.addAll(results);
      }
      catch (Exception e)
      {
        // Any failure (IOException, JSON RuntimeException, ...) is contained:
        // scrubbed one-liner, then carry on with the remaining providers.
        CairoLog.w(SUB, "search: provider=" + provider.name() + " failed: "
            + HttpJson.scrubUrl(String.valueOf(e.getMessage())));
      }
    }

    final List<OnlinePlace> merged = mergeAndDedupe(raw);
    CairoLog.i(SUB, "search: providers=" + availableCount
        + " raw=" + raw.size() + " merged=" + merged.size());
    return merged;
  }

  /// Google-Maps-style "nearby" search for a category keyword (e.g. "fuel",
  /// "pharmacy", "restaurant"): runs the same multi-source aggregation as
  /// {@link #search}. Kept as a distinct entry point so callers can express
  /// intent; it simply delegates.
  @NonNull
  public List<OnlinePlace> searchNearbyCategory(@NonNull String category, @NonNull GeoPoint near)
  {
    return search(category, near);
  }

  /// Collapses near-duplicate places across providers, keeping the
  /// higher-priority survivor, then returns a stably-sorted, capped list.
  @NonNull
  private List<OnlinePlace> mergeAndDedupe(@NonNull List<OnlinePlace> raw)
  {
    final List<OnlinePlace> kept = new ArrayList<>();

    for (OnlinePlace candidate : raw)
    {
      if (candidate == null)
        continue;

      int dupIndex = -1;
      for (int i = 0; i < kept.size(); i++)
      {
        if (isSamePlace(candidate, kept.get(i)))
        {
          dupIndex = i;
          break;
        }
      }

      if (dupIndex < 0)
      {
        kept.add(candidate);
      }
      else if (priorityOf(candidate.provider) < priorityOf(kept.get(dupIndex).provider))
      {
        // Candidate comes from a higher-priority provider: it wins the slot.
        kept.set(dupIndex, candidate);
      }
      // else: existing survivor is equal/higher priority -> drop the candidate.
    }

    kept.sort(new Comparator<OnlinePlace>()
    {
      @Override
      public int compare(OnlinePlace a, OnlinePlace b)
      {
        final int byPriority = Integer.compare(priorityOf(a.provider), priorityOf(b.provider));
        if (byPriority != 0)
          return byPriority;
        return Geo.normalizeName(a.name).compareTo(Geo.normalizeName(b.name));
      }
    });

    if (kept.size() > MAX_RESULTS)
      return new ArrayList<>(kept.subList(0, MAX_RESULTS));
    return kept;
  }

  /// True when two results denote the same physical place: close in distance AND
  /// fuzzily matching names.
  private static boolean isSamePlace(@NonNull OnlinePlace a, @NonNull OnlinePlace b)
  {
    return Geo.haversineMeters(a.location, b.location) < DEDUPE_RADIUS_M
        && Geo.namesMatch(a.name, b.name);
  }

  /// Index of {@code provider} in {@link #PRIORITY}; unknown providers sort last.
  private static int priorityOf(@Nullable String provider)
  {
    final int idx = provider == null ? -1 : PRIORITY.indexOf(provider);
    return idx < 0 ? PRIORITY.size() : idx;
  }
}
