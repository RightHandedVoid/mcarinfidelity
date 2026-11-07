package net.righthandedvoid.infidelity.consent; // change to your package

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.righthandedvoid.infidelity.InfidelityConfig;
import net.righthandedvoid.infidelity.RelationshipContext;
import net.righthandedvoid.infidelity.intimacy.IntimacyNetwork;
import net.righthandedvoid.infidelity.intimacy.IntimacyRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runs accepted interactions.
 *
 * Every tick the session re-runs IntimacyRules for both participants. If anything stops being true
 * (someone walks away, gets hurt, mood or affection drops, etc.) it ends immediately.
 * Either player can end it at any time (stop key or /intimacy stop). Logging out ends it.
 *
 * Server thread only.
 */
public final class SessionManager {

    // Duration and rewards live in InfidelityConfig.

    public enum EndReason { COMPLETED, STOPPED, INTERRUPTED, DISCONNECTED }

    private static final Map<UUID, IntimacySession> BY_ID = new HashMap<>();
    private static final Map<UUID, UUID> BY_PARTICIPANT = new HashMap<>();

    private SessionManager() {}

    public static boolean isInSession(UUID uuid) {
        return BY_PARTICIPANT.containsKey(uuid);
    }

    // =====================================================================
    // Start / stop
    // =====================================================================

    /** Called only by ConsentManager after a request reaches ACCEPTED. */
    static void start(ConsentRequest req, MinecraftServer server) {
        if (isInSession(req.initiator) || isInSession(req.target)) return;

        ServerPlayer initiator = server.getPlayerList().getPlayer(req.initiator);
        if (initiator == null) return;

        RelationshipContext context = null;
        if (req.targetIsVillager) {
            Entity entity = resolve(server, req.dimension, req.target);
            if (!(entity instanceof VillagerEntityMCA villager)) return;
            context = RelationshipContext.of(villager, initiator);
        }

        IntimacySession session = new IntimacySession(req.initiator, req.target, req.targetIsVillager,
            req.dimension, server.getTickCount(), InfidelityConfig.ticks(InfidelityConfig.SESSION_DURATION_SECONDS), context);
        BY_ID.put(session.id, session);
        BY_PARTICIPANT.put(session.initiator, session.id);
        BY_PARTICIPANT.put(session.partner, session.id);

        sendState(server, session, true);
    }

    /** Either participant ends the session. Always allowed, no conditions. */
    public static boolean stop(ServerPlayer player) {
        UUID id = BY_PARTICIPANT.get(player.getUUID());
        if (id == null) return false;
        IntimacySession session = BY_ID.get(id);
        if (session != null) end(session, EndReason.STOPPED, player.server, player.getUUID());
        return true;
    }

    // =====================================================================
    // Lifecycle (wired up in ConsentEvents)
    // =====================================================================

