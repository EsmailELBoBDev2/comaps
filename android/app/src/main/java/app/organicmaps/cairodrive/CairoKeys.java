package app.organicmaps.cairodrive;

import androidx.annotation.NonNull;
import app.organicmaps.BuildConfig;

/// Typed, scrubbed access to the provider API keys injected at build time from
/// the gitignored android/secrets.properties (see CairoKeys never logs a value;
/// the {@code has*()} guards let callers skip a provider whose key is absent so
/// the app degrades gracefully instead of firing keyless requests.
public final class CairoKeys
{
  private CairoKeys() {}

  @NonNull public static String magicLane()    { return BuildConfig.MAGIC_LANE_API_KEY; }
  @NonNull public static String tomTom()        { return BuildConfig.TOMTOM_API_KEY; }
  @NonNull public static String mapbox()        { return BuildConfig.MAPBOX_API_KEY; }
  @NonNull public static String googlePlaces()  { return BuildConfig.GOOGLE_PLACES_API_KEY; }
  @NonNull public static String here()          { return BuildConfig.HERE_API_KEY; }
  @NonNull public static String geoapify()      { return BuildConfig.GEOAPIFY_API_KEY; }
  @NonNull public static String locationIq()    { return BuildConfig.LOCATIONIQ_API_KEY; }
  @NonNull public static String mapillary()      { return BuildConfig.MAPILLARY_API_KEY; }
  @NonNull public static String openRouteService(){ return BuildConfig.OPENROUTESERVICE_API_KEY; }
  /// Optional URL of a supplementary camera dataset (GeoJSON FeatureCollection).
  @NonNull public static String cameraDatasetUrl(){ return BuildConfig.CAMERA_DATASET_URL; }

  public static boolean hasMagicLane()    { return !magicLane().isEmpty(); }
  public static boolean hasTomTom()       { return !tomTom().isEmpty(); }
  public static boolean hasMapbox()       { return !mapbox().isEmpty(); }
  public static boolean hasGooglePlaces() { return !googlePlaces().isEmpty(); }
  public static boolean hasHere()         { return !here().isEmpty(); }
  public static boolean hasGeoapify()     { return !geoapify().isEmpty(); }
  public static boolean hasLocationIq()   { return !locationIq().isEmpty(); }
  public static boolean hasMapillary()    { return !mapillary().isEmpty(); }
  public static boolean hasOpenRouteService() { return !openRouteService().isEmpty(); }
  public static boolean hasCameraDataset()    { return !cameraDatasetUrl().isEmpty(); }
}
