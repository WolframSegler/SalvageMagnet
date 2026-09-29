package salvage_magnet;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

public class SalvageMagnetModPlugin extends BaseModPlugin {
    @Override
    public void onGameLoad(boolean newGame) {
        final SectorAPI sector = Global.getSector();
        sector.getCharacterData().addAbility("magnet_scavenge");
        sector.getPlayerFleet().addAbility("magnet_scavenge");
        sector.addTransientListener(new MagnetScavengeListener(false));
    }
}