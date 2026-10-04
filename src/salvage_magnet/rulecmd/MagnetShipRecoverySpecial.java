package salvage_magnet.rulecmd;

import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;

public class MagnetShipRecoverySpecial extends ShipRecoverySpecial {
    public static class Data extends ShipRecoverySpecialData {
        public Data(ShipRecoverySpecialData from) {
            super(from.desc);
            ships = from.ships;
            storyPointRecovery = from.storyPointRecovery;
            noDescriptionText = from.noDescriptionText;
        }
    }
}
