package app.organicmaps.cairodrive.overlay;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;

/// "CairoDrive tools" button -> opens a chooser (route compare / online search /
/// street view). Built programmatically, mirroring CairoReportButton.
public final class CairoToolsButton
{
  private static final String TAG = "cairo_tools_btn";

  private CairoToolsButton() {}

  public static void show(@NonNull Activity activity, @NonNull Runnable onTap)
  {
    final View content = activity.findViewById(android.R.id.content);
    if (!(content instanceof ViewGroup))
      return;
    final ViewGroup root = (ViewGroup) content;
    if (root.findViewWithTag(TAG) != null)
      return;

    final Button button = new Button(activity);
    button.setTag(TAG);
    button.setText("🧭 Tools");
    button.setTextColor(Color.WHITE);
    button.setBackgroundColor(Color.argb(0xCC, 0x37, 0x47, 0x4F));

    final float d = activity.getResources().getDisplayMetrics().density;
    final FrameLayout.LayoutParams lp =
        new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.gravity = Gravity.BOTTOM | Gravity.START;
    lp.leftMargin = (int) (12 * d);
    lp.bottomMargin = (int) (200 * d);  // above the parking + report buttons
    button.setOnClickListener(v -> onTap.run());
    root.addView(button, lp);
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
