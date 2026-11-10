package net.righthandedvoid.infidelity.affair;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.righthandedvoid.infidelity.MCARInfidelity;

/** Gossip/evidence/chance ticking, exile enforcement, and villagers of an exiling village refusing you. */
@EventBusSubscriber(modid = MCARInfidelity.MODID)
public final class AffairEvents {

    private AffairEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        long now = server.getTickCount();
        if (now % 20 == 0) AffairManager.tick(server);
        if (now % 40 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) Exile.enforce(server, player);
        }
    }

    /**
     * MCA opens its talk menu from interactAt (EntityInteractSpecific) and trading from mobInteract
     * (EntityInteract). The menu is opened by a server packet, so cancelling on the server is enough.
     */
    @SubscribeEvent
    public static void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (refuse(event.getEntity(), event.getTarget())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (refuse(event.getEntity(), event.getTarget())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    private static boolean refuse(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.Entity target) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(target instanceof VillagerEntityMCA villager)) return false;
        if (!Exile.isExiledFrom(serverPlayer.server, serverPlayer.getUUID(), villager)) return false;
        serverPlayer.displayClientMessage(Component.literal(villager.getName().getString() + " turns away from you.")
            .withStyle(ChatFormatting.DARK_RED), true);
        return true;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Exile.clearRuntime();
    }
}
