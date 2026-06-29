package app.organicmaps.cairodrive.overlay;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;

/// A small parking button: tap to save the current spot ("where I parked"),
/// long-press to clear it. Built programmatically, mirroring CairoReportButton.
public final class CairoParkingButton
{
  private static final String TAG = "cairo_parking_btn";

  private CairoParkingButton() {}

  public static void show(@NonNull Activity activity, @NonNull Runnable onTap, @NonNull Runnable onLongPress)
  {
    final View content = activity.findViewById(android.R.id.content);
    if (!(content instanceof ViewGroup))
      return;
    final ViewGroup root = (ViewGroup) content;
    if (root.findViewWithTag(TAG) != null)
      return;

    final Button button = new Button(activity);
    button.setTag(TAG);
    button.setText("🅿");
    button.setTextColor(Color.WHITE);
    button.setBackgroundColor(Color.argb(0xCC, 0x00, 0x89, 0x7B));

    final float d = activity.getResources().getDisplayMetrics().density;
    final FrameLayout.LayoutParams lp =
        new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.gravity = Gravity.BOTTOM | Gravity.START;
    lp.leftMargin = (int) (12 * d);
    lp.bottomMargin = (int) (148 * d);  // sits above the report button
    button.setOnClickListener(v -> onTap.run());
    button.setOnLongClickListener(v -> {
      onLongPress.run();
      return true;
    });
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
