package salvage_magnet.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;

public class RemoveAbility implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (Global.getCurrentState() != GameState.CAMPAIGN) {
            Console.showMessage("This command is only applicable to the campaign.");
            return CommandResult.WRONG_CONTEXT;
        }

        final CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        if (playerFleet.hasAbility("magnet_scavenge")) {
            playerFleet.removeAbility("magnet_scavenge");
            Global.getSector().getCharacterData().removeAbility("magnet_scavenge");

            Console.showMessage("Magnet Scavenge ability removed. You may now disable the mod and load this save without issues.");
            Global.getSector().getCampaignUI().cmdSaveAndExit();
        }

        return CommandResult.SUCCESS;
    }
}
