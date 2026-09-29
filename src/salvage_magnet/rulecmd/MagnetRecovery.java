package salvage_magnet.rulecmd;

import java.util.*;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.util.Misc;

public class MagnetRecovery extends ShipRecoverySpecial {

    public static class Candidate {
        public final SectorEntityToken source;
        public final ShipRecoverySpecialData data;
        public final PerShipData ship;
        public final FleetMemberAPI member;

        public Candidate(SectorEntityToken source, ShipRecoverySpecialData data, PerShipData ship, FleetMemberAPI member) {
            this.source = source;
            this.data = data;
            this.ship = ship;
            this.member = member;
        }

        public boolean requiresStoryPoint() {
            return isStoryPointRecovery(data);
        }
    }

    public static List<Candidate> collect(List<SectorEntityToken> sources) {
        MagnetRecovery recovery = new MagnetRecovery();
        List<Candidate> result = new ArrayList<>();
        for (SectorEntityToken source : sources) {
            if (Misc.getSalvageSpecial(source) instanceof ShipRecoverySpecialData data) {
                recovery.collect(source, data, result);
            } else if (canBeMadeRecoverable(source)) {
                recovery.collect(source, storyPointData(source), result);
            }
        }
        return result;
    }

    public static void detachFromSources(List<Candidate> candidates) {
        for (Candidate candidate : candidates) {
            if (!(Misc.getSalvageSpecial(candidate.source) instanceof ShipRecoverySpecialData data)) {
                continue;
            }

            if (isStoryPointRecovery(data)) {
                Misc.setSalvageSpecial(candidate.source, Misc.getPrevSalvageSpecial(candidate.source));
            } else {
                candidate.source.getMemoryWithoutUpdate().unset("$salvageSpecialData");
            }
        }
    }

    public static void recover(Candidate candidate, CampaignFleetAPI fleet) {
        FleetMemberAPI member = candidate.member;
        candidate.data.ships.remove(candidate.ship);
        member.setShipName(candidate.ship.shipName);
        if (candidate.ship.fleetMemberId != null) {
            member.setId(candidate.ship.fleetMemberId);
        }

        float hull = roll(fleet, "ship_recovery_hull_min", "ship_recovery_hull_max");
        member.getStatus().setHullFraction(Math.max(hull, member.getStatus().getHullFraction()));
        member.getRepairTracker().setCR(roll(fleet, "ship_recovery_cr_min", "ship_recovery_cr_max"));
        fleet.getFleetData().addFleetMember(member);
    }

    public static CargoAPI scuttle(Candidate candidate) {
        CargoAPI cargo = Global.getFactory().createCargo(true);
        if (!candidate.requiresStoryPoint()) {
            new MagnetRecovery().addStuffFromMember(cargo, candidate.member);
        }
        return cargo;
    }

    private static boolean isStoryPointRecovery(ShipRecoverySpecialData data) {
        return Boolean.TRUE.equals(data.storyPointRecovery);
    }

    private static boolean canBeMadeRecoverable(SectorEntityToken source) {
        if (!(source instanceof CustomCampaignEntityAPI custom)
                || !(custom.getCustomPlugin() instanceof DerelictShipEntityPlugin plugin)
                || source.hasTag("unrecoverable")
                || plugin.getData().ship == null) {
            return false;
        }

        ShipVariantAPI variant = plugin.getData().ship.getVariant();
        return variant != null && !Misc.isUnboardable(variant.getHullSpec());
    }

    private static ShipRecoverySpecialData storyPointData(SectorEntityToken source) {
        DerelictShipEntityPlugin plugin = (DerelictShipEntityPlugin) ((CustomCampaignEntityAPI) source).getCustomPlugin();
        ShipRecoverySpecialData data = new ShipRecoverySpecialData(null);
        data.addShip(plugin.getData().ship.clone());
        data.storyPointRecovery = true;
        return data;
    }

    private static float roll(CampaignFleetAPI fleet, String minStat, String maxStat) {
        float min = fleet.getStats().getDynamic().getValue(minStat, 0f);
        float max = fleet.getStats().getDynamic().getValue(maxStat, 0f);
        return (float) Math.random() * (max - min) + min;
    }

    protected void collect(SectorEntityToken source, ShipRecoverySpecialData data, List<Candidate> into) {
        playerFleet = Global.getSector().getPlayerFleet();
        random = Misc.getRandom(Misc.getSalvageSeed(source), 50);
        members.clear();
        for (PerShipData ship : new ArrayList<>(data.ships)) {
            int before = members.size();
            addMember(ship);
            if (members.size() > before) {
                into.add(new Candidate(source, data, ship, members.get(before)));
            }
        }
    }
}
