package app.organicmaps.cairodrive.routing;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;
import java.util.ArrayList;
import java.util.List;

/// Decoder for HERE's Flexible Polyline encoding (used by Routing API v8
/// {@code sections[].polyline}).
///
/// The format encodes, in order: a header version, then a header value packing
/// the coordinate precision (and optional 3rd-dimension precision/type), then a
/// sequence of zig-zag + varint encoded lat/lng (and optional 3rd-dim) deltas.
/// Each varint uses a base-64 alphabet, 5 data bits per char, high bit as a
/// continuation flag. Only the 2D lat/lng pairs are returned here; any 3rd
/// dimension is parsed and discarded.
///
/// Reference: https://github.com/heremaps/flexible-polyline
/// The schema may need live tuning against the API.
public final class HereFlexiblePolyline
{
  private static final String ENCODING_TABLE =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

  /// Reverse lookup: char -> 6-bit value, or -1 for chars outside the alphabet.
  private static final int[] DECODING_TABLE = buildDecodingTable();

  private HereFlexiblePolyline() {}

  /// Decodes a HERE flexible polyline into a list of {@link GeoPoint}s.
  ///
  /// Returns an empty list when {@code encoded} is null/empty. Throws
  /// {@link IllegalArgumentException} on malformed input so callers can choose
  /// to fall back to an empty polyline rather than propagating the error.
  @NonNull
  public static List<GeoPoint> decode(String encoded)
  {
    final List<GeoPoint> points = new ArrayList<>();
    if (encoded == null || encoded.isEmpty())
      return points;

    final Decoder decoder = new Decoder(encoded);

    // Header version (currently 1).
    final long version = decoder.decodeUnsignedVarint();
    if (version != 1)
      throw new IllegalArgumentException("Unsupported HERE flexible polyline version: " + version);

    // Header value: bits 0-3 = precision, bits 4-6 = 3rd-dim type, bits 7-10 =
    // 3rd-dim precision (parsed but unused since we only emit 2D points).
    final long header = decoder.decodeUnsignedVarint();
    final int precision = (int) (header & 15);
    final int thirdDimType = (int) ((header >> 4) & 7);

    final double latLngFactor = Math.pow(10, precision);
    final boolean hasThirdDim = thirdDimType != 0;

    long lat = 0;
    long lng = 0;

    while (decoder.hasNext())
    {
      lat += decoder.decodeSignedVarint();
      lng += decoder.decodeSignedVarint();
      if (hasThirdDim)
        decoder.decodeSignedVarint(); // 3rd dimension parsed and discarded

      points.add(new GeoPoint(lat / latLngFactor, lng / latLngFactor));
    }
    return points;
  }

  /// Stateful base-64 varint reader over the encoded string.
  private static final class Decoder
  {
    @NonNull private final String mData;
    private int mIndex;

    Decoder(@NonNull String data)
    {
      mData = data;
      mIndex = 0;
    }

    boolean hasNext()
    {
      return mIndex < mData.length();
    }

    /// Reads one unsigned varint (LSB-first, 5 data bits per char).
    long decodeUnsignedVarint()
    {
      long result = 0;
      int shift = 0;
      while (mIndex < mData.length())
      {
        final char c = mData.charAt(mIndex++);
        if (c >= DECODING_TABLE.length || DECODING_TABLE[c] < 0)
          throw new IllegalArgumentException("Invalid character in HERE polyline: " + c);
        final long value = DECODING_TABLE[c];
        result |= (value & 0x1F) << shift;
        shift += 5;
        if ((value & 0x20) == 0)
          return result;
      }
      throw new IllegalArgumentException("Unexpected end of HERE polyline");
    }

    /// Reads one zig-zag encoded signed varint.
    long decodeSignedVarint()
    {
      final long unsigned = decodeUnsignedVarint();
      // Zig-zag decode: even -> positive, odd -> negative.
      return (unsigned & 1) != 0 ? ~(unsigned >> 1) : (unsigned >> 1);
    }
  }

  @NonNull
  private static int[] buildDecodingTable()
  {
    final int[] table = new int['_' + 1];
    for (int i = 0; i < table.length; i++)
      table[i] = -1;
    for (int i = 0; i < ENCODING_TABLE.length(); i++)
      table[ENCODING_TABLE.charAt(i)] = i;
    return table;
  }
}
