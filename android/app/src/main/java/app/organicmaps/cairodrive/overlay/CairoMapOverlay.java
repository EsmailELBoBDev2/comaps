package app.organicmaps.cairodrive.overlay;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import app.organicmaps.cairodrive.cameras.OverpassCamera;
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

  private long mCatId = -1;
  private final List<Long> mMarkIds = new ArrayList<>();

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

  /// Remove all marks previously added by this overlay.
  public void clear()
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

  /// Replace the overlay with the given cameras (colour by type) and incidents
  /// (colour by severity). Returns the number of camera marks drawn (for the
  /// in-view badge).
  public int render(@NonNull List<OverpassCamera> cameras, @NonNull List<TrafficIncident> incidents)
  {
    clear();
    final long cat = category();
    if (cat < 0)
      return 0;

    int cameraCount = 0;
    for (OverpassCamera c : cameras)
    {
      if (addMark(cat, c.location.lat, c.location.lon, c.type.label(), c.type.colorArgb()))
        cameraCount++;
    }
    for (TrafficIncident i : incidents)
      addMark(cat, i.location.lat, i.location.lon, i.description, i.severity.color());

    CairoLog.i(SUB, "render cameras=" + cameraCount + " incidents=" + incidents.size());
    return cameraCount;
  }

  private boolean addMark(long cat, double lat, double lon, @NonNull String title, int argb)
  {
    try
    {
      final Bookmark b = BookmarkManager.INSTANCE.addNewBookmark(lat, lon);
      if (b == null)
        return false;
      // addNewBookmark targets the last-edited category; move it to ours.
      BookmarkManager.INSTANCE.notifyCategoryChanging(b, cat);
      final int colorIndex = PredefinedColors.getPredefinedColorIndex(argb);
      BookmarkManager.INSTANCE.setBookmarkParams(b.getBookmarkId(), title, colorIndex, "");
      mMarkIds.add(b.getBookmarkId());
      return true;
    }
    catch (Throwable t)
    {
      CairoLog.w(SUB, "addMark failed: " + t.getMessage());
      return false;
    }
  }
}
