package net.righthandedvoid.infidelity.intimacy; // change to your package

import com.aetherianartificer.townstead.root.LifeStageProgression;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.righthandedvoid.infidelity.EligibilityResult;
import net.righthandedvoid.infidelity.optin.OptIn;

import java.util.Optional;
import java.util.UUID;

/**
 * The single gate every intimate interaction must pass through: villager-initiated,
 * player-initiated, paid, and player-to-player. Server-side only.
 *
 * Call it when a request is made, again when it is accepted, and every tick while
 * an interaction runs. Anything other than OK ends it.
 *
 * Everything fails closed: missing data, exceptions, or unknown states mean "not eligible".
 */
public final class IntimacyRules {

    // ---- Tunables (move these into a NeoForge ModConfigSpec later) ----
    /** MCA reference points: friend = 40, engaged = 50, married = 100. */
    public static final int MIN_HEARTS_NON_PARTNER = 75;
    public static final int MIN_HEARTS_PARTNER = 25;
    /** MCA mood runs -15..15 in normal play (can drop lower from mistreatment). */
    public static final int MIN_MOOD = 0;
    public static final double MAX_DISTANCE = 4.0;
    /** Ticks after taking damage during which someone counts as busy (200 = 10s). */
    public static final int RECENT_HURT_TICKS = 200;

    private IntimacyRules() {}

    // =====================================================================
    // Player <-> villager
    // =====================================================================

    public static EligibilityResult check(ServerPlayer player, VillagerEntityMCA villager) {
        try {
            // 1. Age. Always first, never skippable.
            if (!isAdultVillager(villager)) return EligibilityResult.NOT_ADULT;

            // 1b. Opt-in. Second only to age.
            if (!OptIn.isOptedIn(player)) return EligibilityResult.NOT_OPTED_IN;

            // 2. Basic validity.
            if (!isValidPlayer(player)) return EligibilityResult.INVALID_STATE;
            if (!villager.isAlive() || villager.isRemoved()) return EligibilityResult.INVALID_STATE;

            // 3. Family. Hard block, not configurable.
            if (villager.getRelationships().getFamilyEntry().isRelative(player.getUUID())) {
                return EligibilityResult.RELATED;
            }

            // 4. Proximity.
            if (!closeEnough(player, villager)) return EligibilityResult.TOO_FAR;

            // 5. Busy states.
            VillagerBrain<?> brain = villager.getVillagerBrain();
            if (isBusy(player) || isBusy(villager) || brain.isPanicking() || villager.getTarget() != null) {
                return EligibilityResult.BUSY;
            }

            // 6. Affection. Partners need less, but still some.
            boolean partners = isRomanticPartner(villager, player.getUUID());
            int hearts = brain.getMemoriesForPlayer(player).getHearts();
            int required = partners ? MIN_HEARTS_PARTNER : MIN_HEARTS_NON_PARTNER;
            if (hearts < required) return EligibilityResult.LOW_AFFECTION;

            // 7. Mood.
            if (brain.getMoodValue() < MIN_MOOD) return EligibilityResult.BAD_MOOD;

            return EligibilityResult.OK;
        } catch (Throwable t) {
            // An API changed or data was missing. Fail closed.
            return EligibilityResult.INVALID_STATE;
        }
    }

    // =====================================================================
    // Player <-> player (consent is handled by the request/accept flow)
    // =====================================================================

    public static EligibilityResult check(ServerPlayer a, ServerPlayer b) {
        try {
            if (a == b || a.getUUID().equals(b.getUUID())) return EligibilityResult.INVALID_STATE;

            // Opt-in. Both players, before anything else.
            if (!OptIn.isOptedIn(a)) return EligibilityResult.NOT_OPTED_IN;
            if (!OptIn.isOptedIn(b)) return EligibilityResult.PARTNER_NOT_OPTED_IN;

            if (!isValidPlayer(a) || !isValidPlayer(b)) return EligibilityResult.INVALID_STATE;

            Optional<EntityRelationship> relA = EntityRelationship.of(a);
            if (relA.isEmpty()) return EligibilityResult.INVALID_STATE;
            if (relA.get().getFamilyEntry().isRelative(b.getUUID())) return EligibilityResult.RELATED;

            if (!closeEnough(a, b)) return EligibilityResult.TOO_FAR;
            if (isBusy(a) || isBusy(b)) return EligibilityResult.BUSY;

            return EligibilityResult.OK;
        } catch (Throwable t) {
            return EligibilityResult.INVALID_STATE;
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Three independent checks; all must agree. Fails closed if Townstead's internals change. */
    public static boolean isAdultVillager(VillagerEntityMCA villager) {
        if (villager == null || !villager.isAlive()) return false;
        try {
            if (LifeStageProgression.isPreAdult(villager)) return false;
        } catch (Throwable t) {
            return false;
        }
        if (villager.isBaby()) return false;
        return villager.getAgeState() == AgeState.ADULT;
    }

    private static boolean isRomanticPartner(VillagerEntityMCA villager, UUID playerId) {
        var rel = villager.getRelationships();
        return rel.isMarriedTo(playerId) || rel.isEngagedWith(playerId) || rel.isPromisedTo(playerId);
    }

    private static boolean isValidPlayer(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator();
    }

    private static boolean closeEnough(LivingEntity a, LivingEntity b) {
        return a.level() == b.level() && a.distanceToSqr(b) <= MAX_DISTANCE * MAX_DISTANCE;
    }

    private static boolean isBusy(LivingEntity entity) {
        if (entity.isSleeping() || entity.isPassenger()) return true;
        int lastHurt = entity.getLastHurtByMobTimestamp();
        return lastHurt > 0 && entity.tickCount - lastHurt < RECENT_HURT_TICKS;
    }
}