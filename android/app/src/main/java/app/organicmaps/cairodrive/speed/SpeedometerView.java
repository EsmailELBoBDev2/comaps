package app.organicmaps.cairodrive.speed;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/// Small on-screen speed readout pinned to the bottom-right of the map. Built
/// programmatically (no XML) and attached to the activity's content frame,
/// mirroring {@link app.organicmaps.cairodrive.overlay.CamerasBadge}. Shows
/// "NN km/h" in green normally and switches to a red background when the driver
/// is over the threshold. Reused via a view tag.
public final class SpeedometerView extends FrameLayout
{
  private static final String TAG = "cairo_speedometer";

  private static final int COLOR_NORMAL_TEXT = Color.argb(0xFF, 0x00, 0xC8, 0x53);
  private static final int COLOR_NORMAL_BG = Color.argb(0xCC, 0x00, 0x00, 0x00);
  private static final int COLOR_OVER_TEXT = Color.WHITE;
  private static final int COLOR_OVER_BG = Color.argb(0xEE, 0xD3, 0x2F, 0x2F);

  private final TextView mReadout;

  public SpeedometerView(@NonNull Activity activity)
  {
    super(activity);
    setTag(TAG);

    final float density = activity.getResources().getDisplayMetrics().density;
    final int pad = (int) (8 * density);

    mReadout = new TextView(activity);
    mReadout.setTextColor(COLOR_NORMAL_TEXT);
    mReadout.setBackgroundColor(COLOR_NORMAL_BG);
    mReadout.setPadding(pad * 2, pad, pad * 2, pad);
    mReadout.setTextSize(18);

    final FrameLayout.LayoutParams lp =
        new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    addView(mReadout, lp);

    update(0, false);
  }

  /// Updates the displayed speed and over/under styling.
  public void update(int kmh, boolean over)
  {
    mReadout.setText(kmh + " km/h");
    mReadout.setTextColor(over ? COLOR_OVER_TEXT : COLOR_NORMAL_TEXT);
    mReadout.setBackgroundColor(over ? COLOR_OVER_BG : COLOR_NORMAL_BG);
  }

  /// Attaches a speedometer to the activity's content frame (or returns the
  /// existing one). Returns the view so callers can {@link #update(int, boolean)}.
  @Nullable
  public static SpeedometerView attach(@NonNull Activity activity)
  {
    final View content = activity.findViewById(android.R.id.content);
    if (!(content instanceof ViewGroup))
      return null;
    final ViewGroup root = (ViewGroup) content;

    SpeedometerView existing = root.findViewWithTag(TAG);
    if (existing != null)
      return existing;

    final SpeedometerView view = new SpeedometerView(activity);
    final float density = activity.getResources().getDisplayMetrics().density;
    final int margin = (int) (16 * density);

    final FrameLayout.LayoutParams lp =
        new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.gravity = Gravity.BOTTOM | Gravity.END;
    lp.bottomMargin = margin;
    lp.rightMargin = margin;
    root.addView(view, lp);
    return view;
  }

  /// Removes the speedometer from the activity, if present.
  public static void detach(@NonNull Activity activity)
  {
    final View content = activity.findViewById(android.R.id.content);
    if (content instanceof ViewGroup)
    {
      final View view = ((ViewGroup) content).findViewWithTag(TAG);
      if (view != null)
        ((ViewGroup) content).removeView(view);
    }
  }

  /// Returns the attached speedometer, or null if none is attached.
  @Nullable
  public static SpeedometerView get(@NonNull Activity activity)
  {
    final View content = activity.findViewById(android.R.id.content);
    if (content instanceof ViewGroup)
      return ((ViewGroup) content).findViewWithTag(TAG);
    return null;
  }
}
