package net.righthandedvoid.infidelity.consent; // change to your package

import net.righthandedvoid.infidelity.InfidelityConfig;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.righthandedvoid.infidelity.EligibilityResult;
import net.righthandedvoid.infidelity.intimacy.IntimacyNetwork;
import net.righthandedvoid.infidelity.intimacy.IntimacyRules;
import net.righthandedvoid.infidelity.intimacy.Privacy;
import net.righthandedvoid.infidelity.cycle.FertilityCycle;
import net.righthandedvoid.infidelity.optin.OptIn;
import net.righthandedvoid.infidelity.affair.Grievances;
import net.righthandedvoid.infidelity.preference.VillagerPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks consent requests on the server.
 *
 * Flow: request() -> PENDING -> (respond / villager decision / timeout / cancel / logout / invalidated)
 * -> final status. Eligibility is checked when the request is made, once per second while it is
 * pending, and again at the moment of acceptance. Nothing starts unless the final status is ACCEPTED.
 *
 * Server thread only. The client can only name a target or answer a request ID it was sent;
 * it can never declare a request accepted.
 *
 * Protection is part of consent: the initiator picks protected or unprotected, and the responder
 * (player or villager) can add protection but never remove it. If either side wants it, it's used.
 */
public final class ConsentManager {

    // ---- Tunables ----
    // Timeouts and cooldowns live in InfidelityConfig.
    public static final int REVALIDATE_INTERVAL = 20;           // re-check pending requests every 1s

    public enum Outcome { SENT, NOT_ELIGIBLE, INVALID_TARGET, ALREADY_REQUESTING, TARGET_BUSY, ON_COOLDOWN, IN_SESSION }

    public record RequestResult(Outcome outcome, EligibilityResult eligibility) {}

    private record PairKey(UUID initiator, UUID target) {}

    private static final Map<UUID, ConsentRequest> BY_ID = new HashMap<>();
    private static final Map<UUID, UUID> PENDING_BY_TARGET = new HashMap<>();
    private static final Map<UUID, UUID> PENDING_BY_INITIATOR = new HashMap<>();
    private static final Map<PairKey, Long> COOLDOWNS = new HashMap<>();

    private ConsentManager() {}

    // =====================================================================
    // Public API
    // =====================================================================

    public static RequestResult request(ServerPlayer initiator, Entity target, boolean protectionRequested) {
        MinecraftServer server = initiator.server;
        long now = server.getTickCount();

        if (target == null || target == initiator) return result(Outcome.INVALID_TARGET);
        if (!(target instanceof VillagerEntityMCA) && !(target instanceof ServerPlayer)) {
            return result(Outcome.INVALID_TARGET);
        }

        if (SessionManager.isInSession(initiator.getUUID()) || SessionManager.isInSession(target.getUUID())) {
            return result(Outcome.IN_SESSION);
        }

        EligibilityResult eligibility = checkEligibility(initiator, target);
        if (!eligibility.allowed()) return new RequestResult(Outcome.NOT_ELIGIBLE, eligibility);

        Long cooldownUntil = COOLDOWNS.get(new PairKey(initiator.getUUID(), target.getUUID()));
        if (cooldownUntil != null && cooldownUntil > now) return result(Outcome.ON_COOLDOWN);
        if (PENDING_BY_INITIATOR.containsKey(initiator.getUUID())) return result(Outcome.ALREADY_REQUESTING);
        if (PENDING_BY_TARGET.containsKey(target.getUUID())) return result(Outcome.TARGET_BUSY);

        ConsentRequest req = new ConsentRequest(initiator.getUUID(), target.getUUID(),
            target instanceof VillagerEntityMCA, initiator.level().dimension(), now, protectionRequested);
        BY_ID.put(req.id, req);
        PENDING_BY_TARGET.put(req.target, req.id);
        PENDING_BY_INITIATOR.put(req.initiator, req.id);

        if (target instanceof ServerPlayer targetPlayer) {
            PacketDistributor.sendToPlayer(targetPlayer,
                new IntimacyNetwork.ConsentPromptPayload(req.id, initiator.getName().getString(), protectionRequested));
            gray(initiator, "Request sent to " + targetPlayer.getName().getString() + " (" + protectionLabel(protectionRequested) + ").");
        } else {
            gray(initiator, "You ask " + target.getName().getString() + " (" + protectionLabel(protectionRequested) + ")...");
        }
        return result(Outcome.SENT);
    }

