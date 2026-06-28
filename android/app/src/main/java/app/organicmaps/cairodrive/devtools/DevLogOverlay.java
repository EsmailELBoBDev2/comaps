package app.organicmaps.cairodrive.devtools;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.cairodrive.CairoConfig;
import app.organicmaps.sdk.util.log.CairoLog;

/// Tiny controller that attaches/detaches a {@link DevLogOverlayView} to an
/// activity's content view. The overlay only appears when
/// {@link CairoConfig#isDevOverlayEnabled} is on, and is tagged so repeated
/// {@link #show} calls don't stack duplicates.
public final class DevLogOverlay
{
  private static final String SUB = "devtools";

  // Used as a view tag so we can locate an already-added overlay. A String
  // constant avoids needing a generated R.id (no resources to edit).
  private static final String OVERLAY_TAG = "cairodrive_dev_log_overlay";

  // Keep the overlay below a typical action bar so it doesn't cover it.
  private static final int TOP_MARGIN_DP = 56;

  private DevLogOverlay() {}

  /// Adds the overlay to the activity's content frame if dev overlay is enabled
  /// and one isn't already present.
  public static void show(@NonNull Activity activity)
  {
    if (!CairoConfig.isDevOverlayEnabled(activity))
      return;

    final FrameLayout content = contentView(activity);
    if (content == null)
      return;

    if (find(content) != null)
      return; // Already shown; guard against double-add.

    final DevLogOverlayView view = new DevLogOverlayView(activity);
    view.setTag(OVERLAY_TAG);

    final int topMargin = Math.round(TOP_MARGIN_DP * activity.getResources().getDisplayMetrics().density);
    final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    lp.topMargin = topMargin;
    content.addView(view, lp);
    CairoLog.i(SUB, "dev log overlay shown");
  }

  /// Removes the overlay if present.
  public static void hide(@NonNull Activity activity)
  {
    final FrameLayout content = contentView(activity);
    if (content == null)
      return;

    final View existing = find(content);
    if (existing != null)
    {
      content.removeView(existing);
      CairoLog.i(SUB, "dev log overlay hidden");
    }
  }

  /// Shows the overlay if hidden, hides it if shown.
  public static void toggle(@NonNull Activity activity)
  {
    final FrameLayout content = contentView(activity);
    if (content != null && find(content) != null)
      hide(activity);
    else
      show(activity);
  }

  @Nullable
  private static FrameLayout contentView(@NonNull Activity activity)
  {
    final View v = activity.findViewById(android.R.id.content);
    return (v instanceof FrameLayout) ? (FrameLayout) v : null;
  }

  @Nullable
  private static View find(@NonNull ViewGroup parent)
  {
    return parent.findViewWithTag(OVERLAY_TAG);
  }
}