    static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (IntimacySession session : new ArrayList<>(BY_ID.values())) {
            if (!stillValid(session, server)) {
                end(session, EndReason.INTERRUPTED, server, null);
            } else if (now - session.startTick >= session.durationTicks) {
                end(session, EndReason.COMPLETED, server, null);
            } else {
                holdVillagerStill(session, server);
            }
        }
    }

    static void onLogout(ServerPlayer player) {
        UUID id = BY_PARTICIPANT.get(player.getUUID());
        IntimacySession session = id == null ? null : BY_ID.get(id);
        if (session != null) end(session, EndReason.DISCONNECTED, player.server, player.getUUID());
    }

    static void clear() {
        BY_ID.clear();
        BY_PARTICIPANT.clear();
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private static boolean stillValid(IntimacySession session, MinecraftServer server) {
        ServerPlayer initiator = server.getPlayerList().getPlayer(session.initiator);
        if (initiator == null) return false;

        if (session.partnerIsVillager) {
            Entity entity = resolve(server, session.dimension, session.partner);
            return entity instanceof VillagerEntityMCA villager && IntimacyRules.check(initiator, villager).allowed();
        }
        ServerPlayer partner = server.getPlayerList().getPlayer(session.partner);
        return partner != null && IntimacyRules.check(initiator, partner).allowed();
    }

    /** Keep the villager from wandering off mid-session. MCA's AI may need more than this; adjust if so. */
    private static void holdVillagerStill(IntimacySession session, MinecraftServer server) {
        if (!session.partnerIsVillager) return;
        if (resolve(server, session.dimension, session.partner) instanceof VillagerEntityMCA villager) {
            villager.getNavigation().stop();
            villager.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static void end(IntimacySession session, EndReason reason, MinecraftServer server, UUID endedBy) {
        if (session.ended) return;
        session.ended = true;
        BY_ID.remove(session.id);
        BY_PARTICIPANT.remove(session.initiator, session.id);
        BY_PARTICIPANT.remove(session.partner, session.id);

        sendState(server, session, false);
        notifyEnd(server, session, reason, endedBy);

        if (reason == EndReason.COMPLETED) {
            applyOutcome(session, server);
        }
    }

    /** Consequences of a completed session. Jealousy/gossip from session.context go here later. */
    private static void applyOutcome(IntimacySession session, MinecraftServer server) {
        ServerPlayer initiator = server.getPlayerList().getPlayer(session.initiator);
        if (initiator == null || !session.partnerIsVillager) return;

        if (resolve(server, session.dimension, session.partner) instanceof VillagerEntityMCA villager) {
            var brain = villager.getVillagerBrain();
            brain.getMemoriesForPlayer(initiator).modHearts(InfidelityConfig.HEARTS_ON_COMPLETE.get());
            brain.modifyMoodValue(InfidelityConfig.MOOD_ON_COMPLETE.get());
            // TODO: if session.context shows cheating, hand off to the jealousy/consequences system.
        }
    }

    private static void sendState(MinecraftServer server, IntimacySession session, boolean active) {
        ServerPlayer initiator = server.getPlayerList().getPlayer(session.initiator);
        ServerPlayer partnerPlayer = session.partnerIsVillager ? null : server.getPlayerList().getPlayer(session.partner);

        if (initiator != null) {
            String partnerName = partnerName(server, session, partnerPlayer);
            PacketDistributor.sendToPlayer(initiator,
                new IntimacyNetwork.SessionStatePayload(session.id, active, partnerName));
        }
        if (partnerPlayer != null) {
            String initiatorName = initiator != null ? initiator.getName().getString() : "";
            PacketDistributor.sendToPlayer(partnerPlayer,
                new IntimacyNetwork.SessionStatePayload(session.id, active, initiatorName));
        }
    }

    private static void notifyEnd(MinecraftServer server, IntimacySession session, EndReason reason, UUID endedBy) {
        ServerPlayer initiator = server.getPlayerList().getPlayer(session.initiator);
        ServerPlayer partnerPlayer = session.partnerIsVillager ? null : server.getPlayerList().getPlayer(session.partner);

        for (ServerPlayer player : new ServerPlayer[]{initiator, partnerPlayer}) {
            if (player == null) continue;
            String text = switch (reason) {
                case COMPLETED -> "The moment passes.";
                case STOPPED -> player.getUUID().equals(endedBy) ? "You stopped." : "Your partner stopped.";
                case INTERRUPTED -> "You were interrupted.";
                case DISCONNECTED -> "Your partner left.";
            };
            player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
        }
    }

    private static String partnerName(MinecraftServer server, IntimacySession session, ServerPlayer partnerPlayer) {
        if (partnerPlayer != null) return partnerPlayer.getName().getString();
        Entity entity = resolve(server, session.dimension, session.partner);
        return entity != null ? entity.getName().getString() : "";
    }

    private static Entity resolve(MinecraftServer server, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim, UUID uuid) {
        ServerLevel level = server.getLevel(dim);
        return level == null ? null : level.getEntity(uuid);
    }
}