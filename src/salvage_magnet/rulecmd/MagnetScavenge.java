package salvage_magnet.rulecmd;

import java.util.*;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageEntityGeneratorOld;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageGenFromSeed;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipRecoverySpecialData;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import salvage_magnet.SalvageTargets;
import salvage_magnet.StockRules;

public class MagnetScavenge extends SalvageEntity {
    public static final String TARGETS = "$magnetScavengeTargets";
    public static final String FLAG = "$magnetScavenge";
    public static final String STATE = "$magnetScavengeState";
    public static final String TYPE = "debris_field_shared";
    public static final String RECOVER = "magnetRecover";
    public static final String SKIP_RECOVERY = "magnetRecoverSkip";
    public static final String LEAVE_RECOVERY = "magnetRecoverLeave";
    public static final String OPENED = "MagnetScavengeOpened";
    public static final String DERELICT_OPEN_RULE = "magnet_derelictOpen";

    public static class State {
        public List<SectorEntityToken> targets;
        public SectorEntityToken proxy;
        public List<MagnetRecovery.Candidate> recoverable = new ArrayList<>();
        public Map<SectorEntityToken, ShipRecoverySpecialData> detached = new LinkedHashMap<>();
        public Set<SectorEntityToken> recovered = new HashSet<>();
        public Map<SectorEntityToken, CargoAPI> hauls = new LinkedHashMap<>();
        public CargoAPI loot = Global.getFactory().createCargo(true);
        public int index = -1;
        public long xp = 0L;
        public int crewLost = 0;
        public int machineryLost = 0;
    }

