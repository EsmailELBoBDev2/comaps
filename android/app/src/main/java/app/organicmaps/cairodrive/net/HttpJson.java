package app.organicmaps.cairodrive.net;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.util.log.CairoLog;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/// Minimal HTTP+JSON helper for CairoDrive's online providers. Uses only the
/// Android framework (HttpURLConnection + org.json), so it adds no
/// dependencies. All logging goes through CairoLog, which scrubs keys/tokens,
/// and request URLs are themselves scrubbed before logging.
public final class HttpJson
{
  private static final String SUB = "net";
  private static final int CONNECT_TIMEOUT_MS = 10_000;
  private static final int READ_TIMEOUT_MS = 15_000;

  private HttpJson() {}

  @NonNull
  public static JSONObject getObject(@NonNull String url, @Nullable Map<String, String> headers) throws IOException
  {
    return new JSONObject(request("GET", url, headers, null));
  }

  @NonNull
  public static JSONArray getArray(@NonNull String url, @Nullable Map<String, String> headers) throws IOException
  {
    return new JSONArray(request("GET", url, headers, null));
  }

  @NonNull
  public static JSONObject postObject(@NonNull String url, @Nullable Map<String, String> headers,
                                      @NonNull String jsonBody) throws IOException
  {
    return new JSONObject(request("POST", url, headers, jsonBody));
  }

  @NonNull
  private static String request(@NonNull String method, @NonNull String url,
                                @Nullable Map<String, String> headers, @Nullable String body) throws IOException
  {
    final long t0 = System.nanoTime();
    HttpURLConnection conn = null;
    try
    {
      conn = (HttpURLConnection) new URL(url).openConnection();
      conn.setRequestMethod(method);
      conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
      conn.setReadTimeout(READ_TIMEOUT_MS);
      conn.setRequestProperty("Accept", "application/json");
      if (headers != null)
        for (Map.Entry<String, String> e : headers.entrySet())
          conn.setRequestProperty(e.getKey(), e.getValue());

      if (body != null)
      {
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream())
        {
          os.write(body.getBytes(StandardCharsets.UTF_8));
        }
      }

      final int code = conn.getResponseCode();
      final boolean ok = code >= 200 && code < 300;
      final String payload = readAll(ok ? conn.getInputStream() : conn.getErrorStream());
      final long ms = (System.nanoTime() - t0) / 1_000_000;
      CairoLog.d(SUB, method + " " + scrubUrl(url) + " -> " + code + " (" + ms + "ms)");
      if (!ok)
        throw new IOException("HTTP " + code + " for " + scrubUrl(url));
      return payload;
    }
    finally
    {
      if (conn != null)
        conn.disconnect();
    }
  }

  @NonNull
  private static String readAll(@Nullable InputStream in) throws IOException
  {
    if (in == null)
      return "";
    final StringBuilder sb = new StringBuilder();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
    {
      String line;
      while ((line = r.readLine()) != null)
        sb.append(line);
    }
    return sb.toString();
  }

  /// Strips key/token query params so URLs are safe to log even before
  /// CairoLog's own scrubbing.
  @NonNull
  public static String scrubUrl(@NonNull String url)
  {
    return url.replaceAll("(?i)([?&])(key|api_key|apikey|access_token|token|apiKey)=[^&]*", "$1$2=***");
  }

  /// org.json wrappers throw JSONException (a checked subtype is convenient as
  /// IOException for callers); helper to keep call sites tidy.
  @NonNull
  public static JSONObject parseObject(@NonNull String raw) throws IOException
  {
    try { return new JSONObject(raw); }
    catch (JSONException e) { throw new IOException("Bad JSON object", e); }
  }

  @NonNull
  public static Map<String, String> noHeaders()
  {
    return Collections.emptyMap();
  }
}
