package net.righthandedvoid.infidelity.client; // change to your package

import com.mojang.blaze3d.platform.InputConstants;
import net.righthandedvoid.infidelity.MCARInfidelity; // change to your main mod class
import net.righthandedvoid.infidelity.intimacy.IntimacyNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.righthandedvoid.infidelity.consent.ConsentResponse;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * Client side of the consent flow: keybinds, the incoming prompt, and packet handlers.
 * Nothing here decides anything; it only asks the server and shows what the server sends.
 */
public final class ClientConsent {

    public static final String CATEGORY = "key.categories." + MCARInfidelity.MODID;

    public static final KeyMapping REQUEST_KEY = new KeyMapping("key." + MCARInfidelity.MODID + ".request",
        KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping REVIEW_KEY = new KeyMapping("key." + MCARInfidelity.MODID + ".review",
        KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    /** Active session, if any, and timing for the fade overlay. */
    private static UUID sessionId;
    private static String sessionPartner = "";
    private static boolean sessionProtected;
    /** True when the scene plays instead of fading to black. Animation playback hooks in here later. */
    private static boolean sessionShowScene;
    private static long fadeInStart;
    private static long fadeOutStart;
    private static final long FADE_MS = 1000;

    /** The request currently addressed to this player, if any. */
    private static UUID pendingId;
    private static String pendingFrom;
    private static boolean pendingProtection;

    private ClientConsent() {}

    // ---- Packet handlers (called from IntimacyNetwork on the client thread) ----

    public static void onPrompt(IntimacyNetwork.ConsentPromptPayload payload) {
        pendingId = payload.requestId();
        pendingFrom = payload.initiatorName();
        pendingProtection = payload.protection();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(pendingFrom + " is asking to be intimate with you ("
                    + (pendingProtection ? "with" : "without") + " protection). Press ")
                .append(REVIEW_KEY.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.YELLOW))
                .append(" to respond."), false);
        }
    }

    public static void onClosed(IntimacyNetwork.ConsentClosedPayload payload) {
        if (payload.requestId().equals(pendingId)) {
            pendingId = null;
            pendingFrom = null;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ConsentPromptScreen screen && screen.requestId().equals(payload.requestId())) {
                screen.onClose();
            }
        }
    }

    public static void onSessionState(IntimacyNetwork.SessionStatePayload payload) {
        if (payload.active()) {
            sessionId = payload.sessionId();
            sessionPartner = payload.partnerName();
            sessionProtected = payload.protection();
            sessionShowScene = payload.showScene();
            fadeInStart = System.currentTimeMillis();
            fadeOutStart = 0;
        } else if (payload.sessionId().equals(sessionId)) {
            sessionId = null;
            // Only fade back in if we faded out in the first place.
            fadeOutStart = sessionShowScene ? 0 : System.currentTimeMillis();
        }
    }

    static void sendResponse(UUID requestId, ConsentResponse response) {
        PacketDistributor.sendToServer(new IntimacyNetwork.ConsentResponsePayload(requestId, response));
    }

    // ---- Event wiring ----

    @EventBusSubscriber(modid = MCARInfidelity.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(REQUEST_KEY);
            event.register(REVIEW_KEY);
        }

        /** Fade-to-black while a session is active, with a reminder of how to stop. */
        @SubscribeEvent
        public static void registerOverlay(RegisterGuiLayersEvent event) {
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(MCARInfidelity.MODID, "session_fade"),
                (graphics, deltaTracker) -> {
                    if (sessionId != null && sessionShowScene) {
                        // Scene mode: no fade, just the stop reminder at the top of the screen.
                        // TODO: start/stop the scene's animation here once animations exist.
                        Minecraft mc = Minecraft.getInstance();
                        graphics.drawCenteredString(mc.font,
                            Component.literal("With " + sessionPartner + (sessionProtected ? " (protected)" : " (unprotected)")
                                + ". Press ").append(REVIEW_KEY.getTranslatedKeyMessage()).append(" to stop at any time."),
                            graphics.guiWidth() / 2, 8, 0xFFFFFF);
                        return;
                    }
                    long now = System.currentTimeMillis();
                    float alpha;
                    if (sessionId != null) {
                        alpha = Mth.clamp((now - fadeInStart) / (float) FADE_MS, 0f, 1f);
                    } else if (fadeOutStart > 0) {
                        alpha = 1f - Mth.clamp((now - fadeOutStart) / (float) FADE_MS, 0f, 1f);
                        if (alpha <= 0f) fadeOutStart = 0;
                    } else {
                        return;
                    }
                    if (alpha <= 0f) return;

                    int w = graphics.guiWidth();
                    int h = graphics.guiHeight();
                    int a = (int) (alpha * 0.95f * 255);
                    graphics.fill(0, 0, w, h, a << 24);

                    if (sessionId != null && alpha >= 1f) {
                        Minecraft mc = Minecraft.getInstance();
                        graphics.drawCenteredString(mc.font, "With " + sessionPartner
                            + (sessionProtected ? " (protected)" : " (unprotected)"), w / 2, h / 2 - 10, 0xFFFFFF);
                        graphics.drawCenteredString(mc.font,
                            Component.literal("Press ").append(REVIEW_KEY.getTranslatedKeyMessage())
                                .append(" to stop at any time."),
                            w / 2, h / 2 + 4, 0xAAAAAA);
                    }
                });
        }
    }

    @EventBusSubscriber(modid = MCARInfidelity.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                sessionId = null;   // reset on disconnect
                pendingId = null;
                return;
            }

            while (REQUEST_KEY.consumeClick()) {
                Entity target = mc.crosshairPickEntity;
                if (target instanceof Player || target instanceof VillagerEntityMCA) {
                    // Pick protection first; the screen sends the request.
                    mc.setScreen(new RequestScreen(target.getId(), target.getName().getString()));
                } else if (target != null) {
                    mc.player.displayClientMessage(Component.literal("That isn't a valid target.")
                        .withStyle(ChatFormatting.GRAY), true);
                }
            }

            while (REVIEW_KEY.consumeClick()) {
                if (sessionId != null) {
                    // Stopping is always honored by the server, no questions asked.
                    PacketDistributor.sendToServer(IntimacyNetwork.StopSessionPayload.INSTANCE);
                } else if (pendingId != null) {
                    mc.setScreen(new ConsentPromptScreen(pendingId, pendingFrom, pendingProtection));
                } else {
                    mc.player.displayClientMessage(Component.literal("No pending request.")
                        .withStyle(ChatFormatting.GRAY), true);
                }
            }
        }
    }
}