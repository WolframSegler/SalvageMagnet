package aoescavenge.rulecmd;

import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;

public class AoEShipRecoverySpecial extends ShipRecoverySpecial {
    public static class Data extends ShipRecoverySpecialData {
        public Data(ShipRecoverySpecialData from) {
            super(from.desc);
            ships = from.ships;
            storyPointRecovery = from.storyPointRecovery;
            noDescriptionText = from.noDescriptionText;
        }
    }
}
