package app.organicmaps.cairodrive.overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.reports.CairoReport;

/// A small "Report" button pinned to the map; tapping it opens a one-tap chooser
/// for the community report kinds (camera / radar / police / bump / pothole /
/// hazard). Built programmatically and attached to the activity content frame,
/// mirroring CamerasBadge.
public final class CairoReportButton
{
  private static final String TAG = "cairo_report_btn";

  public interface Sink
  {
    void onReport(@NonNull CairoReport.Kind kind);
  }

  private CairoReportButton() {}

  public static void show(@NonNull Activity activity, @NonNull Sink sink)
  {
    final View content = activity.findViewById(android.R.id.content);
    if (!(content instanceof ViewGroup))
      return;
    final ViewGroup root = (ViewGroup) content;
    if (root.findViewWithTag(TAG) != null)
      return;

    final Button button = new Button(activity);
    button.setTag(TAG);
    button.setText("⚠ Report");
    button.setTextColor(Color.WHITE);
    button.setBackgroundColor(Color.argb(0xCC, 0xD3, 0x2F, 0x2F));

    final float d = activity.getResources().getDisplayMetrics().density;
    final FrameLayout.LayoutParams lp =
        new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.gravity = Gravity.BOTTOM | Gravity.START;
    lp.leftMargin = (int) (12 * d);
    lp.bottomMargin = (int) (96 * d);
    button.setOnClickListener(v -> showChooser(activity, sink));
    root.addView(button, lp);
  }

  private static void showChooser(@NonNull Activity activity, @NonNull Sink sink)
  {
    final CairoReport.Kind[] kinds = CairoReport.Kind.values();
    final String[] labels = new String[kinds.length];
    for (int i = 0; i < kinds.length; i++)
      labels[i] = kinds[i].label;

    new AlertDialog.Builder(activity)
        .setTitle("Report")
        .setItems(labels, (dialog, which) -> sink.onReport(kinds[which]))
        .show();
  }

  public static void hide(@NonNull Activity activity)
  {
    final View content = activity.findViewById(android.R.id.content);
    if (content instanceof ViewGroup)
    {
      final View button = ((ViewGroup) content).findViewWithTag(TAG);
      if (button != null)
        ((ViewGroup) content).removeView(button);
    }
  }
}