    private Map<String, MemoryAPI> map;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params,
            Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params.isEmpty()) {
            return false;
        }

        this.map = memoryMap;
        super.execute(ruleId, dialog, params, memoryMap);

        String command = params.get(0).getString(memoryMap);
        if ("canGroup".equals(command)) {
            return canGroup();
        } else if ("begin".equals(command)) {
            begin();
        } else if ("descAll".equals(command)) {
            describe(state());
        } else if ("explore".equals(command)) {
            explore(state());
        } else if ("recover".equals(command)) {
            recover(state());
        } else if ("leave".equals(command)) {
            leave(state());
        } else if ("next".equals(command)) {
            next(state());
        } else if ("salvage".equals(command)) {
            salvage(state());
        } else if ("defer".equals(command)) {
            defer(state());
        }

        return true;
    }

    protected State state() {
        return (State) memory.get(STATE);
    }

    protected boolean canGroup() {
        if (!SalvageTargets.isGroupableDerelict(entity)) {
            return false;
        }

        List<SectorEntityToken> targets = SalvageTargets.derelicts(playerFleet);
        targets.remove(entity);
        targets.add(0, entity);
        if (targets.size() < 2) {
            return false;
        }

        targets = StockRules.vetted(dialog, null, targets);
        if (targets.size() < 2 || targets.get(0) != entity) {
            return false;
        }

        memory.set(TARGETS, targets, 0f);
        return true;
    }

    @SuppressWarnings("unchecked")
    protected void begin() {
        List<SectorEntityToken> targets = new ArrayList<>((List<SectorEntityToken>) memory.get(TARGETS));
        SectorEntityToken proxy = TYPE.equals(entity.getCustomEntityType()) ? entity : null;

        if (proxy != null) {
            SectorEntityToken scavenged = targets.get(0);
            targets = StockRules.vetted(dialog, proxy, targets);
            if (targets.size() < 2 || targets.get(0) != scavenged) {
                focus(proxy, scavenged);
                FireBest.fire(null, dialog, map, "OpenInteractionDialog");
                return;
            }
        }

        Misc.stopPlayerFleet();
        State state = new State();
        state.targets = targets;
        state.proxy = proxy;
        memory.set(FLAG, true, 0f);
        memory.set(STATE, state, 0f);
        FireBest.fire(null, dialog, map, OPENED);
    }

    public static void aim(InteractionDialogAPI dialog, SectorEntityToken proxy, SectorEntityToken target) {
        DebrisFieldTerrainPlugin field = field(target);
        if (field == null) {
            dialog.setInteractionTarget(target);
        } else {
            MemoryAPI fieldMemory = target.getMemory();
            fieldMemory.set("$salvageDebrisField", field, 0f);
            proxy.setFaction(target.getFaction().getId());
            proxy.getLocation().set(target.getLocation());
            proxy.setMemory(fieldMemory);
            dialog.setInteractionTarget(proxy);
        }

        ((RuleBasedDialog) dialog.getPlugin()).updateMemory();
    }

    protected static DebrisFieldTerrainPlugin field(SectorEntityToken target) {
        return target instanceof CampaignTerrainAPI terrain
            && terrain.getPlugin() instanceof DebrisFieldTerrainPlugin field ? field : null;
    }

    protected static float lootValue(DebrisFieldTerrainPlugin field) {
        float value = 0f;
        for (SalvageEntityGenDataSpec.DropData data : field.getEntity().getDropValue()) {
            value += data.value;
        }
        for (SalvageEntityGenDataSpec.DropData data : field.getEntity().getDropRandom()) {
            value += data.value > 0 ? data.value : 500f;
        }
        return value * field.getParams().density;
    }

    protected static float accidentProbability(DebrisFieldTerrainPlugin field) {
        return Math.min(0.9f, 0.2f + 0.8f * (1f - field.getParams().density));
    }

    protected static int outlook(DebrisFieldTerrainPlugin field) {
        float loot = lootValue(field);
        float risk = accidentProbability(field);
        int stability = field.getDaysLeft() >= 1000f ? 1 : 0;
        int value = loot < 500f ? 0 : loot < 2500f ? 1 : 2;
        int safety = risk <= 0.2f ? 2 : risk < 0.7f ? 1 : 0;
        return stability + value + safety;
    }

    protected void describe(State state) {
        List<DebrisFieldTerrainPlugin> fields = new ArrayList<>();
        int derelicts = 0;
        for (SectorEntityToken target : state.targets) {
            DebrisFieldTerrainPlugin field = field(target);
            if (field != null) {
                fields.add(field);
            } else {
                derelicts++;
            }
        }

        if (fields.size() > 1) {
            text.addPara("Sensors map %s debris fields within reach of your salvage crews.",
                    Misc.getHighlightColor(), new String[] { "" + fields.size() });
        }

        if (!fields.isEmpty()) {
            DebrisFieldTerrainPlugin best = Collections.max(fields, Comparator
                    .comparingInt(MagnetScavenge::outlook)
                    .thenComparingDouble(MagnetScavenge::lootValue)
                    .thenComparingDouble(DebrisFieldTerrainPlugin::getDaysLeft));
            bind(state, best.getEntity());
            new SalvageEntity().execute(null, dialog, Misc.tokenize("descDebris"), map);
        }

        if (derelicts > 0) {
            String hulls = derelicts == 1 ? "a derelict hull" : derelicts + " derelict hulls";
            text.addPara("Long-range scans pick out %s drifting within reach of your salvage crews.",
                    Misc.getHighlightColor(), new String[] { hulls });
        }
    }

    protected void explore(State state) {
        state.recoverable = MagnetRecovery.collect(state.targets);
        if (state.recoverable.isEmpty()) {
            next(state);
            return;
        }

        state.detached = MagnetRecovery.detachFromSources(state.recoverable);

        CampaignFleetAPI recoverable = Global.getFactory().createEmptyFleet("neutral", "patrolSmall", true);
        for (MagnetRecovery.Candidate candidate : state.recoverable) {
            recoverable.getFleetData().addFleetMember(candidate.member);
        }

        if (state.recoverable.size() == 1) {
            dialog.getVisualPanel().showFleetMemberInfo(state.recoverable.get(0).member, true);
        } else {
            dialog.getVisualPanel().showFleetInfo("Your fleet", playerFleet, "Recoverable ships", recoverable, null, true);
        }

        int storyPoint = storyPointPool(state).size();
        int regular = state.recoverable.size() - storyPoint;
        if (regular == 1) {
            text.addPara("Salvage crews report %s that could be restored to basic functionality. "
                    + "If not recovered, the ship will be scuttled, and any fitted weapons and fighter LPCs will be retrieved.",
                    Misc.getHighlightColor(), new String[] { "a ship" });
        } else if (regular > 1) {
            text.addPara("Salvage crews report %s that could be restored to basic functionality. "
                    + "Any ships that aren't recovered will be scuttled, and any fitted weapons and fighter LPCs will be retrieved.",
                    Misc.getHighlightColor(), new String[] { regular + " ships" });
        }
        if (storyPoint > 0) {
            String hulls = regular > 0
                    ? (storyPoint == 1 ? "another hull" : storyPoint + " more hulls")
                    : (storyPoint == 1 ? "a derelict hull" : storyPoint + " derelict hulls");
            text.addPara("Your chief engineer believes %s could be made to fly again, "
                    + "though it would take special measures.",
                    Misc.getStoryOptionColor(), new String[] { hulls });
        }

        options.clearOptions();
        options.addOption("Consider ship recovery", RECOVER,
                regular == 0 ? Misc.getStoryOptionColor() : Misc.getButtonTextColor(), null);
        options.addOption("Continue", SKIP_RECOVERY);
        options.addOption("Leave the ships untouched", LEAVE_RECOVERY,
                "Recoverable hulls are left as they are and skipped by the salvage operation, so you can return for them later.");
    }

    protected void leave(State state) {
        new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), map);
        MagnetRecovery.reattach(state.detached);
        state.detached.clear();

        Set<SectorEntityToken> skipped = new HashSet<>();
        for (MagnetRecovery.Candidate candidate : state.recoverable) {
            skipped.add(candidate.source);
        }
        state.recoverable.clear();

        for (SectorEntityToken source : skipped) {
            int i = state.targets.indexOf(source);
            if (i < 0) {
                continue;
            }
            state.targets.remove(i);
            if (i <= state.index) {
                state.index--;
            }
            release(source);
        }

        String hulls = skipped.size() == 1 ? "the recoverable hull" : "the recoverable hulls";
        text.addPara("Your salvage crews are instructed to leave %s untouched for now.",
                Misc.getHighlightColor(), new String[] { hulls });

        if (state.index + 1 >= state.targets.size()) {
            for (SectorEntityToken target : state.targets) {
                release(target);
            }
            if (state.proxy != null) {
                release(state.proxy);
            }
            options.clearOptions();
            String leave = memory.contains("$salvageLeaveText") ? memory.getString("$salvageLeaveText") : "Leave";
            options.addOption(leave, "defaultLeave");
            options.setShortcut("defaultLeave", 1, false, false, false, true);
            return;
        }

        next(state);
    }

    protected List<FleetMemberAPI> storyPointPool(State state) {
        List<FleetMemberAPI> pool = new ArrayList<>();
        for (MagnetRecovery.Candidate candidate : state.recoverable) {
            if (candidate.requiresStoryPoint()) {
                pool.add(candidate.member);
            }
        }
        return pool;
    }

    protected List<FleetMemberAPI> regularPool(State state) {
        List<FleetMemberAPI> pool = new ArrayList<>();
        for (MagnetRecovery.Candidate candidate : state.recoverable) {
            if (!candidate.requiresStoryPoint()) {
                pool.add(candidate.member);
            }
        }
        return pool;
    }

    protected void recover(final State state) {
        dialog.showFleetMemberRecoveryDialog("Select ships to recover", regularPool(state), storyPointPool(state),
                new FleetMemberPickerListener() {
                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> selected) {
                        if (!selected.isEmpty()) {
                            recovered(state, selected);
                            next(state);
                        }
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                    }
                });
    }

    protected void recovered(State state, List<FleetMemberAPI> selected) {
        new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), map);

        List<MagnetRecovery.Candidate> picked = new ArrayList<>();
        for (MagnetRecovery.Candidate candidate : state.recoverable) {
            if (selected.contains(candidate.member)) {
                picked.add(candidate);
            }
        }
        state.recoverable.removeAll(picked);

        List<FleetMemberAPI> members = new ArrayList<>();
        for (MagnetRecovery.Candidate candidate : picked) {
            MagnetRecovery.recover(candidate, playerFleet);
            members.add(candidate.member);
            text.addParagraph("The " + candidate.member.getShipName() + " is now part of your fleet.");
        }
        ListenerUtil.reportShipsRecovered(members, dialog);

        for (MagnetRecovery.Candidate candidate : picked) {
            if (field(candidate.source) != null) {
                continue;
            }

            bind(state, candidate.source);
            memory.set("$srs_memberId", candidate.member.getId(), 0f);
            memory.set("$srs_hullId", candidate.member.getHullId(), 0f);
            memory.set("$srs_baseHullId", candidate.member.getHullSpec().getBaseHullId(), 0f);
            FireAll.fire(null, dialog, map, "PostShipRecoverySpecial");

            if (candidate.data.ships.isEmpty()) {
                state.recovered.add(candidate.source);
            }
        }
    }

    protected void retire(State state, SectorEntityToken hull) {
        CargoAPI extra = BaseSalvageSpecial.getCombinedExtraSalvage(hull);
        if (!extra.isEmpty()) {
            state.loot.addAll(extra);
            BaseSalvageSpecial.clearExtraSalvage(hull);
            ListenerUtil.reportSpecialCargoGainedFromRecoveredDerelict(extra, dialog);
        }
        Misc.fadeAndExpire(hull, 1f);
    }

    protected void defer(State state) {
        SectorEntityToken target = state.targets.get(state.index);
        if (state.recovered.contains(target)) {
            retire(state, target);
        }
        next(state);
    }

    protected void scuttleUnrecovered(State state) {
        for (MagnetRecovery.Candidate candidate : state.recoverable) {
            state.loot.addAll(MagnetRecovery.scuttle(candidate));
        }
        state.recoverable.clear();
    }

    protected void next(State state) {
        scuttleUnrecovered(state);

        while (++state.index < state.targets.size()) {
            bind(state, state.targets.get(state.index));
            new SalvageGenFromSeed().execute(null, dialog, Misc.tokenize(""), map);
            if (!memory.getBoolean("$hasDefenders")) {
                FireBest.fire(null, dialog, map, "CheckSalvageSpecial");
                return;
            }
        }

        finish(state);
    }

    protected void bind(State state, SectorEntityToken target) {
        Misc.getSalvageSeed(target);

        MemoryAPI targetMemory = target.getMemory();
        targetMemory.set(FLAG, true, 0f);
        targetMemory.set(STATE, state, 0f);
        focus(state.proxy, target);
    }

    protected void focus(SectorEntityToken proxy, SectorEntityToken target) {
        aim(dialog, proxy, target);

        DebrisFieldTerrainPlugin field = field(target);
        if (field != null) {
            this.spec = configureDebrisSpec(field);
        }
        this.memory = getEntityMemory(map);
        this.entity = dialog.getInteractionTarget();
    }

    protected SalvageEntityGenDataSpec configureDebrisSpec(DebrisFieldTerrainPlugin field) {
        SectorEntityToken source = field.getEntity();
        DebrisFieldTerrainPlugin.DebrisFieldParams params = field.getParams();
        SalvageEntityGenDataSpec spec = SalvageEntityGeneratorOld.getSalvageSpec(TYPE);
        spec.getDropValue().clear();
        spec.getDropRandom().clear();
        spec.getDropValue().addAll(source.getDropValue());
        spec.getDropRandom().addAll(source.getDropRandom());
        spec.setProbDefenders(params.defenderProb * params.density);
        spec.setMinStr(params.minStr * params.density);
        spec.setMaxStr(params.maxStr * params.density);
        spec.setMaxDefenderSize(params.maxDefenderSize);
        spec.setDefFaction(params.defFaction);
        spec.setXpSalvage(params.baseSalvageXP * params.density);
        return spec;
    }

    protected void release(SectorEntityToken target) {
        MemoryAPI targetMemory = target.getMemoryWithoutUpdate();
        targetMemory.unset(FLAG);
        targetMemory.unset(STATE);
        targetMemory.unset(TARGETS);
        if (field(target) != null) {
            targetMemory.unset("$salvageDebrisField");
        }
    }

    protected void salvage(State state) {
        SectorEntityToken target = state.targets.get(state.index);
        DebrisFieldTerrainPlugin field = field(target);
        if (state.recovered.contains(target)) {
            retire(state, target);
        } else if (field == null) {
            state.hauls.put(target, salvageDerelict(state));
        } else {
            state.hauls.put(target, salvageField(state, field));
        }
    }

    protected CargoAPI takeExtraSalvage() {
        CargoAPI extra = BaseSalvageSpecial.getCombinedExtraSalvage(map);
        BaseSalvageSpecial.clearExtraSalvage(map);
        if (!extra.isEmpty()) {
            ListenerUtil.reportExtraSalvageShown(entity);
        }
        return extra;
    }

    protected CargoAPI salvageDerelict(State state) {
        Random random = Misc.getRandom(memory.getLong("$salvageSeed"), 100);
        MutableStat valueRecovery = getValueRecoveryStat(true);
        float rareItemSkillMult = playerFleet.getStats().getDynamic().getValue("salvage_value_bonus_fleet");
        float fuelMult = playerFleet.getStats().getDynamic().getValue("fuel_salvage_value_mult_fleet");

        List<SalvageEntityGenDataSpec.DropData> dropValue = new ArrayList<>(spec.getDropValue());
        List<SalvageEntityGenDataSpec.DropData> dropRandom = new ArrayList<>(spec.getDropRandom());
        dropValue.addAll(entity.getDropValue());
        dropRandom.addAll(entity.getDropRandom());

        CargoAPI salvage = generateSalvage(random, valueRecovery.getModifiedValue(), rareItemSkillMult,
                computeOverallMultForDebrisField(), fuelMult, dropValue, dropRandom);
        salvage.addAll(takeExtraSalvage());
        state.xp += (long) (entity.hasSalvageXP() ? entity.getSalvageXP() : spec.getXpSalvage());

        if (!spec.hasTag("no_debris")) {
            convertToDebrisField(random, FIELD_CONTENT_MULTIPLIER_AFTER_SALVAGE);
        } else if (!spec.hasTag("no_remove")) {
            Misc.fadeAndExpire(entity, 1f);
        }
        return salvage;
    }

    protected CargoAPI salvageField(State state, DebrisFieldTerrainPlugin field) {
        DebrisFieldTerrainPlugin.DebrisFieldParams params = field.getParams();
        long seed = memory.getLong("$salvageSeed");

        accidents(state, field);

        Random random = Misc.getRandom(seed, 100);
        float overallMult = computeOverallMultForDebrisField();
        MutableStat valueRecovery = getValueRecoveryStat(true);
        float rareItemSkillMult = playerFleet.getStats().getDynamic().getValue("salvage_value_bonus_fleet");
        float fuelMult = playerFleet.getStats().getDynamic().getValue("fuel_salvage_value_mult_fleet");

        List<SalvageEntityGenDataSpec.DropData> dropValue = new ArrayList<>(spec.getDropValue());
        List<SalvageEntityGenDataSpec.DropData> dropRandom = new ArrayList<>(spec.getDropRandom());
        dropValue.addAll(entity.getDropValue());
        dropRandom.addAll(entity.getDropRandom());

        memory.unset("$salvageSpecialData");

        CargoAPI salvage = generateSalvage(random, valueRecovery.getModifiedValue(), rareItemSkillMult,
                overallMult, fuelMult, dropValue, dropRandom);
        salvage.addAll(takeExtraSalvage());
        state.xp += (long) spec.getXpSalvage();

        params.density -= overallMult;
        if (params.density < 0f) {
            params.density = 0f;
        }
        field.getEntity().getMemoryWithoutUpdate().set("$salvageSeed", random.nextLong());
        field.setScavenged(true);
        return salvage;
    }

    protected void reportHauls(State state) {
        Iterator<Map.Entry<SectorEntityToken, CargoAPI>> hauls = state.hauls.entrySet().iterator();
        while (hauls.hasNext()) {
            Map.Entry<SectorEntityToken, CargoAPI> haul = hauls.next();
            aim(dialog, state.proxy, haul.getKey());
            if (hauls.hasNext()) {
                ListenerUtil.reportAboutToShowLootToPlayer(haul.getValue(), dialog);
            }
            state.loot.addAll(haul.getValue());
        }
    }

    protected void accidents(State state, DebrisFieldTerrainPlugin field) {
        DebrisFieldTerrainPlugin.DebrisFieldParams params = field.getParams();
        Random random = Misc.getRandom(memory.getLong("$salvageSeed"), 175);

        if (random.nextFloat() > accidentProbability(field)) {
            return;
        }

        Map<String, Integer> requiredRes = computeRequiredToSalvage(entity);
        float reqCrew = requiredRes.get("crew");
        float reqMachinery = requiredRes.get("heavy_machinery");
        float crew = cargo.getCrew();
        float machinery = cargo.getCommodityQuantity("heavy_machinery");
        float fCrew = Math.max(0f, Math.min(1f, crew / reqCrew));
        float fMachinery = Math.max(0f, Math.min(1f, machinery / reqMachinery));

        float lossValue = reqCrew * fCrew * 5f;
        lossValue += (1f - params.density / params.baseDensity) * 500f;
        lossValue *= 0.5f + random.nextFloat();

        WeightedRandomPicker<String> lossPicker = new WeightedRandomPicker<>(random);
        lossPicker.add("crew", 10f + 100f * (1f - fMachinery));
        lossPicker.add("heavy_machinery", 10f + 100f * fMachinery);

        CargoAPI losses = Global.getFactory().createCargo(true);
        float loss = 0f;
        while (loss < lossValue) {
            String id = lossPicker.pick();
            loss += Global.getSector().getEconomy().getCommoditySpec(id).getBasePrice();
            losses.addCommodity(id, 1f);
        }
        losses.sort();

        int crewLost = losses.getCrew();
        if (crewLost > 0) {
            losses.removeCrew(crewLost);
            crewLost = (int) (crewLost * playerFleet.getStats().getDynamic().getValue("overall_crew_loss_mult"));
            if (crewLost < 1) {
                crewLost = 1;
            }
            losses.addCrew(crewLost);
        }

        int machineryLost = (int) losses.getCommodityQuantity("heavy_machinery");
        if (crewLost > crew) {
            crewLost = (int) crew;
        }
        if (machineryLost > machinery) {
            machineryLost = (int) machinery;
        }

        for (CargoStackAPI stack : losses.getStacksCopy()) {
            cargo.removeCommodity(stack.getCommodityId(), stack.getSize());
        }

        state.crewLost += crewLost;
        state.machineryLost += machineryLost;
    }

    protected void finish(final State state) {
        for (SectorEntityToken target : state.targets) {
            release(target);
        }
        reportHauls(state);

        if (state.crewLost > 0 && state.machineryLost > 0) {
            text.addPara("Accidents during the operation have resulted in the loss of %s crew and %s heavy machinery.",
                    Misc.getHighlightColor(), new String[] { "" + state.crewLost, "" + state.machineryLost });
        } else if (state.crewLost > 0) {
            text.addPara("Accidents during the operation have resulted in the loss of %s crew.",
                    Misc.getHighlightColor(), new String[] { "" + state.crewLost });
        } else if (state.machineryLost > 0) {
            text.addPara("Accidents during the operation have resulted in the loss of %s heavy machinery.",
                    Misc.getHighlightColor(), new String[] { "" + state.machineryLost });
        }

        playerFleet.getStats().addTemporaryModFlat(0.25f, "salvage_ops", "Recent salvage operation",
                SALVAGE_DETECTION_MOD_FLAT, playerFleet.getStats().getDetectedRangeMod());
        Global.getSector().addPing(playerFleet, "noticed_player");

        final InteractionDialogAPI dialog = this.dialog;
        options.clearOptions();
        if (state.loot.isEmpty()) {
            text.addParagraph("Operations conclude with nothing of value found.");
            if (state.xp > 0L) {
                Global.getSector().getPlayerPerson().getStats().addXP(state.xp, text);
            }
            String leave = memory.contains("$salvageLeaveText") ? memory.getString("$salvageLeaveText") : "Leave";
            options.addOption(leave, "defaultLeave");
            options.setShortcut("defaultLeave", 1, false, false, false, true);
        } else {
            dialog.setPromptText("");
            dialog.getVisualPanel().showLoot("Salvaged", state.loot, false, true, true, new CoreInteractionListener() {
                @Override
                public void coreUIDismissed() {
                    dialog.dismiss();
                    dialog.hideTextPanel();
                    dialog.hideVisualPanel();
                    if (state.xp > 0L) {
                        Global.getSector().getPlayerPerson().getStats().addXP(state.xp);
                    }
                }
            });
        }
    }
}
