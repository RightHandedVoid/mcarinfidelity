package net.righthandedvoid.infidelity.consent; // change to your package

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.righthandedvoid.infidelity.RelationshipContext;

import java.util.UUID;

/** One active interaction between a player and a villager or another player. Owned by SessionManager. */
public final class IntimacySession {

    public final UUID id = UUID.randomUUID();
    public final UUID initiator;          // always a player for now
    public final UUID partner;            // player or villager
    public final boolean partnerIsVillager;
    public final ResourceKey<Level> dimension;
    public final long startTick;
    public final int durationTicks;
    /** Captured at the start so consequences reflect the situation as it was. Null for player-player for now. */
    public final RelationshipContext context;

    boolean ended;

    IntimacySession(UUID initiator, UUID partner, boolean partnerIsVillager, ResourceKey<Level> dimension,
                    long startTick, int durationTicks, RelationshipContext context) {
        this.initiator = initiator;
        this.partner = partner;
        this.partnerIsVillager = partnerIsVillager;
        this.dimension = dimension;
        this.startTick = startTick;
        this.durationTicks = durationTicks;
        this.context = context;
    }

    public boolean involves(UUID uuid) {
        return initiator.equals(uuid) || partner.equals(uuid);
    }
}