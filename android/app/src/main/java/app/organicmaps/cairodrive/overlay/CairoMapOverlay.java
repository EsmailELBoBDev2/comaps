package app.organicmaps.cairodrive.overlay;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import app.organicmaps.cairodrive.cameras.OverpassCamera;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.reports.CairoReport;
import app.organicmaps.cairodrive.routing.OnlineRoute;
import app.organicmaps.cairodrive.safety.Hazard;
import app.organicmaps.cairodrive.traffic.TrafficIncident;
import app.organicmaps.sdk.bookmarks.data.Bookmark;
import app.organicmaps.sdk.bookmarks.data.BookmarkCategory;
import app.organicmaps.sdk.bookmarks.data.BookmarkManager;
import app.organicmaps.sdk.bookmarks.data.PredefinedColors;
import app.organicmaps.sdk.util.log.CairoLog;
import java.util.ArrayList;
import java.util.List;

/// Draws CairoDrive's online camera + traffic points on the core map by reusing
/// the existing bookmark/mark infrastructure (the same path the app already
/// renders through), so the marks also appear on the Android Auto car screen
/// for free. Points are color-mapped to the nearest predefined palette colour.
///
/// Marks live in a single dedicated, clearly-named category that is reused
/// across runs (found by name) and rebuilt on each refresh. Every BookmarkManager
/// interaction is guarded so a failure degrades to "no overlay" instead of
/// crashing -- the app stays fully usable.
///
/// NOTE (prod follow-up): bookmarks are persistent KML; a transient core
/// UserMarkLayer exposed via JNI would be the non-persistent replacement. This
/// reuse is the build-safe path that renders today.
@UiThread
public final class CairoMapOverlay
{
  private static final String SUB = "overlay";
  private static final String CATEGORY = "CairoDrive Live (auto)";

  private static final long INVALID_TRACK = -1L;  // kml::kInvalidTrackId (uint64 max) as a jlong.
  // Route line colours (ARGB): fastest = green, alternatives = blue.
  private static final int COLOR_FASTEST = 0xFF2E7D32;
  private static final int COLOR_ALT = 0xFF1565C0;
  private static final double ROUTE_WIDTH_PX = 6.0;

  private long mCatId = -1;
  private final List<Long> mMarkIds = new ArrayList<>();
  private final List<Long> mTrackIds = new ArrayList<>();
  private final List<Long> mReportIds = new ArrayList<>();
  private final List<Long> mHazardIds = new ArrayList<>();

  private long category()
  {
    if (mCatId >= 0)
      return mCatId;
    try
    {
      // Reuse an existing category from a previous run if present.
      for (BookmarkCategory c : BookmarkManager.INSTANCE.getCategories())
      {
        if (CATEGORY.equals(c.getName()))
        {
          mCatId = c.getId();
          return mCatId;
        }
      }
      mCatId = BookmarkManager.INSTANCE.createCategory(CATEGORY);
    }
    catch (Throwable t)
    {
      CairoLog.w(SUB, "category() failed: " + t.getMessage());
      mCatId = -1;
    }
    return mCatId;
  }

  /// Remove all marks, route lines and report marks previously added.
  public void clear()
  {
    clearMarks();
    clearTracks();
    clearReports();
    clearHazards();
  }

  private void clearHazards()
  {
    for (long id : mHazardIds)
    {
      try
      {
        BookmarkManager.INSTANCE.deleteBookmark(id);
      }
      catch (Throwable t)
      {
        CairoLog.w(SUB, "deleteHazard failed: " + t.getMessage());
      }
    }
    mHazardIds.clear();
  }

  /// Draw Cairo safety hazards (speed bumps, school zones, curves, crossings).
  public void showHazards(@NonNull List<Hazard> hazards)
  {
    clearHazards();
    final long cat = category();
    if (cat < 0)
      return;
    for (Hazard h : hazards)
    {
      final Long id = addMarkId(cat, h.location.lat, h.location.lon, h.type.label(), h.type.colorArgb());
      if (id != null)
        mHazardIds.add(id);
    }
    CairoLog.i(SUB, "hazards drawn=" + mHazardIds.size());
  }

  private void clearReports()
  {
    for (long id : mReportIds)
    {
      try
      {
        BookmarkManager.INSTANCE.deleteBookmark(id);
      }
      catch (Throwable t)
      {
        CairoLog.w(SUB, "deleteReport failed: " + t.getMessage());
      }
    }
    mReportIds.clear();
  }

