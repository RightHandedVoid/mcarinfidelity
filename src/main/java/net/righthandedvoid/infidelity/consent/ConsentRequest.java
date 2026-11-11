package net.righthandedvoid.infidelity.consent; // change to your package

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * One pending request from a player to a villager or another player.
 * Owned and mutated only by ConsentManager, on the server thread.
 */
public final class ConsentRequest {

    /** NOT_HERE: a villager would say yes, just not here. No cooldown, so you can ask again somewhere else. */
    /** NO_PROTECTION: both agreed to protection, but nobody has any and it isn't a safe day. No cooldown either. */
    public enum Status { PENDING, ACCEPTED, DECLINED, NOT_HERE, NO_PROTECTION, TIMED_OUT, CANCELLED, INVALIDATED }

    public final UUID id = UUID.randomUUID();
    public final UUID initiator;          // player, or a villager making an offer
    public final UUID target;             // player or villager (always a player when initiatorIsVillager)
    public final boolean targetIsVillager;
    /** A villager offering to a player. The player answers, exactly like a player-to-player request. */
    public final boolean initiatorIsVillager;
    /** For messages, since a villager initiator isn't in the player list. */
    public final String initiatorName;
    public final ResourceKey<Level> dimension;
    public final long createdTick;
    /** What the initiator asked for. */
    public final boolean protectionRequested;

    Status status = Status.PENDING;
    /** What will actually be used. Set on acceptance: true if either side wanted protection. */
    boolean protectionUsed;

    ConsentRequest(UUID initiator, UUID target, boolean targetIsVillager,
                   ResourceKey<Level> dimension, long createdTick, boolean protectionRequested) {
        this(initiator, target, targetIsVillager, false, "", dimension, createdTick, protectionRequested);
    }

    ConsentRequest(UUID initiator, UUID target, boolean targetIsVillager, boolean initiatorIsVillager,
                   String initiatorName, ResourceKey<Level> dimension, long createdTick, boolean protectionRequested) {
        this.initiator = initiator;
        this.target = target;
        this.targetIsVillager = targetIsVillager;
        this.initiatorIsVillager = initiatorIsVillager;
        this.initiatorName = initiatorName;
        this.dimension = dimension;
        this.createdTick = createdTick;
        this.protectionRequested = protectionRequested;
        this.protectionUsed = protectionRequested;
    }

    public Status status() {
        return status;
    }

    public boolean protectionUsed() {
        return protectionUsed;
    }

    public boolean involves(UUID uuid) {
        return initiator.equals(uuid) || target.equals(uuid);
    }
}