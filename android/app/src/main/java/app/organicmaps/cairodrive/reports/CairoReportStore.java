package app.organicmaps.cairodrive.reports;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import app.organicmaps.sdk.util.log.CairoLog;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/// Local-only persistence for community reports (no backend). Backed by a
/// private SharedPreferences JSON array. Expired transient reports are pruned on
/// every load and persisted away, so the map never shows a stale mobile-radar
/// or police mark.
public final class CairoReportStore
{
  private static final String SUB = "reports";
  private static final String PREFS = "cairodrive_reports";
  private static final String KEY = "reports";

  private CairoReportStore() {}

  @NonNull
  private static SharedPreferences prefs(@NonNull Context ctx)
  {
    return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  /// All non-expired reports (and prunes expired ones from storage).
  @NonNull
  public static List<CairoReport> active(@NonNull Context ctx, long nowMs)
  {
    final List<CairoReport> out = new ArrayList<>();
    boolean pruned = false;
    try
    {
      final JSONArray arr = new JSONArray(prefs(ctx).getString(KEY, "[]"));
      for (int i = 0; i < arr.length(); i++)
      {
        final JSONObject o = arr.optJSONObject(i);
        if (o == null)
          continue;
        final CairoReport r = CairoReport.fromJson(o);
        if (r.isExpired(nowMs))
          pruned = true;
        else
          out.add(r);
      }
    }
    catch (Throwable t)
    {
      CairoLog.w(SUB, "load failed: " + t.getMessage());
    }
    if (pruned)
      save(ctx, out);
    return out;
  }

  public static void add(@NonNull Context ctx, @NonNull CairoReport report, long nowMs)
  {
    final List<CairoReport> all = active(ctx, nowMs);
    all.add(report);
    save(ctx, all);
    CairoLog.i(SUB, "added " + report.kind.name() + " total=" + all.size());
  }

  public static void clear(@NonNull Context ctx)
  {
    prefs(ctx).edit().remove(KEY).apply();
  }

  private static void save(@NonNull Context ctx, @NonNull List<CairoReport> reports)
  {
    try
    {
      final JSONArray arr = new JSONArray();
      for (CairoReport r : reports)
        arr.put(r.toJson());
      prefs(ctx).edit().putString(KEY, arr.toString()).apply();
    }
    catch (Throwable t)
    {
      CairoLog.w(SUB, "save failed: " + t.getMessage());
    }
  }
}
