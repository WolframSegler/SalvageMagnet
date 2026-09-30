package aoescavenge;

import java.util.*;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.rules.RuleAPI;
import com.fs.starfarer.api.campaign.rules.RulesAPI;

import aoescavenge.rulecmd.AoEScavenge;

public final class StockRules {
    private static final String OPTION = "$option";
    private static final String OPTION_SELECTED = "DialogOptionSelected";

    private static final Map<String, Set<String>> BEST_BY_TRIGGER = Map.of(
        "OpenInteractionDialog", Set.of("sal_default", "sal_derelictDefault", "sal_scavengeDebris"),
        "ShowSalvageEntityDetails", Set.of("sal_defaultDetails", "sal_wreckDetails"),
        "SalvageCheckHostile", Set.of("sal_hostileNearby", "sal_noHostileNearby"),
        "CheckSalvageSpecial", Set.of("sal_checkSpecialFound", "sal_checkSpecialNoneFound"),
        "SalvageSpecialFinished", Set.of("sal_specialFinished"),
        "SalvageSpecialFinishedNoContinue", Set.of("sal_specialFinishedNoContinue"),
        "BeginSalvage", Set.of("sal_showRatingAndCost"),
        "ShipRecoveryCustomText", Set.of(),
        "PostSalvagePerform", Set.of()
    );

    private static final Map<String, Set<String>> ALL_BY_TRIGGER = Map.of(
        "PopulateSalvageOptions1", Set.of("sal_defaultLeave1", "sal_explore", "sal_assess"),
        "PopulateSalvageOptions2", Set.of("sal_optionLeave", "sal_optionSalvageEnabled", "sal_forceRecoveryOpt")
    );

    private static final Map<String, Set<String>> BEST_BY_OPTION = Map.of(
        "salExplore", Set.of("sal_noDefenders"),
        "salSpecialContinue", Set.of("sal_specialContinueSelected"),
        "salSalvage", Set.of("sal_optionCheckAccidents"),
        "salPerform", Set.of("sal_salvageOptionSelected")
    );

    private StockRules() {
    }

    public static List<SectorEntityToken> vetted(InteractionDialogAPI dialog, SectorEntityToken proxy,
            List<SectorEntityToken> targets) {
        SectorEntityToken original = dialog.getInteractionTarget();
        MemoryAPI proxyMemory = proxy == null ? null : proxy.getMemoryWithoutUpdate();

        List<SectorEntityToken> result = new ArrayList<>();
        for (SectorEntityToken target : targets) {
            AoEScavenge.aim(dialog, proxy, target);
            if (followsStockRules(dialog)) {
                result.add(target);
            }
        }

        if (proxy != null) {
            proxy.setMemory(proxyMemory);
        }
        dialog.setInteractionTarget(original);
        ((RuleBasedDialog) dialog.getPlugin()).updateMemory();
        return result;
    }

    private static boolean followsStockRules(InteractionDialogAPI dialog) {
        Map<String, MemoryAPI> memoryMap = dialog.getPlugin().getMemoryMap();
        RulesAPI rules = Global.getSector().getRules();

        for (Map.Entry<String, Set<String>> stock : BEST_BY_TRIGGER.entrySet()) {
            if (!isAmong(stock.getValue(), rules.getBestMatching(null, stock.getKey(), dialog, memoryMap))) {
                return false;
            }
        }

        for (Map.Entry<String, Set<String>> stock : ALL_BY_TRIGGER.entrySet()) {
            for (RuleAPI rule : rules.getAllMatching(null, stock.getKey(), dialog, memoryMap)) {
                if (!isAmong(stock.getValue(), rule)) {
                    return false;
                }
            }
        }

        MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
        try {
            for (Map.Entry<String, Set<String>> stock : BEST_BY_OPTION.entrySet()) {
                local.set(OPTION, stock.getKey(), 0f);
                if (!isAmong(stock.getValue(), rules.getBestMatching(null, OPTION_SELECTED, dialog, memoryMap))) {
                    return false;
                }
            }
        } finally {
            local.unset(OPTION);
        }

        return true;
    }

    private static boolean isAmong(Set<String> stockRuleIds, RuleAPI rule) {
        return rule == null || stockRuleIds.contains(rule.getId());
    }
}
