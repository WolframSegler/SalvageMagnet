package aoescavenge;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

public class AoEScavengeModPlugin extends BaseModPlugin {
    @Override
    public void onGameLoad(boolean newGame) {
        Global.getSector().getCharacterData().addAbility("magnet_scavenge");
        Global.getSector().getPlayerFleet().addAbility("magnet_scavenge");
        Global.getSector().addTransientListener(new AoEScavengeListener(false));
    }
}
