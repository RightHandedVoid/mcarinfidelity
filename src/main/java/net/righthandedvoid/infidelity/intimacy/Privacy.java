package net.righthandedvoid.infidelity.intimacy;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.righthandedvoid.infidelity.InfidelityConfig;
import net.righthandedvoid.infidelity.preference.PrivacyPreference;
import net.righthandedvoid.infidelity.preference.VillagerPreferences;

import java.util.List;
import java.util.Optional;

/**
 * Checks a villager's place preference against where she actually is. Server side only.
 *
 * This is the villager's comfort, not a rule: it's checked when she decides on a request (and, if
 * configured, while a session runs), not in IntimacyRules. The onlooker search here is also what
 * witnessed discovery will build on later.
 */
public final class Privacy {

    public enum PlaceResult { OK, WANTS_HOME, WANTS_INDOORS, WATCHED }

    private Privacy() {}

    public static PlaceResult check(VillagerEntityMCA villager, LivingEntity partner) {
        try {
            PrivacyPreference pref = VillagerPreferences.privacyPreference(villager);
            if (pref == PrivacyPreference.ANYWHERE) return PlaceResult.OK;

            if (pref == PrivacyPreference.HOME && homeBuilding(villager).isPresent()) {
                if (!atHome(villager)) return PlaceResult.WANTS_HOME;
            } else if (!isSheltered(villager)) {
                // PRIVATE, or HOME without a home to go to.
                return PlaceResult.WANTS_INDOORS;
            }

            if (!onlookers(villager, partner).isEmpty()) return PlaceResult.WATCHED;
            return PlaceResult.OK;
        } catch (Throwable t) {
            return PlaceResult.WANTS_INDOORS;   // fail toward "not here"
        }
    }

    /** Players and MCA villagers (other than the two involved) who can see either of them. */
    public static List<LivingEntity> onlookers(LivingEntity a, LivingEntity b) {
        if (!(a.level() instanceof ServerLevel level)) return List.of();
        double radius = InfidelityConfig.ONLOOKER_RADIUS.get();
        return level.getEntitiesOfClass(LivingEntity.class, a.getBoundingBox().inflate(radius), e ->
            e != a && e != b
                && e.isAlive() && !e.isSpectator() && !e.isSleeping()
                && (e instanceof Player || e instanceof VillagerEntityMCA)
                && (e.hasLineOfSight(a) || e.hasLineOfSight(b)));
    }

    public static boolean atHome(VillagerEntityMCA villager) {
        return homeBuilding(villager).map(b -> b.containsPos(villager.blockPosition())).orElse(false);
    }

    /** The MCA building that contains her home bed, in her current dimension. */
    public static Optional<Building> homeBuilding(VillagerEntityMCA villager) {
        Optional<GlobalPos> home = villager.getResidency().getHome();
        if (home.isEmpty() || !home.get().dimension().equals(villager.level().dimension())) return Optional.empty();
        return villager.getResidency().getHomeVillage().flatMap(v -> v.getBuildingAt(home.get().pos()));
    }

    /** Inside any MCA building of her village, or at least under a roof. */
    public static boolean isSheltered(VillagerEntityMCA villager) {
        boolean inBuilding = villager.getResidency().getHomeVillage()
            .flatMap(v -> v.getBuildingAt(villager.blockPosition())).isPresent();
        return inBuilding || !villager.level().canSeeSky(villager.blockPosition().above());
    }

    public static String possessive(VillagerEntityMCA villager) {
        Gender g = villager.getGenetics().getGender();
        return g == Gender.FEMALE ? "her" : g == Gender.MALE ? "his" : "their";
    }
}
