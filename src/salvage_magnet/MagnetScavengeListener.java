package salvage_magnet;

import java.util.*;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.SectorEntityToken;

import salvage_magnet.rulecmd.MagnetScavenge;

public class MagnetScavengeListener extends BaseCampaignEventListener {

    public MagnetScavengeListener(boolean permaRegister) {
        super(permaRegister);
    }

    @Override
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {
        SectorEntityToken target = dialog.getInteractionTarget();
        if (!(dialog.getPlugin() instanceof RuleBasedDialog plugin) || !SalvageTargets.isGroupableDerelict(target)) {
            return;
        }

        List<SectorEntityToken> targets = SalvageTargets.derelicts(Global.getSector().getPlayerFleet());
        targets.remove(target);
        targets.add(0, target);
        if (targets.size() < 2) {
            return;
        }

        target.getMemory().set(MagnetScavenge.TARGETS, targets, 0f);
        target.getMemory().set(MagnetScavenge.FLAG, true, 0f);

        if (dialog.getOptionPanel().hasOptions()) {
            dialog.getTextPanel().clear();
            plugin.reinit(false);
        }
    }
}
