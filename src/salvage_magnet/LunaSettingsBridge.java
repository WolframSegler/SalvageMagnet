package salvage_magnet;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

final class LunaSettingsBridge implements LunaSettingsListener {

    protected static int salvageRange = getRange();

    static void register() {
        if (!LunaSettings.hasSettingsListenerOfClass(LunaSettingsBridge.class)) {
            LunaSettings.addSettingsListener(new LunaSettingsBridge());
        }
    }

    static boolean vanilla() {
        return Boolean.TRUE.equals(LunaSettings.getBoolean(MagnetSettings.MOD_ID, MagnetSettings.VANILLA_FIELD));
    }

    private static int getRange() {
        final Integer range = LunaSettings.getInt(MagnetSettings.MOD_ID, "salvage_range");
        return range == null ? (int) Global.getSettings().getFloat("magnetScavengeRadius") : range;
    }

    @Override
    public void settingsChanged(String modId) {
        if (!MagnetSettings.MOD_ID.equals(modId)) return;
        
        if (Global.getCurrentState() != GameState.TITLE && Global.getSector() != null) {
            MagnetSettings.apply();
        }

        salvageRange = getRange();
    }
}
