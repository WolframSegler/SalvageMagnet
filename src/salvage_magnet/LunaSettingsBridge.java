package salvage_magnet;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

// Only loaded when LunaLib is enabled.
final class LunaSettingsBridge implements LunaSettingsListener {

    static void register() {
        if (!LunaSettings.hasSettingsListenerOfClass(LunaSettingsBridge.class)) {
            LunaSettings.addSettingsListener(new LunaSettingsBridge());
        }
    }

    static boolean vanilla() {
        return Boolean.TRUE.equals(LunaSettings.getBoolean(MagnetSettings.MOD_ID, MagnetSettings.VANILLA_FIELD));
    }

    @Override
    public void settingsChanged(String modId) {
        if (MagnetSettings.MOD_ID.equals(modId)
                && Global.getCurrentState() != GameState.TITLE
                && Global.getSector() != null) {
            MagnetSettings.apply();
        }
    }
}
