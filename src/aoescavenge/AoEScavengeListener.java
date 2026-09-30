package aoescavenge;

import java.util.*;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.SectorEntityToken;

import aoescavenge.rulecmd.AoEScavenge;

public class AoEScavengeListener extends BaseCampaignEventListener {

    public AoEScavengeListener(boolean permaRegister) {
        super(permaRegister);
    }

    @Override
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {
        SectorEntityToken target = dialog.getInteractionTarget();
        if (!(dialog.getPlugin() instanceof RuleBasedDialog plugin)
                || dialog.getPlugin().getMemoryMap() == null
                || !SalvageTargets.isGroupableDerelict(target)) {
            return;
        }

        List<SectorEntityToken> targets = SalvageTargets.derelicts(Global.getSector().getPlayerFleet());
        targets.remove(target);
        targets.add(0, target);
        if (targets.size() < 2) {
            return;
        }

        targets = StockRules.vetted(dialog, null, targets);
        if (targets.size() < 2 || targets.get(0) != target) {
            return;
        }

        target.getMemory().set(AoEScavenge.TARGETS, targets, 0f);
        target.getMemory().set(AoEScavenge.FLAG, true, 0f);

        if (dialog.getOptionPanel().hasOptions()) {
            dialog.getTextPanel().clear();
            plugin.reinit(false);
        }
    }
}
