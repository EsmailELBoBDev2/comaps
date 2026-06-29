package app.organicmaps.cairodrive.routing;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;
import java.util.ArrayList;
import java.util.List;

/// Decoder for Google/Mapbox encoded polylines.
///
/// Implements the standard Encoded Polyline Algorithm Format. The {@code
/// precision} parameter selects the coordinate resolution: precision 5 is the
/// classic Google format ({@code geometries=polyline}), precision 6 is the
/// higher-resolution Mapbox format ({@code geometries=polyline6}).
///
/// See https://developers.google.com/maps/documentation/utilities/polylinealgorithm
public final class PolylineCodec
{
  private PolylineCodec() {}

  /// Decodes an encoded polyline string into a list of {@link GeoPoint}s.
  ///
  /// @param encoded   the encoded polyline (may be null/empty -> empty list)
  /// @param precision number of decimal digits encoded (5 or 6)
  @NonNull
  public static List<GeoPoint> decode(String encoded, int precision)
  {
    final List<GeoPoint> points = new ArrayList<>();
    if (encoded == null || encoded.isEmpty())
      return points;

    final double factor = Math.pow(10, precision);
    final int len = encoded.length();
    int index = 0;
    int lat = 0;
    int lon = 0;

    while (index < len)
    {
      int result = 1;
      int shift = 0;
      int b;
      do
      {
        b = encoded.charAt(index++) - 63 - 1;
        result += b << shift;
        shift += 5;
      }
      while (b >= 0x1f && index < len);
      lat += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);

      result = 1;
      shift = 0;
      do
      {
        b = encoded.charAt(index++) - 63 - 1;
        result += b << shift;
        shift += 5;
      }
      while (b >= 0x1f && index < len);
      lon += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);

      points.add(new GeoPoint(lat / factor, lon / factor));
    }
    return points;
  }
}