    /**
     * A villager offers to a player. The player gets the same prompt as a player-to-player request and can
     * accept, accept only with protection, or decline. Declining costs nothing with her.
     * Called by VillagerOffers (spontaneous offers, the journal, arriving home after leading the way).
     */
    public static RequestResult offerFromVillager(VillagerEntityMCA villager, ServerPlayer player, boolean protection) {
        MinecraftServer server = player.server;
        long now = server.getTickCount();

        if (!OptIn.allowsOffers(player)) return result(Outcome.INVALID_TARGET);
        if (SessionManager.isInSession(player.getUUID()) || SessionManager.isInSession(villager.getUUID())) {
            return result(Outcome.IN_SESSION);
        }

        EligibilityResult eligibility = IntimacyRules.check(player, villager);
        if (!eligibility.allowed()) return new RequestResult(Outcome.NOT_ELIGIBLE, eligibility);

        Long cooldownUntil = COOLDOWNS.get(new PairKey(villager.getUUID(), player.getUUID()));
        if (cooldownUntil != null && cooldownUntil > now) return result(Outcome.ON_COOLDOWN);
        if (hasPending(villager.getUUID())) return result(Outcome.ALREADY_REQUESTING);
        if (hasPending(player.getUUID())) return result(Outcome.TARGET_BUSY);

        ConsentRequest req = new ConsentRequest(villager.getUUID(), player.getUUID(), false, true,
            villager.getName().getString(), villager.level().dimension(), now, protection);
        BY_ID.put(req.id, req);
        PENDING_BY_TARGET.put(req.target, req.id);
        PENDING_BY_INITIATOR.put(req.initiator, req.id);

        PacketDistributor.sendToPlayer(player,
            new IntimacyNetwork.ConsentPromptPayload(req.id, req.initiatorName, protection));
        return result(Outcome.SENT);
    }

    /** Whether this player or villager is on either end of a pending request. */
    public static boolean hasPending(UUID uuid) {
        return PENDING_BY_INITIATOR.containsKey(uuid) || PENDING_BY_TARGET.containsKey(uuid);
    }

    /** A player answers a specific request. Ignored unless that request is pending and addressed to them. */
    public static boolean respond(ServerPlayer responder, UUID requestId, ConsentResponse response) {
        UUID pendingId = PENDING_BY_TARGET.get(responder.getUUID());
        if (pendingId == null || !pendingId.equals(requestId)) return false;
        ConsentRequest req = BY_ID.get(requestId);
        if (req == null || req.targetIsVillager) return false;

        if (response == ConsentResponse.DECLINE) {
            finish(req, ConsentRequest.Status.DECLINED, responder.server);
            return true;
        }

        // The responder can add protection, never remove it.
        req.protectionUsed = req.protectionRequested || response == ConsentResponse.ACCEPT_WITH_PROTECTION;

        // Re-check at the moment of acceptance.
        boolean stillOk;
        if (req.initiatorIsVillager) {
            stillOk = villagerOfferStillOk(req, responder, responder.server);
        } else {
            ServerPlayer initiator = responder.server.getPlayerList().getPlayer(req.initiator);
            stillOk = initiator != null && IntimacyRules.check(initiator, responder).allowed();
        }
        if (!stillOk) {
            finish(req, ConsentRequest.Status.INVALIDATED, responder.server);
        } else {
            finish(req, ConsentRequest.Status.ACCEPTED, responder.server);
        }
        return true;
    }

    /** Answer whatever request is currently addressed to this player (used by the chat commands). */
    public static boolean respond(ServerPlayer responder, ConsentResponse response) {
        UUID pendingId = PENDING_BY_TARGET.get(responder.getUUID());
        return pendingId != null && respond(responder, pendingId, response);
    }

