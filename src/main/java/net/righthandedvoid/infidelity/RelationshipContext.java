package net.righthandedvoid.infidelity;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.minecraft.server.level.ServerPlayer;

public record RelationshipContext(boolean partners, boolean initiatorCheating, boolean targetCheating) {

    public static RelationshipContext of(VillagerEntityMCA villager, ServerPlayer player) {
        var villagerRel = villager.getRelationships();

        boolean partners = villagerRel.isMarriedTo(player.getUUID())
            || villagerRel.isEngagedWith(player.getUUID())
            || villagerRel.isPromisedTo(player.getUUID());

        boolean villagerCheating = !partners && villagerRel.getPartnerUUID().isPresent();
        boolean playerCheating = !partners && EntityRelationship.of(player)
            .flatMap(EntityRelationship::getPartnerUUID).isPresent();

        return new RelationshipContext(partners, playerCheating, villagerCheating);
    }
}
