package net.righthandedvoid.infidelity.consent;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MoveState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.righthandedvoid.infidelity.EligibilityResult;
import net.righthandedvoid.infidelity.InfidelityConfig;
import net.righthandedvoid.infidelity.cycle.FertilityCycle;
import net.righthandedvoid.infidelity.intimacy.IntimacyRules;
import net.righthandedvoid.infidelity.intimacy.Privacy;
import net.righthandedvoid.infidelity.optin.OptIn;
import net.righthandedvoid.infidelity.preference.VillagerPreferences;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Villagers taking the first step. Everything here ends in ConsentManager.offerFromVillager, so the player
 * always gets the normal prompt and can say no (or only with protection). Three ways in:
 *
 *   Spontaneous - now and then, an eligible villager nearby who likes you walks over and asks.
 *   Journal     - asking about her cycle near ovulation may lead her to offer.
 *   Escort      - she'd say yes but wants to be at home: she leads the way and asks once you're both there.
 *
 * Villagers who've been told to stay or follow (MCA's move states) are left alone.
 * Server thread only; nothing here is saved, so a restart just forgets in-progress walks.
 */
public final class VillagerOffers {

    private record Walk(UUID villager, UUID player, ResourceKey<Level> dimension, long startTick,
                        boolean escort, boolean playerWantedProtection) {}

    private static final int APPROACH_TIMEOUT_TICKS = 20 * 30;
    private static final double LOSE_TRACK_DISTANCE = 48;

    /** Villagers currently walking, keyed by villager. */
    private static final Map<UUID, Walk> WALKS = new HashMap<>();
    /** Villager -> server tick before which she won't make another spontaneous or journal offer. */
    private static final Map<UUID, Long> VILLAGER_COOLDOWN = new HashMap<>();
    /** Player -> server tick of their next spontaneous-offer roll. */
    private static final Map<UUID, Long> NEXT_ROLL = new HashMap<>();

    private VillagerOffers() {}

    // =====================================================================
    // Entry points
    // =====================================================================

    /** Whether she has a home to go to, isn't already there, and isn't under a stay/follow order. */
    public static boolean canLeadHome(VillagerEntityMCA villager) {
        return freeToMove(villager) && !WALKS.containsKey(villager.getUUID())
            && Privacy.homeBuilding(villager).isPresent() && !Privacy.atHome(villager);
    }

    /** She heads home and will ask the player once they're both there. */
    public static void startEscort(VillagerEntityMCA villager, ServerPlayer player, boolean playerWantedProtection) {
        WALKS.put(villager.getUUID(), new Walk(villager.getUUID(), player.getUUID(), villager.level().dimension(),
            player.server.getTickCount(), true, playerWantedProtection));
        gray(player, villager.getName().getString() + " would rather go back to " + Privacy.possessive(villager)
            + " place. Follow if you like (or /intimacy cancel).");
        walkHome(villager);
    }

    /** Called by the cycle journal after she's shared that she's in her fertile window. */
    public static void afterJournal(ServerPlayer player, VillagerEntityMCA villager) {
        if (!OptIn.allowsOffers(player) || onCooldown(villager, player.server)) return;
        if (!FertilityCycle.phase(villager).fertile()) return;
        if (villager.getRandom().nextDouble() >= InfidelityConfig.JOURNAL_OFFER_CHANCE.get()) return;
        if (!IntimacyRules.check(player, villager).allowed()) return;

        setCooldown(villager, player.server);
        String name = villager.getName().getString();
        if (Privacy.check(villager, player) == Privacy.PlaceResult.OK) {
            gray(player, name + " holds your gaze a little longer than usual...");
            offer(villager, player, false);
        } else if (canLeadHome(villager)) {
            startEscort(villager, player, false);
        }
    }

    /** /intimacy cancel: calls off any walk involving this player. */
    public static boolean cancelFor(ServerPlayer player) {
        boolean any = false;
        for (Walk walk : new ArrayList<>(WALKS.values())) {
            if (walk.player.equals(player.getUUID())) {
                WALKS.remove(walk.villager);
                any = true;
            }
        }
        if (any) gray(player, "Never mind, then.");
        return any;
    }

    /** Debug: start an approach right now, skipping the chance roll and cooldown. */
    public static boolean forceApproach(ServerPlayer player, VillagerEntityMCA villager) {
        if (WALKS.containsKey(villager.getUUID())) return false;
        WALKS.put(villager.getUUID(), new Walk(villager.getUUID(), player.getUUID(), villager.level().dimension(),
            player.server.getTickCount(), false, false));
        return true;
    }

    // =====================================================================
    // Lifecycle (wired up in ConsentEvents)
    // =====================================================================

