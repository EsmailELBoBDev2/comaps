package app.organicmaps.cairodrive.overlay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.cairodrive.CairoConfig;
import app.organicmaps.cairodrive.cameras.CameraAggregator;
import app.organicmaps.cairodrive.cameras.CameraTileTracker;
import app.organicmaps.cairodrive.cameras.OverpassCamera;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.reports.CairoReport;
import app.organicmaps.cairodrive.reports.CairoReportStore;
import app.organicmaps.cairodrive.routing.OnlineRoute;
import app.organicmaps.cairodrive.routing.RouteCameraRanker;
import app.organicmaps.cairodrive.routing.RouteCompareManager;
import app.organicmaps.cairodrive.mapillary.MapillaryClient;
import app.organicmaps.cairodrive.mapillary.MapillaryImage;
import app.organicmaps.cairodrive.safety.Hazard;
import app.organicmaps.cairodrive.safety.HazardAggregator;
import app.organicmaps.cairodrive.search.OnlinePlace;
import app.organicmaps.cairodrive.search.OnlineSearchManager;
import app.organicmaps.cairodrive.traffic.TrafficAggregator;
import app.organicmaps.cairodrive.trip.ParkingStore;
import app.organicmaps.sdk.editor.Editor;
import app.organicmaps.cairodrive.traffic.TrafficIncident;
import app.organicmaps.sdk.util.log.CairoLog;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/// Coordinates fetching online cameras + traffic (off the UI thread) and
/// rendering them through {@link CairoMapOverlay} (on the UI thread). Honours
/// the CairoConfig online toggle: when off, it just clears the overlay so the
/// app stays fully offline.
public final class CairoOverlayController
{
  private static final String SUB = "overlay";
  private static final double FETCH_RADIUS_M = 10_000;  // ~10 km, matches the spec's pan-to-load box.
  // ~0.09 deg latitude ~= 10 km; longitude scaled by cos(lat) at use time.
  private static final double BBOX_HALF_DEG = 0.09;
  // Don't re-hit the network more than once a minute even if the user moved.
  private static final long MIN_REFETCH_INTERVAL_MS = 60_000;

  private final CairoMapOverlay mOverlay = new CairoMapOverlay();
  // Built lazily with the app context on first use (always from the single IO
  // thread, so no synchronisation needed). Wires Overpass + remote dataset +
  // community reports; see CameraAggregator.createDefault.
  @Nullable
  private CameraAggregator mCameras;
  private final TrafficAggregator mTraffic = new TrafficAggregator();
  private final HazardAggregator mHazards = new HazardAggregator();
  private final RouteCompareManager mRouter = new RouteCompareManager();
  private final OnlineSearchManager mSearch = new OnlineSearchManager();
  private final MapillaryClient mMapillary = new MapillaryClient();
  // Pan-to-load gate: only refetch when the map centre moved > ~2 km.
  private final CameraTileTracker mTracker = new CameraTileTracker();
  private long mLastFetchMs = 0;
  // Force one fetch on first run and after online is (re)enabled, bypassing the gate.
  private boolean mForceFetch = true;
  private final ExecutorService mIo = Executors.newSingleThreadExecutor();
  private final Handler mUi = new Handler(Looper.getMainLooper());

  public interface BadgeListener
  {
    void onCameraCount(int count);
  }

