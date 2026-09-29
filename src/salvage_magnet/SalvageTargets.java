package salvage_magnet;

import java.util.*;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.DefenderDataOverride;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageEntityGeneratorOld;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageGenFromSeed;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.util.Misc;

import salvage_magnet.rulecmd.MagnetScavenge;

public final class SalvageTargets {
    public static final float RADIUS = Global.getSettings().getFloat("magnetScavengeRadius");

    private static final Set<String> BLACKLISTED_HULLS = Set.of(
        "ziggurat",
        "onslaught_mk1"
    );

    private SalvageTargets() {
    }

    public static List<SectorEntityToken> near(CampaignFleetAPI fleet) {
        List<SectorEntityToken> result = fields(fleet);
        result.addAll(derelicts(fleet));
        return result;
    }

    public static List<SectorEntityToken> fields(CampaignFleetAPI fleet) {
        List<SectorEntityToken> result = new ArrayList<>();
        for (CampaignTerrainAPI terrain : fleet.getContainingLocation().getTerrainCopy()) {
            if (terrain.getPlugin() instanceof DebrisFieldTerrainPlugin field
                    && (field.containsEntity(fleet) || isInRange(fleet, terrain))
                    && isGroupableField(field)) {
                result.add(terrain);
            }
        }
        return result;
    }

    public static List<SectorEntityToken> derelicts(CampaignFleetAPI fleet) {
        List<SectorEntityToken> result = new ArrayList<>();
        for (CustomCampaignEntityAPI entity : fleet.getContainingLocation().getCustomEntities()) {
            if (isInRange(fleet, entity) && isGroupableDerelict(entity)) {
                result.add(entity);
            }
        }
        return result;
    }

    public static boolean isGroupableField(DebrisFieldTerrainPlugin field) {
        if (field.isScavenged()) {
            return false;
        }

        DebrisFieldTerrainPlugin.DebrisFieldParams params = field.getParams();
        SalvageEntityGenDataSpec shared = SalvageEntityGeneratorOld.getSalvageSpec(MagnetScavenge.TYPE);
        return !rollsDefenders(field.getEntity(),
            params.minStr * params.density,
            params.maxStr * params.density,
            params.defenderProb * params.density,
            shared.getProbStation(),
            shared.getStationRole(),
            params.defFaction);
    }

    public static boolean isGroupableDerelict(SectorEntityToken entity) {
        if (!(entity instanceof CustomCampaignEntityAPI custom)
                || !(custom.getCustomPlugin() instanceof DerelictShipEntityPlugin plugin)
                || entity.isDiscoverable()
                || entity.hasTag("fading_out_and_expiring")) {
            return false;
        }

        ShipRecoverySpecial.PerShipData ship = plugin.getData().ship;
        if (ship == null || ship.getVariant() == null
                || BLACKLISTED_HULLS.contains(ship.getVariant().getHullSpec().getHullId())) {
            return false;
        }

        SalvageEntityGenDataSpec spec = SalvageEntityGeneratorOld.getSalvageSpec(salvageSpecId(entity));
        return spec != null && !rollsDefenders(entity,
            spec.getMinStr(),
            spec.getMaxStr(),
            spec.getProbDefenders(),
            spec.getProbStation(),
            spec.getStationRole(),
            spec.getDefFaction());
    }

    public static boolean isInRange(CampaignFleetAPI fleet, SectorEntityToken entity) {
        return Misc.getDistance(fleet, entity) < RADIUS;
    }

    private static String salvageSpecId(SectorEntityToken entity) {
        MemoryAPI memory = entity.getMemoryWithoutUpdate();
        String specId = entity.getCustomEntityType();
        return specId == null || memory.contains("$salvageSpecId") ? memory.getString("$salvageSpecId") : specId;
    }

    private static boolean rollsDefenders(SectorEntityToken entity, float minStr, float maxStr, float prob,
            float probStation, String stationRole, String defFaction) {
        MemoryAPI memory = entity.getMemoryWithoutUpdate();
        if (memory.getBoolean("$defenderFleetDefeated")) {
            return false;
        }

        long seed = Misc.getSalvageSeed(entity);
        Random random = Misc.getRandom(seed, 0);
        Random fleetRandom = Misc.getRandom(seed, 1);
        DefenderDataOverride override = memory.get("$salvageDOv") instanceof DefenderDataOverride o ? o : null;
        boolean overridden = override != null;

        float strength = minStr + Math.round((maxStr - minStr) * fleetRandom.nextFloat());
        String factionId = defFaction != null ? defFaction : entity.getFaction().getId();
        if (overridden) {
            strength = override.minStr + Math.round((override.maxStr - override.minStr) * fleetRandom.nextFloat());
            prob = override.probDefenders;
            probStation = override.probStation;
            if (override.defFaction != null) {
                factionId = override.defFaction;
            }
            if (override.stationRole != null) {
                stationRole = override.stationRole;
            }
        }

        SalvageGenFromSeed.SDMParams params = new SalvageGenFromSeed.SDMParams();
        params.entity = entity;
        params.factionId = factionId;
        SalvageGenFromSeed.SalvageDefenderModificationPlugin plugin = Global.getSector().getGenericPlugins()
            .pickPlugin(SalvageGenFromSeed.SalvageDefenderModificationPlugin.class, params);
        if (plugin != null) {
            strength = plugin.getStrength(params, strength, random, overridden);
            prob = plugin.getProbability(params, prob, random, overridden);
        }

        boolean hasStation = fleetRandom.nextFloat() < probStation && stationRole != null;
        return ((int) strength > 0 || hasStation) && random.nextFloat() < prob;
    }
}
