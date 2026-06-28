package app.organicmaps.cairodrive.overlay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.cairodrive.CairoConfig;
import app.organicmaps.cairodrive.cameras.CameraAggregator;
import app.organicmaps.cairodrive.cameras.OverpassCamera;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.traffic.TrafficAggregator;
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

  private final CairoMapOverlay mOverlay = new CairoMapOverlay();
  private final CameraAggregator mCameras = new CameraAggregator();
  private final TrafficAggregator mTraffic = new TrafficAggregator();
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
    if (!CairoConfig.isOnlineEnabled(ctx))
    {
      clear();
      if (badge != null)
        badge.onCameraCount(0);
      return;
    }

    mIo.execute(() -> {
      List<OverpassCamera> cams = new ArrayList<>();
      List<TrafficIncident> incidents = new ArrayList<>();
      try
      {
        cams = mCameras.collect(new GeoPoint(lat, lon), FETCH_RADIUS_M);
      }
      catch (Throwable t)
      {
        CairoLog.w(SUB, "camera fetch failed: " + t.getMessage());
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
      mUi.post(() -> {
        final int count = mOverlay.render(fcams, fincidents);
        if (badge != null)
          badge.onCameraCount(count);
      });
    });
  }

  public void clear()
  {
    mUi.post(mOverlay::clear);
  }
}
