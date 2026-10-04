package salvage_magnet;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

public class SalvageMagnetModPlugin extends BaseModPlugin {
    @Override
    public void onApplicationLoad() {
        if (MagnetSettings.hasLunaLib()) {
            LunaSettingsBridge.register();
        }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        Global.getSector().getCharacterData().addAbility("magnet_scavenge");
        Global.getSector().getPlayerFleet().addAbility("magnet_scavenge");
        MagnetSettings.apply();
    }
}