    /** The initiator withdraws their pending request. */
    public static boolean cancel(ServerPlayer initiator) {
        UUID id = PENDING_BY_INITIATOR.get(initiator.getUUID());
        if (id == null) return false;
        ConsentRequest req = BY_ID.get(id);
        if (req != null) finish(req, ConsentRequest.Status.CANCELLED, initiator.server);
        return true;
    }

    /** Player-facing text for a request outcome, or null if the manager already gave feedback. */
    public static String describe(RequestResult result) {
        return switch (result.outcome()) {
            case SENT -> null;
            case NOT_ELIGIBLE -> switch (result.eligibility()) {
                case NOT_OPTED_IN -> "You haven't opted in. Use /intimacy optin to learn more.";
                // Don't reveal another player's opt-in setting; to the asker it looks like any other "no".
                case PARTNER_NOT_OPTED_IN -> "That isn't possible.";
                default -> "Not possible right now (" + result.eligibility() + ").";
            };
            case INVALID_TARGET -> "That isn't a valid target.";
            case ALREADY_REQUESTING -> "You already have a pending request. Use /intimacy cancel first.";
            case TARGET_BUSY -> "They already have a pending request.";
            case ON_COOLDOWN -> "They recently declined. Give it some time.";
            case IN_SESSION -> "Not possible right now.";
        };
    }

    // =====================================================================
    // Lifecycle (wired up in ConsentEvents)
    // =====================================================================

    static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        boolean revalidate = now % REVALIDATE_INTERVAL == 0;