  /// Fetch around (lat,lon) and repaint the overlay. No-op clear when online
  /// features are disabled. Safe to call from the UI thread.
  public void refresh(@NonNull Context ctx, double lat, double lon, @Nullable BadgeListener badge)
  {
    // Community reports + parking are local: always shown, even fully offline.
    final List<CairoReport> reports = CairoReportStore.active(ctx, System.currentTimeMillis());
    final boolean hasPark = ParkingStore.hasParking(ctx);
    final double pLat = hasPark ? ParkingStore.getParkingLat(ctx) : 0;
    final double pLon = hasPark ? ParkingStore.getParkingLon(ctx) : 0;

    if (!CairoConfig.isOnlineEnabled(ctx))
    {
      mForceFetch = true;  // re-enabling online should refetch immediately
      mUi.post(() -> {
        mOverlay.render(new ArrayList<>(), new ArrayList<>());  // clears any online marks
        mOverlay.showReports(reports);
        mOverlay.showParking(hasPark, pLat, pLon);
        if (badge != null)
          badge.onCameraCount(0);
      });
      return;
    }

    // Pan-to-load + min-interval gate: skip the network fetch when we haven't
    // moved > ~2 km and fetched recently. Existing online marks stay; only the
    // local report/parking layers are repainted.
    final GeoPoint center = new GeoPoint(lat, lon);
    final long now = System.currentTimeMillis();
    final boolean doNetwork =
        mForceFetch || (mTracker.needsRefetch(center) && (now - mLastFetchMs >= MIN_REFETCH_INTERVAL_MS));
    if (!doNetwork)
    {
      mUi.post(() -> {
        mOverlay.showReports(reports);
        mOverlay.showParking(hasPark, pLat, pLon);
      });
      return;
    }
    mForceFetch = false;
    mTracker.markFetched(center);
    mLastFetchMs = now;

    mIo.execute(() -> {
      List<OverpassCamera> cams = new ArrayList<>();
      List<TrafficIncident> incidents = new ArrayList<>();
      List<Hazard> hazards = new ArrayList<>();
      try
      {
        cams = cameras(ctx).collect(new GeoPoint(lat, lon), FETCH_RADIUS_M);
      }
      catch (Throwable t)
      {
        CairoLog.w(SUB, "camera fetch failed: " + t.getMessage());
      }
      try
      {
        hazards = mHazards.collect(new GeoPoint(lat, lon), FETCH_RADIUS_M);
      }
      catch (Throwable t)
      {
        CairoLog.w(SUB, "hazard fetch failed: " + t.getMessage());
      }
      try
      {
        final double lonHalf = BBOX_HALF_DEG / Math.max(0.1, Math.cos(Math.toRadians(lat)));
        incidents = mTraffic.collect(lon - lonHalf, lat - BBOX_HALF_DEG, lon + lonHalf, lat + BBOX_HALF_DEG);
      }
      catch (Throwable t)
      {
        CairoLog.w(SUB, "traffic fetch failed: " + t.getMessage());
      }

      final List<OverpassCamera> fcams = cams;
      final List<TrafficIncident> fincidents = incidents;
      final List<Hazard> fhazards = hazards;
      mUi.post(() -> {
        final int count = mOverlay.render(fcams, fincidents);
        mOverlay.showHazards(fhazards);
        mOverlay.showReports(reports);
        mOverlay.showParking(hasPark, pLat, pLon);
        if (badge != null)
          badge.onCameraCount(count);
      });
    });
  }

  /// Lazily build the multi-source camera aggregator with the app context.
  /// Only ever called from the single IO thread, so no locking is needed.
  @NonNull
  private CameraAggregator cameras(@NonNull Context ctx)
  {
    if (mCameras == null)
      mCameras = CameraAggregator.createDefault(ctx.getApplicationContext());
    return mCameras;
  }

  /// Save the current location as "where I parked" and drop a mark.
  public void park(@NonNull Context ctx, double lat, double lon)
  {
    ParkingStore.saveParking(ctx, lat, lon);
    mUi.post(() -> mOverlay.showParking(true, lat, lon));
  }

  /// Clear the saved parking spot.
  public void unpark(@NonNull Context ctx)
  {
    ParkingStore.clearParking(ctx);
    mUi.post(() -> mOverlay.showParking(false, 0, 0));
  }

  /// Add a one-tap community report at (lat,lon) and repaint the report marks.
  /// Works offline (stored locally). Permanent, mappable kinds (camera, speed
  /// bump, pothole) ALSO create a public OSM Note so the report reaches the
  /// shared OpenStreetMap map directly -- not a private database. Transient
  /// kinds (mobile radar, police, hazard) stay local, since OSM doesn't map them.
  public void report(@NonNull Context ctx, @NonNull CairoReport.Kind kind, double lat, double lon)
  {
    final long now = System.currentTimeMillis();
    CairoReportStore.add(ctx, new CairoReport(kind, lat, lon, now), now);
    final List<CairoReport> reports = CairoReportStore.active(ctx, now);
    mUi.post(() -> mOverlay.showReports(reports));

    if (kind.submitsToOsm)
    {
      try
      {
        Editor.nativeCreateStandaloneNote(lat, lon, "CairoDrive: " + kind.label + " reported here.");
        CairoLog.i(SUB, "OSM note queued for " + kind.name());
      }
      catch (Throwable t)
      {
        CairoLog.w(SUB, "OSM note failed: " + t.getMessage());
      }
    }
  }

