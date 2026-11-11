package net.righthandedvoid.infidelity.item;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.righthandedvoid.infidelity.MCARInfidelity;
import net.righthandedvoid.infidelity.cycle.CycleTalk;
import net.righthandedvoid.infidelity.protection.BirthControlItem;

/**
 * Using our items on an MCA villager (the journal, the pills).
 *
 * MCA opens its talk menu from interactAt, which runs before the normal interact, so we listen to
 * EntityInteractSpecific (fires before interactAt) as well as EntityInteract. The client always takes the
 * click (it can't know the server's answer); the server either handles it or lets MCA's menu open as usual.
 */
@EventBusSubscriber(modid = MCARInfidelity.MODID)
public final class VillagerItemEvents {

    private VillagerItemEvents() {}

    @SubscribeEvent
    public static void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (handle(event.getEntity(), event.getTarget(), event.getItemStack(), event.getLevel().isClientSide)) {
            cancel(event);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (handle(event.getEntity(), event.getTarget(), event.getItemStack(), event.getLevel().isClientSide)) {
            cancel(event);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static boolean handle(Player player, Entity target, ItemStack stack, boolean client) {
        if (!(target instanceof VillagerEntityMCA villager)) return false;
        boolean journal = stack.is(ModItems.CYCLE_JOURNAL.get());
        boolean pill = stack.is(ModItems.BIRTH_CONTROL.get());
        if (!journal && !pill) return false;
        if (client) return true;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        return journal ? CycleTalk.askVillager(serverPlayer, villager) : BirthControlItem.offerTo(serverPlayer, villager, stack);
    }

    private static void cancel(ICancellableEvent event) {
        event.setCanceled(true);
    }
}