        for (ConsentRequest req : new ArrayList<>(BY_ID.values())) {
            long age = now - req.createdTick;
            if (req.targetIsVillager) {
                if (age >= InfidelityConfig.ticks(InfidelityConfig.VILLAGER_THINK_SECONDS)) decideForVillager(req, server);
            } else if (age >= InfidelityConfig.ticks(InfidelityConfig.REQUEST_TIMEOUT_SECONDS)) {
                finish(req, ConsentRequest.Status.TIMED_OUT, server);
            } else if (revalidate && !stillEligible(req, server)) {
                finish(req, ConsentRequest.Status.INVALIDATED, server);
            }
        }
        COOLDOWNS.values().removeIf(until -> until <= now);
    }

    static void onLogout(ServerPlayer player) {
        for (ConsentRequest req : new ArrayList<>(BY_ID.values())) {
            if (req.involves(player.getUUID())) finish(req, ConsentRequest.Status.CANCELLED, player.server);
        }
    }

    static void clear() {
        BY_ID.clear();
        PENDING_BY_TARGET.clear();
        PENDING_BY_INITIATOR.clear();
        COOLDOWNS.clear();
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private static boolean stillEligible(ConsentRequest req, MinecraftServer server) {
        ServerPlayer target = server.getPlayerList().getPlayer(req.target);
        if (req.initiatorIsVillager) return target != null && villagerOfferStillOk(req, target, server);
        ServerPlayer initiator = server.getPlayerList().getPlayer(req.initiator);
        return initiator != null && target != null && IntimacyRules.check(initiator, target).allowed();
    }

    /** A villager's offer stands while she's eligible and still comfortable with where you are. */
    private static boolean villagerOfferStillOk(ConsentRequest req, ServerPlayer player, MinecraftServer server) {
        ServerLevel level = server.getLevel(req.dimension);
        Entity entity = level == null ? null : level.getEntity(req.initiator);
        return entity instanceof VillagerEntityMCA villager
            && IntimacyRules.check(player, villager).allowed()
            && Privacy.check(villager, player) == Privacy.PlaceResult.OK;
    }

    private static void decideForVillager(ConsentRequest req, MinecraftServer server) {
        ServerPlayer initiator = server.getPlayerList().getPlayer(req.initiator);
        ServerLevel level = server.getLevel(req.dimension);
        Entity entity = level == null ? null : level.getEntity(req.target);

        if (initiator == null || !(entity instanceof VillagerEntityMCA villager)) {
            finish(req, ConsentRequest.Status.INVALIDATED, server);
            return;
        }

        EligibilityResult eligibility = IntimacyRules.check(initiator, villager);
        if (!eligibility.allowed()) {
            initiator.sendSystemMessage(villagerDeclineMessage(villager, eligibility));
            finish(req, ConsentRequest.Status.DECLINED, server);
            return;
        }

        // Still hurt from being cheated on (by you, or with you): a firm no, with the usual cooldown.
        if (Grievances.refusesIntimacy(villager, initiator.getUUID())) {
            gray(initiator, villager.getName().getString() + " isn't ready to forgive you.");
            finish(req, ConsentRequest.Status.DECLINED, server);
            return;
        }

        // Eligible villagers still don't always say yes. More hearts = more likely.
        // TODO: factor in personality, interaction fatigue, time of day, etc.
        int hearts = villager.getVillagerBrain().getMemoriesForPlayer(initiator).getHearts();
        // A little more willing around ovulation, a little less while menstruating.
        double chance = Math.min(0.95, 0.5 + hearts / 200.0 + FertilityCycle.willingnessModifier(villager));

        // Her place preference: "not here" isn't a "no", so no cooldown.
        Privacy.PlaceResult place = Privacy.check(villager, initiator);
        if (place != Privacy.PlaceResult.OK) {
            // If home would do and she'd say yes, she offers to lead the way and asks again once you're both there.
            boolean homeWouldDo = place == Privacy.PlaceResult.WANTS_HOME || place == Privacy.PlaceResult.WANTS_INDOORS;
            if (homeWouldDo && VillagerOffers.canLeadHome(villager) && villager.getRandom().nextDouble() < chance) {
                VillagerOffers.startEscort(villager, initiator, req.protectionRequested);
            } else {
                gray(initiator, notHereText(villager, place));
            }
            finish(req, ConsentRequest.Status.NOT_HERE, server);
            return;
        }

        if (villager.getRandom().nextDouble() < chance) {
            // Her own view on protection, based on her lasting "wants children" preference.
            req.protectionUsed = req.protectionRequested || VillagerPreferences.wantsProtection(villager, initiator);
            finish(req, ConsentRequest.Status.ACCEPTED, server);
        } else {
            gray(initiator, villager.getName().getString() + " gently turns you down this time.");
            finish(req, ConsentRequest.Status.DECLINED, server);
        }
    }

    private static void finish(ConsentRequest req, ConsentRequest.Status status, MinecraftServer server) {
        if (req.status != ConsentRequest.Status.PENDING) return;
        req.status = status;
        BY_ID.remove(req.id);
        PENDING_BY_TARGET.remove(req.target, req.id);
        PENDING_BY_INITIATOR.remove(req.initiator, req.id);

        if (status == ConsentRequest.Status.DECLINED) {
            COOLDOWNS.put(new PairKey(req.initiator, req.target), (long) server.getTickCount() + InfidelityConfig.ticks(InfidelityConfig.DECLINE_COOLDOWN_SECONDS));
        }

        ServerPlayer targetPlayer = req.targetIsVillager ? null : server.getPlayerList().getPlayer(req.target);
        if (targetPlayer != null) {
            // Close the prompt on the target's client, however the request ended.
            PacketDistributor.sendToPlayer(targetPlayer, new IntimacyNetwork.ConsentClosedPayload(req.id));
        }

        notifyPlayers(req, status, server, targetPlayer);

        if (status == ConsentRequest.Status.ACCEPTED) {
            onAccepted(req, server);
        }
    }

    private static void onAccepted(ConsentRequest req, MinecraftServer server) {
        SessionManager.start(req, server);
    }

    private static void notifyPlayers(ConsentRequest req, ConsentRequest.Status status,
                                      MinecraftServer server, ServerPlayer targetPlayer) {
        if (req.initiatorIsVillager) {
            if (targetPlayer == null) return;
            String name = req.initiatorName;
            String text = switch (status) {
                case ACCEPTED -> "You accepted " + name + "'s offer (" + protectionLabel(req.protectionUsed) + ").";
                case DECLINED, NOT_HERE -> "You declined " + name + "'s offer.";
                case TIMED_OUT -> name + "'s offer passed.";
                case CANCELLED -> name + "'s offer was withdrawn.";
                case INVALIDATED -> name + "'s offer is off.";
                case PENDING -> null;
            };
            if (text != null) gray(targetPlayer, text);
            return;
        }

        ServerPlayer initiator = server.getPlayerList().getPlayer(req.initiator);
        String initiatorName = initiator != null ? initiator.getName().getString() : "They";

        if (req.targetIsVillager) {
            // Villager declines already sent their own message.
            if (initiator == null) return;
            switch (status) {
                case ACCEPTED -> gray(initiator, acceptedText("They said yes", req));
                case CANCELLED -> gray(initiator, "Request cancelled.");
                case INVALIDATED -> gray(initiator, "Request no longer possible.");
                default -> {}
            }
            return;
        }

        String targetName = targetPlayer != null ? targetPlayer.getName().getString() : "They";
        String toInitiator = switch (status) {
            case ACCEPTED -> acceptedText(targetName + " accepted", req);
            case DECLINED, NOT_HERE -> targetName + " declined your request.";
            case TIMED_OUT -> "Your request to " + targetName + " expired.";
            case CANCELLED -> "Request cancelled.";
            case INVALIDATED -> "Your request to " + targetName + " is no longer possible.";
            case PENDING -> null;
        };
        String toTarget = switch (status) {
            case ACCEPTED -> "You accepted " + initiatorName + "'s request (" + protectionLabel(req.protectionUsed) + ").";
            case DECLINED, NOT_HERE -> "You declined " + initiatorName + "'s request.";
            case TIMED_OUT -> initiatorName + "'s request expired.";
            case CANCELLED -> initiatorName + "'s request was cancelled.";
            case INVALIDATED -> initiatorName + "'s request is no longer possible.";
            case PENDING -> null;
        };

        if (initiator != null && toInitiator != null) gray(initiator, toInitiator);
        if (targetPlayer != null && toTarget != null) gray(targetPlayer, toTarget);
    }

    /** "X accepted, but only with protection." when the responder added it; otherwise states what's used. */
    private static String acceptedText(String prefix, ConsentRequest req) {
        if (req.protectionUsed && !req.protectionRequested) return prefix + ", but only with protection.";
        return prefix + " (" + protectionLabel(req.protectionUsed) + ").";
    }

    private static String notHereText(VillagerEntityMCA villager, Privacy.PlaceResult place) {
        String name = villager.getName().getString();
        return switch (place) {
            case WANTS_HOME -> name + " would rather wait until you're at " + Privacy.possessive(villager) + " home.";
            case WANTS_INDOORS -> name + " would rather go somewhere more private.";
            case WATCHED -> name + " glances around. Not with someone watching.";
            case OK -> name + " declines.";
        };
    }

    public static String protectionLabel(boolean protection) {
        return protection ? "with protection" : "without protection";
    }

    private static Component villagerDeclineMessage(VillagerEntityMCA villager, EligibilityResult reason) {
        String name = villager.getName().getString();
        String text = switch (reason) {
            case LOW_AFFECTION -> name + " isn't interested.";
            case BAD_MOOD -> name + " isn't in the mood right now.";
            case BUSY -> name + " is busy right now.";
            case TOO_FAR -> name + " is too far away.";
            default -> name + " declines.";
        };
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static EligibilityResult checkEligibility(ServerPlayer initiator, Entity target) {
        if (target instanceof VillagerEntityMCA villager) return IntimacyRules.check(initiator, villager);
        if (target instanceof ServerPlayer player) return IntimacyRules.check(initiator, player);
        return EligibilityResult.INVALID_STATE;
    }

    private static void gray(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    private static RequestResult result(Outcome outcome) {
        return new RequestResult(outcome, null);
    }
}