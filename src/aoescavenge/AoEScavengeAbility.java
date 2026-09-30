package aoescavenge;

import java.util.*;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.abilities.ScavengeAbility;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;

import aoescavenge.rulecmd.AoEScavenge;

public class AoEScavengeAbility extends ScavengeAbility {

    @Override
    protected void activateImpl() {
        if (!this.entity.isPlayerFleet()) {
            return;
        }

        CampaignFleetAPI fleet = this.getFleet();
        DebrisFieldTerrainPlugin current = this.getDebrisField();
        if (fleet == null || current == null) {
            return;
        }

        List<SectorEntityToken> targets = SalvageTargets.near(fleet);
        if (targets.size() < 2 || !targets.remove(current.getEntity())) {
            super.activateImpl();
            return;
        }
        targets.add(0, current.getEntity());

        LocationAPI location = fleet.getContainingLocation();
        SectorEntityToken proxy = location.addCustomEntity(
            null, null, AoEScavenge.TYPE, current.getEntity().getFaction().getId());
        location.removeEntity(proxy);
        proxy.getLocation().set(fleet.getLocation());
        proxy.getMemory().set(AoEScavenge.TARGETS, targets, 0f);
        proxy.getMemory().set(AoEScavenge.FLAG, true, 0f);

        Global.getSector().getCampaignUI().showInteractionDialog(
            new RuleBasedInteractionDialogPluginImpl(),
            proxy
        );
    }
}
