package net.righthandedvoid.infidelity.pregnancy;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.righthandedvoid.infidelity.MCARInfidelity;

/**
 * Advances pregnancies. Time only passes while the carrier is loaded (villagers) or online (players),
 * the same way MCA's own villager pregnancies and baby items work.
 */
@EventBusSubscriber(modid = MCARInfidelity.MODID)
public final class PregnancyEvents {

    private PregnancyEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) PregnancyManager.tick(player);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof VillagerEntityMCA villager && !villager.level().isClientSide) {
            PregnancyManager.tick(villager);
        }
    }
}
