package app.organicmaps.cairodrive.overlay;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;

/// On-screen speed-camera HUD: shows the approaching camera's type and a live
/// distance countdown (e.g. "Red-light camera · 320 m"). Driven each GPS fix
/// from the native closest-camera accessors. Built programmatically, mirroring
/// CamerasBadge.
public final class CameraHudView
{
  private static final String TAG = "cairo_camera_hud";

  // Indexed by routing::SpeedCameraType ordinal: 0=Unknown..4=Mobile.
  private static final String[] LABELS = {"Camera", "Fixed camera", "Red-light camera", "Average-speed camera",
                                          "Mobile camera"};

  private CameraHudView() {}

  public static void show(@NonNull Activity activity, int typeOrdinal, double distanceMeters)
  {
    final View content = activity.findViewById(android.R.id.content);
    if (!(content instanceof ViewGroup))
      return;
    final ViewGroup root = (ViewGroup) content;

    TextView hud = root.findViewWithTag(TAG);
    if (hud == null)
    {
      hud = new TextView(activity);
      hud.setTag(TAG);
      hud.setTextColor(Color.WHITE);
      hud.setBackgroundColor(Color.argb(0xE0, 0xC6, 0x28, 0x28));  // strong red
      final int pad = (int) (8 * activity.getResources().getDisplayMetrics().density);
      hud.setPadding(pad * 2, pad, pad * 2, pad);

      final FrameLayout.LayoutParams lp =
          new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
      // Sits below the "N cameras" badge (which is at ~72dp).
      lp.topMargin = (int) (118 * activity.getResources().getDisplayMetrics().density);
      root.addView(hud, lp);
    }

    final String label = (typeOrdinal >= 0 && typeOrdinal < LABELS.length) ? LABELS[typeOrdinal] : LABELS[0];
    hud.setText("📷 " + label + " · " + Math.round(distanceMeters) + " m");
  }

  public static void hide(@NonNull Activity activity)
  {
    final View content = activity.findViewById(android.R.id.content);
    if (content instanceof ViewGroup)
    {
      final View hud = ((ViewGroup) content).findViewWithTag(TAG);
      if (hud != null)
        ((ViewGroup) content).removeView(hud);
    }
  }
}
