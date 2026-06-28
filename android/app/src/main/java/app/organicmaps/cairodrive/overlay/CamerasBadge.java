package app.organicmaps.cairodrive.overlay;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;

/// Small "N cameras" badge pinned to the top of the map. Built programmatically
/// (no XML) and attached to the activity's content frame; reused via a view tag.
public final class CamerasBadge
{
  private static final String TAG = "cairo_cameras_badge";

  private CamerasBadge() {}

  public static void show(@NonNull Activity activity, int cameraCount)
  {
    final View content = activity.findViewById(android.R.id.content);
    if (!(content instanceof ViewGroup))
      return;
    final ViewGroup root = (ViewGroup) content;

    TextView badge = root.findViewWithTag(TAG);
    if (cameraCount <= 0)
    {
      if (badge != null)
        root.removeView(badge);
      return;
    }

    if (badge == null)
    {
      badge = new TextView(activity);
      badge.setTag(TAG);
      badge.setTextColor(Color.WHITE);
      badge.setBackgroundColor(Color.argb(0xCC, 0x00, 0x00, 0x00));
      final int pad = (int) (8 * activity.getResources().getDisplayMetrics().density);
      badge.setPadding(pad * 2, pad, pad * 2, pad);

      final FrameLayout.LayoutParams lp =
          new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
      lp.topMargin = (int) (72 * activity.getResources().getDisplayMetrics().density);
      root.addView(badge, lp);
    }

    badge.setText(cameraCount + (cameraCount == 1 ? " camera" : " cameras"));
  }

  public static void hide(@NonNull Activity activity)
  {
    final View content = activity.findViewById(android.R.id.content);
    if (content instanceof ViewGroup)
    {
      final View badge = ((ViewGroup) content).findViewWithTag(TAG);
      if (badge != null)
        ((ViewGroup) content).removeView(badge);
    }
  }
}
