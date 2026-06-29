package app.organicmaps.sdk.util.log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/// CairoDrive's developer logger. Everything goes out under the single logcat
/// tag {@code CairoDrive} (so `adb logcat -s CairoDrive` shows only our lines)
/// and is mirrored into a bounded in-memory ring buffer that the on-screen dev
/// log panel reads. All messages pass through {@link SecretScrubber}, so API
/// keys and tokens never reach logcat, the log file, or the overlay.
public final class CairoLog
{
  public static final String TAG = "CairoDrive";

  private static final int MAX_ENTRIES = 500;
  private static final Deque<String> sBuffer = new ArrayDeque<>(MAX_ENTRIES);

  private CairoLog() {}

  public static void d(@NonNull String sub, @NonNull String msg) { emit('D', sub, msg, null); }
  public static void i(@NonNull String sub, @NonNull String msg) { emit('I', sub, msg, null); }
  public static void w(@NonNull String sub, @NonNull String msg) { emit('W', sub, msg, null); }
  public static void e(@NonNull String sub, @NonNull String msg) { emit('E', sub, msg, null); }
  public static void e(@NonNull String sub, @NonNull String msg, @Nullable Throwable tr) { emit('E', sub, msg, tr); }

  private static void emit(char level, @NonNull String sub, @NonNull String msg, @Nullable Throwable tr)
  {
    // Scrub here too so the ring buffer never holds a secret, even though
    // Logger scrubs again on its own (the operation is idempotent).
    final String line = "[" + sub + "] " + SecretScrubber.scrub(msg);
    switch (level)
    {
    case 'D': Logger.d(TAG, line); break;
    case 'I': Logger.i(TAG, line); break;
    case 'W': Logger.w(TAG, line); break;
    default:  if (tr != null) Logger.e(TAG, line, tr); else Logger.e(TAG, line); break;
    }
    synchronized (sBuffer)
    {
      if (sBuffer.size() >= MAX_ENTRIES)
        sBuffer.pollFirst();
      sBuffer.addLast(level + "/" + line);
    }
  }

  /// Snapshot of recent log lines for the on-screen dev panel (oldest first).
  @NonNull
  public static List<String> getEntries()
  {
    synchronized (sBuffer)
    {
      return new ArrayList<>(sBuffer);
    }
  }

  public static void clear()
  {
    synchronized (sBuffer)
    {
      sBuffer.clear();
    }
  }
}
