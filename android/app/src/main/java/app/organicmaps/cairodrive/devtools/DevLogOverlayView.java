package app.organicmaps.cairodrive.devtools;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import app.organicmaps.sdk.util.log.CairoLog;
import java.util.List;

/// Self-contained on-screen developer log panel. Renders the {@link CairoLog}
/// ring buffer in a scrolling monospace TextView with Clear/Close controls and
/// auto-refreshes once per second while attached. Built entirely in code, so it
/// needs no layout XML.
public final class DevLogOverlayView extends FrameLayout
{
  private static final long REFRESH_MS = 1000L;
  private static final int PANEL_BG = 0xCC000000; // semi-transparent dark.
  private static final int TEXT_COLOR = 0xFFE0E0E0;
  private static final int ERROR_COLOR = 0xFFFF6E6E;

  @NonNull private final TextView mLog;
  @NonNull private final ScrollView mScroll;
  @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());

  private final Runnable mTick = new Runnable()
  {
    @Override
    public void run()
    {
      refresh();
      mHandler.postDelayed(this, REFRESH_MS);
    }
  };

  public DevLogOverlayView(@NonNull Context context)
  {
    super(context);

    setBackgroundColor(PANEL_BG);

    final LinearLayout root = new LinearLayout(context);
    root.setOrientation(LinearLayout.VERTICAL);
    final int pad = dp(8);
    root.setPadding(pad, pad, pad, pad);
    addView(root, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

    // Header row with Clear/Close buttons.
    final LinearLayout buttons = new LinearLayout(context);
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    buttons.setGravity(Gravity.END);
    root.addView(buttons, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    final Button clear = new Button(context);
    clear.setText("Clear");
    clear.setOnClickListener(v ->
    {
      CairoLog.clear();
      refresh();
    });
    buttons.addView(clear);

    final Button close = new Button(context);
    close.setText("Close");
    close.setOnClickListener(v -> setVisibility(GONE));
    buttons.addView(close);

    // Scrolling log body.
    mScroll = new ScrollView(context);
    mScroll.setFillViewport(true);
    final LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
    root.addView(mScroll, scrollLp);

    mLog = new TextView(context);
    mLog.setTypeface(Typeface.MONOSPACE);
    mLog.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
    mLog.setTextColor(TEXT_COLOR);
    mLog.setTextIsSelectable(true);
    mLog.setPadding(pad, pad, pad, pad);
    mScroll.addView(mLog, new ScrollView.LayoutParams(
        ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

    refresh();
  }

  /// Pulls the latest log entries, colours error lines red-ish, and scrolls to
  /// the bottom so the newest output is visible.
  public void refresh()
  {
    final List<String> entries = CairoLog.getEntries();

    boolean hasError = false;
    final StringBuilder sb = new StringBuilder();
    for (final String e : entries)
    {
      sb.append(e).append('\n');
      if (e.startsWith("E/"))
        hasError = true;
    }

    // Cheap, reliable colouring: tint the whole panel text red when any error
    // line is present; otherwise use the normal text colour. (Per-line spans
    // are avoided to keep this dependency-free and simple.)
    mLog.setTextColor(hasError ? ERROR_COLOR : TEXT_COLOR);
    mLog.setText(sb.toString());

    mScroll.post(() -> mScroll.fullScroll(View.FOCUS_DOWN));
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    mHandler.removeCallbacks(mTick);
    mHandler.post(mTick);
  }

  @Override
  protected void onDetachedFromWindow()
  {
    mHandler.removeCallbacks(mTick);
    super.onDetachedFromWindow();
  }

  private int dp(int value)
  {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
