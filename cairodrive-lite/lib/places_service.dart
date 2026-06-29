import 'dart:convert';
import 'package:http/http.dart' as http;

/// One business/place result from Google Places.
class Place {
  final String name;
  final String address;
  final double lat;
  final double lon;
  const Place(this.name, this.address, this.lat, this.lon);
}

/// Google Places API (NEW) Text Search.
///
/// POST https://places.googleapis.com/v1/places:searchText
/// Headers: X-Goog-Api-Key, X-Goog-FieldMask
/// We use the NEW API on purpose: keys provisioned today enable "Places API
/// (New)"; the legacy textsearch endpoint returns REQUEST_DENIED for them.
class PlacesService {
  final String apiKey;
  const PlacesService(this.apiKey);

  bool get isConfigured => apiKey.isNotEmpty;

  /// Search businesses/places by free text, biased around (lat,lon).
  Future<List<Place>> searchText(String query, {double? lat, double? lon}) async {
    if (!isConfigured || query.trim().isEmpty) return const [];

    final body = <String, dynamic>{'textQuery': query};
    if (lat != null && lon != null) {
      body['locationBias'] = {
        'circle': {
          'center': {'latitude': lat, 'longitude': lon},
          'radius': 30000.0,
        }
      };
    }

    final resp = await http.post(
      Uri.parse('https://places.googleapis.com/v1/places:searchText'),
      headers: {
        'Content-Type': 'application/json',
        'X-Goog-Api-Key': apiKey,
        'X-Goog-FieldMask':
            'places.displayName,places.formattedAddress,places.location',
      },
      body: jsonEncode(body),
    );

    if (resp.statusCode != 200) {
      throw Exception('Places ${resp.statusCode}: ${resp.body}');
    }

    final json = jsonDecode(resp.body) as Map<String, dynamic>;
    final places = (json['places'] as List?) ?? const [];
    return places.map((p) {
      final m = p as Map<String, dynamic>;
      final name = (m['displayName']?['text'] as String?) ?? '';
      final address = (m['formattedAddress'] as String?) ?? '';
      final loc = m['location'] as Map<String, dynamic>?;
      return Place(
        name,
        address,
        (loc?['latitude'] as num?)?.toDouble() ?? 0,
        (loc?['longitude'] as num?)?.toDouble() ?? 0,
      );
    }).where((p) => p.lat != 0 || p.lon != 0).toList();
  }
}
