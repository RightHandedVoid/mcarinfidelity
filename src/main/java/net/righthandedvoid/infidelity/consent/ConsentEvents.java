package net.righthandedvoid.infidelity.consent; // change to your package

import net.righthandedvoid.infidelity.MCARInfidelity; // change to your main mod class
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Hooks ConsentManager into the server lifecycle. */
@EventBusSubscriber(modid = MCARInfidelity.MODID)
public final class ConsentEvents {

    private ConsentEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ConsentManager.tick(event.getServer());
        SessionManager.tick(event.getServer());
        VillagerOffers.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ConsentManager.onLogout(player);
            SessionManager.onLogout(player);
            VillagerOffers.onLogout(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ConsentManager.clear();
        SessionManager.clear();
        VillagerOffers.clear();
    }
}