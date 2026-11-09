package net.righthandedvoid.infidelity.cycle;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.righthandedvoid.infidelity.MCARInfidelity;
import net.righthandedvoid.infidelity.item.ModItems;

/**
 * Right-clicking an MCA villager while holding the journal. This event fires before the villager's own
 * interaction (which would open MCA's menu), so we can take the click.
 */
@EventBusSubscriber(modid = MCARInfidelity.MODID)
public final class CycleEvents {

    private CycleEvents() {}

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getItemStack().is(ModItems.CYCLE_JOURNAL.get())) return;
        if (!(event.getTarget() instanceof VillagerEntityMCA villager)) return;

        if (event.getLevel().isClientSide) {
            // The client can't know the server's answer (opt-in etc.), so it just stops MCA's menu from
            // opening locally; the server either handles it or lets MCA's interaction through.
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && CycleTalk.askVillager(player, villager)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
