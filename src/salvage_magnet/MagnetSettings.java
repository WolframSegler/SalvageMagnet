package salvage_magnet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

public final class MagnetSettings {
    public static final String MOD_ID = "salvage_magnet";
    public static final String VANILLA_FIELD = "salvageMagnet_vanilla";
    public static final String VANILLA = "$" + VANILLA_FIELD;

    private MagnetSettings() {
    }

    public static boolean hasLunaLib() {
        return Global.getSettings().getModManager().isModEnabled("lunalib");
    }

    public static void apply() {
        final MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();

        if (hasLunaLib()) {
            global.set(VANILLA, LunaSettingsBridge.vanilla());
        } else {
            global.unset(VANILLA);
        }
    }

    public static boolean isVanilla() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(VANILLA);
    }

    public static int getRange() {
        return LunaSettingsBridge.salvageRange;
    }
}