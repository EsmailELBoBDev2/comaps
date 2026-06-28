package app.organicmaps.cairodrive.routing;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.CairoKeys;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.net.HttpJson;
import app.organicmaps.sdk.util.log.CairoLog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/// HERE Routing API v8 online routing.
///
/// Endpoint: GET https://router.hereapi.com/v8/routes
///   ?transportMode=car&origin={lat},{lon}&destination={lat},{lon}
///   &return=summary,polyline&alternatives=2&apiKey=...
///
/// Each {@code routes[]} entry holds {@code sections[]}; per section we sum
/// {@code summary.length} (m) / {@code summary.duration} (s) for the route
/// totals, and decode {@code section.polyline} (HERE Flexible Polyline) into
/// the geometry via {@link HereFlexiblePolyline}. If polyline decoding fails we
/// keep the route with an empty polyline rather than dropping it or throwing.
///
/// Parsing is defensive (optDouble/optJSONArray, tolerate missing fields); the
/// schema may need live tuning against the API.
public final class HereRouter implements RouteProvider
{
  private static final String SUB = "route";
  private static final String NAME = "HERE";
  private static final String BASE = "https://router.hereapi.com/v8/routes";

  @NonNull
  @Override
  public String name()
  {
    return NAME;
  }

  @Override
  public boolean isAvailable()
  {
    return CairoKeys.hasHere();
  }

  @NonNull
  @Override
  public List<OnlineRoute> route(@NonNull GeoPoint from, @NonNull GeoPoint to) throws IOException
  {
    final String origin = String.format(Locale.US, "%f,%f", from.lat, from.lon);
    final String destination = String.format(Locale.US, "%f,%f", to.lat, to.lon);
    final String url = BASE
        + "?transportMode=car"
        + "&origin=" + origin
        + "&destination=" + destination
        + "&return=summary,polyline"
        + "&alternatives=2"
        + "&apiKey=" + CairoKeys.here();

    final JSONObject root = HttpJson.getObject(url, HttpJson.noHeaders());
    return parse(root);
  }

  @NonNull
  private static List<OnlineRoute> parse(@NonNull JSONObject root)
  {
    final List<OnlineRoute> routes = new ArrayList<>();
    final JSONArray arr = root.optJSONArray("routes");
    if (arr == null)
    {
      CairoLog.w(SUB, NAME + ": no routes array in response");
      return routes;
    }

    for (int i = 0; i < arr.length(); i++)
    {
      final JSONObject r = arr.optJSONObject(i);
      if (r == null)
        continue;

      final JSONArray sections = r.optJSONArray("sections");
      if (sections == null)
        continue;

      double distance = 0;
      double duration = 0;
      final List<GeoPoint> poly = new ArrayList<>();

      for (int j = 0; j < sections.length(); j++)
      {
        final JSONObject section = sections.optJSONObject(j);
        if (section == null)
          continue;

        final JSONObject summary = section.optJSONObject("summary");
        if (summary != null)
        {
          distance += summary.optDouble("length", 0);
          duration += summary.optDouble("duration", 0);
        }

        // Geometry is a HERE flexible polyline; tolerate decode failures.
        if (section.opt("polyline") instanceof String)
        {
          final String encoded = section.optString("polyline", null);
          if (encoded != null && !encoded.isEmpty())
          {
            try
            {
              poly.addAll(HereFlexiblePolyline.decode(encoded));
            }
            catch (RuntimeException e)
            {
              CairoLog.w(SUB, NAME + ": flexible polyline decode failed: " + e.getMessage());
            }
          }
        }
      }

      // Keep the route even with an empty polyline (e.g. decode failure).
      routes.add(new OnlineRoute(NAME, distance, duration, poly));
    }
    return routes;
  }
}
