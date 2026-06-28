package app.organicmaps.cairodrive.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.net.HttpJson;
import app.organicmaps.sdk.util.log.CairoLog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/// Auto-failover online place search.
///
/// There is no provider chip in the UI: results "appear automatically without
/// selecting a provider". To achieve that, the manager walks its providers in a
/// fixed PRIORITY order [Google, HERE, Geoapify, LocationIQ, TomTom], skipping
/// any whose key is absent, and queries the FIRST available provider. If that
/// provider throws or returns no results, it FALLS BACK to the next available
/// one, and so on, returning the first non-empty result set.
///
/// All providers are OPT-IN; with no keys configured this simply returns an
/// empty list and the offline-first app is unaffected.
public final class OnlineSearchManager
{
  private static final String SUB = "search";

  @NonNull
  private final List<SearchProvider> mProviders;

  /// Wires the default provider order [Google, HERE, Geoapify, LocationIQ, TomTom].
  public OnlineSearchManager()
  {
    this(Arrays.asList(
        new GooglePlacesProvider(),
        new HereProvider(),
        new GeoapifyProvider(),
        new LocationIqProvider(),
        new TomTomSearchProvider()));
  }

  /// Explicit-providers constructor for testability.
  public OnlineSearchManager(@NonNull List<SearchProvider> providers)
  {
    mProviders = new ArrayList<>(providers);
  }

  /// Returns the first non-empty result set from the available providers, in
  /// priority order. Per-provider failures are logged (scrubbed) and skipped.
  @NonNull
  public List<OnlinePlace> search(@NonNull String query, @Nullable GeoPoint near)
  {
    if (query.trim().isEmpty())
      return Collections.emptyList();

    for (SearchProvider provider : mProviders)
    {
      if (!provider.isAvailable())
        continue;

      try
      {
        final List<OnlinePlace> results = provider.search(query, near);
        final int n = results != null ? results.size() : 0;
        CairoLog.i(SUB, "search: provider=" + provider.name() + " results=" + n);
        if (n > 0)
          return results;
      }
      catch (IOException e)
      {
        // Scrubbed one-liner; fall back to the next available provider.
        CairoLog.w(SUB, "search: provider=" + provider.name() + " failed: "
            + HttpJson.scrubUrl(String.valueOf(e.getMessage())));
      }
      catch (RuntimeException e)
      {
        // Defensive: a malformed response can surface as a JSON RuntimeException.
        CairoLog.w(SUB, "search: provider=" + provider.name() + " error: "
            + HttpJson.scrubUrl(String.valueOf(e.getMessage())));
      }
    }
    return Collections.emptyList();
  }
}