  /// Run the multi-provider route comparison from->to (off the UI thread) and
  /// draw the resulting polylines (fastest green, alternatives blue) on the map.
  /// No-op when online features are disabled.
  public void showRouteCompare(@NonNull Context ctx, @NonNull GeoPoint from, @NonNull GeoPoint to)
  {
    if (!CairoConfig.isOnlineEnabled(ctx))
      return;
    mIo.execute(() -> {
      List<OnlineRoute> routes;
      try
      {
        routes = mRouter.compare(from, to, CairoConfig.getPreferredRouter(ctx));
      }
      catch (Throwable t)
      {
        CairoLog.w(SUB, "route compare failed: " + t.getMessage());
        return;
      }
      // "Fewer cameras" ranking: count cameras near each alternative.
      try
      {
        final GeoPoint mid = new GeoPoint((from.lat + to.lat) / 2, (from.lon + to.lon) / 2);
        RouteCameraRanker.annotateFewest(routes, cameras(ctx).collect(mid, FETCH_RADIUS_M));
      }
      catch (Throwable t)
      {
        CairoLog.w(SUB, "camera ranking failed: " + t.getMessage());
      }
      final List<OnlineRoute> fr = routes;
      mUi.post(() -> mOverlay.showRoutes(fr));
    });
  }

  /// Called when the user taps a map track; if it's one of our route lines,
  /// make it the active (green) route.
  public void onTrackTapped(long trackId)
  {
    mUi.post(() -> mOverlay.setActiveRoute(trackId));
  }

  public interface UrlSink
  {
    void onUrl(@Nullable String url);
  }

  /// Run the aggregated online place search (off the UI thread) and drop the
  /// results as purple marks on the map. Requires the online toggle + keys.
  public void onlineSearch(@NonNull Context ctx, @NonNull String query, @Nullable GeoPoint near)
  {
    if (!CairoConfig.isOnlineEnabled(ctx))
      return;
    mIo.execute(() -> {
      List<OnlinePlace> places;
      try
      {
        places = mSearch.search(query, near);
      }
      catch (Throwable t)
      {
        CairoLog.w(SUB, "online search failed: " + t.getMessage());
        return;
      }
      final List<OnlinePlace> fp = places;
      mUi.post(() -> {
        mOverlay.showSearchResults(fp);
        // Visible feedback independent of the mark layer: toast the count and fly
        // the map to the first hit (online coords land on the OSM map even when
        // the place isn't in the offline OSM data).
        android.widget.Toast
            .makeText(ctx, fp.isEmpty() ? "No results" : ("Found " + fp.size() + " place(s)"),
                      android.widget.Toast.LENGTH_SHORT)
            .show();
        if (!fp.isEmpty())
        {
          final OnlinePlace first = fp.get(0);
          app.organicmaps.sdk.Framework.nativeSetViewportCenter(first.location.lat, first.location.lon, 16);
        }
      });
    });
  }

  /// Fetch the nearest Mapillary street-level image and hand its URL to the sink
  /// (on the UI thread), or null if none/unavailable.
  public void mapillaryHere(@NonNull GeoPoint center, @NonNull UrlSink sink)
  {
    mIo.execute(() -> {
      String url = null;
      try
      {
        final List<MapillaryImage> images = mMapillary.imagesNear(center, 150);
        if (!images.isEmpty())
          url = images.get(0).thumbUrl;
      }
      catch (Throwable t)
      {
        CairoLog.w(SUB, "mapillary fetch failed: " + t.getMessage());
      }
      final String furl = url;
      mUi.post(() -> sink.onUrl(furl));
    });
  }

  public void clear()
  {
    mUi.post(mOverlay::clear);
  }

  /// Release the background executor and drop pending UI callbacks. Call from
  /// the host activity's onDestroy to avoid leaking the worker thread.
  public void shutdown()
  {
    mIo.shutdownNow();
    mUi.removeCallbacksAndMessages(null);
  }
}

