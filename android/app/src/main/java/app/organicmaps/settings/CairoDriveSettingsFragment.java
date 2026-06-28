package app.organicmaps.settings;

import android.os.Bundle;
import androidx.annotation.XmlRes;
import androidx.preference.PreferenceDataStore;
import app.organicmaps.R;
import app.organicmaps.cairodrive.CairoConfig;

/// CairoDrive settings sub-screen: the online-features master toggle and the
/// developer log overlay toggle. Both are backed by {@link CairoConfig} (a
/// dedicated SharedPreferences file) through a {@link PreferenceDataStore}, so
/// CairoConfig stays the single source of truth that the rest of the app reads.
public class CairoDriveSettingsFragment extends BaseXmlSettingsFragment
{
  @Override
  protected @XmlRes int getXmlResources()
  {
    return R.xml.prefs_cairodrive;
  }

  @Override
  public void onCreatePreferences(Bundle bundle, String root)
  {
    final PreferenceDataStore store = new PreferenceDataStore()
    {
      @Override
      public void putBoolean(String key, boolean value)
      {
        if (key.equals(getString(R.string.pref_cairodrive_online)))
          CairoConfig.setOnlineEnabled(requireContext(), value);
        else if (key.equals(getString(R.string.pref_cairodrive_dev_overlay)))
          CairoConfig.setDevOverlayEnabled(requireContext(), value);
      }

      @Override
      public boolean getBoolean(String key, boolean defValue)
      {
        if (key.equals(getString(R.string.pref_cairodrive_online)))
          return CairoConfig.isOnlineEnabled(requireContext());
        if (key.equals(getString(R.string.pref_cairodrive_dev_overlay)))
          return CairoConfig.isDevOverlayEnabled(requireContext());
        return defValue;
      }
    };
    getPreferenceManager().setPreferenceDataStore(store);
    super.onCreatePreferences(bundle, root);
  }
}
