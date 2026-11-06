package net.righthandedvoid.infidelity.consent; // change to your package

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * One pending request from a player to a villager or another player.
 * Owned and mutated only by ConsentManager, on the server thread.
 */
public final class ConsentRequest {

    public enum Status { PENDING, ACCEPTED, DECLINED, TIMED_OUT, CANCELLED, INVALIDATED }

    public final UUID id = UUID.randomUUID();
    public final UUID initiator;          // always a player for now
    public final UUID target;             // player or villager
    public final boolean targetIsVillager;
    public final ResourceKey<Level> dimension;
    public final long createdTick;

    Status status = Status.PENDING;

    ConsentRequest(UUID initiator, UUID target, boolean targetIsVillager,
                   ResourceKey<Level> dimension, long createdTick) {
        this.initiator = initiator;
        this.target = target;
        this.targetIsVillager = targetIsVillager;
        this.dimension = dimension;
        this.createdTick = createdTick;
    }

    public Status status() {
        return status;
    }

    public boolean involves(UUID uuid) {
        return initiator.equals(uuid) || target.equals(uuid);
    }
}