  /// Draw the locally-stored community reports as colour-coded marks.
  public void showReports(@NonNull List<CairoReport> reports)
  {
    clearReports();
    final long cat = category();
    if (cat < 0)
      return;
    for (CairoReport r : reports)
    {
      final Long id = addMarkId(cat, r.lat, r.lon, r.kind.label, r.kind.colorArgb);
      if (id != null)
        mReportIds.add(id);
    }
    CairoLog.i(SUB, "reports drawn=" + mReportIds.size());
  }

  private void clearMarks()
  {
    for (long id : mMarkIds)
    {
      try
      {
        BookmarkManager.INSTANCE.deleteBookmark(id);
      }
      catch (Throwable t)
      {
        CairoLog.w(SUB, "deleteBookmark failed: " + t.getMessage());
      }
    }
    mMarkIds.clear();
  }

  private void clearTracks()
  {
    for (long id : mTrackIds)
    {
      try
      {
        BookmarkManager.INSTANCE.deleteTrack(id);
      }
      catch (Throwable t)
      {
        CairoLog.w(SUB, "deleteTrack failed: " + t.getMessage());
      }
    }
    mTrackIds.clear();
  }

  /// Draw the route-compare polylines: the fastest route in green, the rest in
  /// blue. Call after render() (which clears previous overlay state).
  public void showRoutes(@NonNull List<OnlineRoute> routes)
  {
    clearTracks();  // replace any previous route lines
    final long cat = category();
    if (cat < 0)
      return;
    for (OnlineRoute r : routes)
    {
      if (r.polyline.size() < 2)
        continue;
      final double[] latLon = new double[r.polyline.size() * 2];
      int j = 0;
      for (GeoPoint p : r.polyline)
      {
        latLon[j++] = p.lat;
        latLon[j++] = p.lon;
      }
      try
      {
        final long id = BookmarkManager.INSTANCE.createTrack(latLon, r.isFastest ? COLOR_FASTEST : COLOR_ALT,
                                                             ROUTE_WIDTH_PX, cat);
        if (id != INVALID_TRACK)
          mTrackIds.add(id);
      }
      catch (Throwable t)
      {
        CairoLog.w(SUB, "createTrack failed: " + t.getMessage());
      }
    }
    CairoLog.i(SUB, "routes drawn=" + mTrackIds.size());
  }

  /// Replace the overlay with the given cameras (colour by type) and incidents
  /// (colour by severity). Returns the number of camera marks drawn (for the
  /// in-view badge).
  public int render(@NonNull List<OverpassCamera> cameras, @NonNull List<TrafficIncident> incidents)
  {
    clearMarks();  // points only; leaves any route lines intact
    final long cat = category();
    if (cat < 0)
      return 0;

    int cameraCount = 0;
    for (OverpassCamera c : cameras)
    {
      final Long id = addMarkId(cat, c.location.lat, c.location.lon, c.type.label(), c.type.colorArgb());
      if (id != null)
      {
        mMarkIds.add(id);
        cameraCount++;
      }
    }
    for (TrafficIncident i : incidents)
    {
      final Long id = addMarkId(cat, i.location.lat, i.location.lon, i.description, i.severity.color());
      if (id != null)
        mMarkIds.add(id);
    }

    CairoLog.i(SUB, "render cameras=" + cameraCount + " incidents=" + incidents.size());
    return cameraCount;
  }

  /// Add one bookmark mark in the given category; returns its id, or null on
  /// failure. The caller decides which id list it belongs to.
  @Nullable
  private Long addMarkId(long cat, double lat, double lon, @NonNull String title, int argb)
  {
    try
    {
      final Bookmark b = BookmarkManager.INSTANCE.addNewBookmark(lat, lon);
      if (b == null)
        return null;
      // addNewBookmark targets the last-edited category; move it to ours.
      BookmarkManager.INSTANCE.notifyCategoryChanging(b, cat);
      final int colorIndex = PredefinedColors.getPredefinedColorIndex(argb);
      BookmarkManager.INSTANCE.setBookmarkParams(b.getBookmarkId(), title, colorIndex, "");
      return b.getBookmarkId();
    }
    catch (Throwable t)
    {
      CairoLog.w(SUB, "addMark failed: " + t.getMessage());
      return null;
    }
  }
}