    static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % 20 != 0) return;

        for (Walk walk : new ArrayList<>(WALKS.values())) tickWalk(walk, server, now);
        VILLAGER_COOLDOWN.values().removeIf(until -> until <= now);

        if (!InfidelityConfig.VILLAGER_OFFERS.get()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) maybeStartApproach(player, now);
    }

    static void onLogout(ServerPlayer player) {
        WALKS.values().removeIf(w -> w.player.equals(player.getUUID()));
        NEXT_ROLL.remove(player.getUUID());
    }

    static void clear() {
        WALKS.clear();
        VILLAGER_COOLDOWN.clear();
        NEXT_ROLL.clear();
    }

    // =====================================================================
    // Spontaneous offers
    // =====================================================================

    private static void maybeStartApproach(ServerPlayer player, long now) {
        Long next = NEXT_ROLL.get(player.getUUID());
        if (next != null && next > now) return;
        NEXT_ROLL.put(player.getUUID(), now + InfidelityConfig.ticks(InfidelityConfig.OFFER_CHECK_SECONDS));

        if (!OptIn.allowsOffers(player) || SessionManager.isInSession(player.getUUID())
            || ConsentManager.hasPending(player.getUUID())) return;
        if (WALKS.values().stream().anyMatch(w -> w.player.equals(player.getUUID()))) return;

        double radius = InfidelityConfig.OFFER_SEARCH_RADIUS.get();
        Optional<VillagerEntityMCA> chosen = player.serverLevel()
            .getEntitiesOfClass(VillagerEntityMCA.class, player.getBoundingBox().inflate(radius), v ->
                freeToMove(v)
                    && !WALKS.containsKey(v.getUUID())
                    && !onCooldown(v, player.server)
                    && !SessionManager.isInSession(v.getUUID())
                    && !ConsentManager.hasPending(v.getUUID())
                    && IntimacyRules.check(player, v, true) == EligibilityResult.OK
                    && Privacy.check(v, player) == Privacy.PlaceResult.OK)
            .stream()
            // The one who likes you most.
            .max(Comparator.comparingInt(v -> v.getVillagerBrain().getMemoriesForPlayer(player).getHearts()));
        if (chosen.isEmpty()) return;

        VillagerEntityMCA villager = chosen.get();
        double chance = InfidelityConfig.SPONTANEOUS_OFFER_CHANCE.get() + FertilityCycle.willingnessModifier(villager) * 0.5;
        if (villager.getRandom().nextDouble() >= chance) return;

        setCooldown(villager, player.server);
        WALKS.put(villager.getUUID(), new Walk(villager.getUUID(), player.getUUID(), villager.level().dimension(),
            now, false, false));
    }

    // =====================================================================
    // Walking (approach or escort)
    // =====================================================================

    private static void tickWalk(Walk walk, MinecraftServer server, long now) {
        ServerPlayer player = server.getPlayerList().getPlayer(walk.player);
        ServerLevel level = server.getLevel(walk.dimension);
        Entity entity = level == null ? null : level.getEntity(walk.villager);
        if (player == null || !(entity instanceof VillagerEntityMCA villager) || player.level() != villager.level()) {
            WALKS.remove(walk.villager);
            return;
        }
        String name = villager.getName().getString();

        int timeout = walk.escort ? InfidelityConfig.ticks(InfidelityConfig.ESCORT_TIMEOUT_SECONDS) : APPROACH_TIMEOUT_TICKS;
        if (now - walk.startTick > timeout) {
            WALKS.remove(walk.villager);
            if (walk.escort) gray(player, name + " stops waiting for you.");
            return;
        }
        if (!freeToMove(villager) || IntimacyRules.check(player, villager, true) != EligibilityResult.OK) {
            WALKS.remove(walk.villager);
            if (walk.escort) gray(player, name + " changes " + Privacy.possessive(villager) + " mind.");
            return;
        }
        if (villager.distanceTo(player) > LOSE_TRACK_DISTANCE) {
            WALKS.remove(walk.villager);
            if (walk.escort) gray(player, "You lost track of " + name + ".");
            return;
        }

        if (walk.escort) {
            if (!Privacy.atHome(villager)) {
                walkHome(villager);
                return;
            }
            // Home. Wait for the player to come close and for nobody else to be watching.
            if (villager.distanceTo(player) > IntimacyRules.MAX_DISTANCE_FOR_OFFERS
                || Privacy.check(villager, player) != Privacy.PlaceResult.OK) {
                villager.getNavigation().stop();
                return;
            }
            WALKS.remove(walk.villager);
            offer(villager, player, walk.playerWantedProtection);
            return;
        }

        // Approach: walk to the player, then ask. Give up quietly if the spot stops suiting her.
        if (Privacy.check(villager, player) != Privacy.PlaceResult.OK) {
            WALKS.remove(walk.villager);
            return;
        }
        if (villager.distanceTo(player) > IntimacyRules.MAX_DISTANCE_FOR_OFFERS) {
            villager.moveTowards(player.blockPosition(), 0.6F, 2);
            return;
        }
        WALKS.remove(walk.villager);
        villager.getNavigation().stop();
        offer(villager, player, false);
    }

    /** Protection: what the player originally asked for (escorts), or whatever she wants. */
    private static void offer(VillagerEntityMCA villager, ServerPlayer player, boolean playerWantedProtection) {
        boolean protection = playerWantedProtection || VillagerPreferences.wantsProtection(villager, player);
        ConsentManager.offerFromVillager(villager, player, protection);
    }

    private static void walkHome(VillagerEntityMCA villager) {
        Optional<GlobalPos> home = villager.getResidency().getHome();
        if (home.isEmpty()) return;
        BlockPos pos = home.get().pos();
        villager.moveTowards(pos, 0.6F, 1);
    }

    private static boolean freeToMove(VillagerEntityMCA villager) {
        try {
            return villager.getVillagerBrain().getMoveState() == MoveState.MOVE;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean onCooldown(VillagerEntityMCA villager, MinecraftServer server) {
        Long until = VILLAGER_COOLDOWN.get(villager.getUUID());
        return until != null && until > server.getTickCount();
    }

    private static void setCooldown(VillagerEntityMCA villager, MinecraftServer server) {
        VILLAGER_COOLDOWN.put(villager.getUUID(),
            (long) server.getTickCount() + InfidelityConfig.OFFER_COOLDOWN_MINUTES.get() * 60L * 20L);
    }

    private static void gray(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }
}
