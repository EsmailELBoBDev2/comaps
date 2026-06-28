package app.organicmaps.sdk.util.log;

import androidx.annotation.NonNull;
import java.util.regex.Pattern;

/// Redacts API keys and tokens from log messages before they are printed to
/// logcat or written to the on-disk log file. Applied centrally in
/// {@link Logger} so every log call site (including native code proxied via
/// JNI) is covered.
///
/// Scrubs the patterns required by the CairoDrive security baseline:
/// `key=`, `api_key=`, `token=`, `access_token=` (case-insensitive, `=` or
/// `:` separators), plus `Bearer <token>` authorization values.
public final class SecretScrubber
{
  private static final String REDACTED = "***";

  // key/token names followed by '=' or ':' and a run of non-space value chars.
  // Longer names are listed first so they win the alternation.
  private static final Pattern KEY_VALUE =
      Pattern.compile("(?i)\\b(access_token|api_key|token|key)\\s*[=:]\\s*[^\\s&\"',;]+");

  private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._\\-]+");

  private SecretScrubber() {}

  @NonNull
  public static String scrub(@NonNull String message)
  {
    if (message.isEmpty())
      return message;
    String scrubbed = KEY_VALUE.matcher(message).replaceAll("$1=" + REDACTED);
    scrubbed = BEARER.matcher(scrubbed).replaceAll("Bearer " + REDACTED);
    return scrubbed;
  }
}
