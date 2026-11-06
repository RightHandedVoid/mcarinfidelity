package net.righthandedvoid.infidelity.intimacy; // change to your package

import net.righthandedvoid.infidelity.MCARInfidelity; // change to your main mod class
import net.righthandedvoid.infidelity.client.ClientConsent;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.righthandedvoid.infidelity.consent.ConsentManager;
import net.righthandedvoid.infidelity.consent.SessionManager;

import java.util.UUID;

/**
 * Packets:
 *   C2S RequestIntimacyPayload  - "I'm asking the entity I'm looking at" (server validates everything)
 *   C2S ConsentResponsePayload  - "I accept/decline request <id>"       (server checks it's theirs and pending)
 *   S2C ConsentPromptPayload    - "Someone is asking you"               (opens the prompt option on the client)
 *   S2C ConsentClosedPayload    - "That request is over"                (closes the prompt on the client)
 *   S2C SessionStatePayload     - "Your session started/ended"          (fade overlay on the client)
 *   C2S StopSessionPayload      - "I want to stop"                      (always honored)
 */
@EventBusSubscriber(modid = MCARInfidelity.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class IntimacyNetwork {

    private IntimacyNetwork() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MCARInfidelity.MODID, path);
    }

    // =====================================================================
    // Payloads
    // =====================================================================

    public record RequestIntimacyPayload(int entityId) implements CustomPacketPayload {
        public static final Type<RequestIntimacyPayload> TYPE = new Type<>(id("request_intimacy"));
        public static final StreamCodec<ByteBuf, RequestIntimacyPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(RequestIntimacyPayload::new, RequestIntimacyPayload::entityId);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ConsentResponsePayload(UUID requestId, boolean accept) implements CustomPacketPayload {
        public static final Type<ConsentResponsePayload> TYPE = new Type<>(id("consent_response"));
        public static final StreamCodec<ByteBuf, ConsentResponsePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ConsentResponsePayload::requestId,
            ByteBufCodecs.BOOL, ConsentResponsePayload::accept,
            ConsentResponsePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ConsentPromptPayload(UUID requestId, String initiatorName) implements CustomPacketPayload {
        public static final Type<ConsentPromptPayload> TYPE = new Type<>(id("consent_prompt"));
        public static final StreamCodec<ByteBuf, ConsentPromptPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ConsentPromptPayload::requestId,
            ByteBufCodecs.STRING_UTF8, ConsentPromptPayload::initiatorName,
            ConsentPromptPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ConsentClosedPayload(UUID requestId) implements CustomPacketPayload {
        public static final Type<ConsentClosedPayload> TYPE = new Type<>(id("consent_closed"));
        public static final StreamCodec<ByteBuf, ConsentClosedPayload> STREAM_CODEC =
            UUIDUtil.STREAM_CODEC.map(ConsentClosedPayload::new, ConsentClosedPayload::requestId);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SessionStatePayload(UUID sessionId, boolean active, String partnerName) implements CustomPacketPayload {
        public static final Type<SessionStatePayload> TYPE = new Type<>(id("session_state"));
        public static final StreamCodec<ByteBuf, SessionStatePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SessionStatePayload::sessionId,
            ByteBufCodecs.BOOL, SessionStatePayload::active,
            ByteBufCodecs.STRING_UTF8, SessionStatePayload::partnerName,
            SessionStatePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record StopSessionPayload() implements CustomPacketPayload {
        public static final StopSessionPayload INSTANCE = new StopSessionPayload();
        public static final Type<StopSessionPayload> TYPE = new Type<>(id("stop_session"));
        public static final StreamCodec<ByteBuf, StopSessionPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // =====================================================================
    // Registration
    // =====================================================================

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(RequestIntimacyPayload.TYPE, RequestIntimacyPayload.STREAM_CODEC,
            IntimacyNetwork::handleRequest);
        registrar.playToServer(ConsentResponsePayload.TYPE, ConsentResponsePayload.STREAM_CODEC,
            IntimacyNetwork::handleResponse);
        registrar.playToServer(StopSessionPayload.TYPE, StopSessionPayload.STREAM_CODEC,
            IntimacyNetwork::handleStop);

        // Lambdas (not method references) so the client class is only loaded when a packet
        // actually arrives on the client. This keeps the dedicated server from crashing.
        registrar.playToClient(ConsentPromptPayload.TYPE, ConsentPromptPayload.STREAM_CODEC,
            (payload, ctx) -> ClientConsent.onPrompt(payload));
        registrar.playToClient(ConsentClosedPayload.TYPE, ConsentClosedPayload.STREAM_CODEC,
            (payload, ctx) -> ClientConsent.onClosed(payload));
        registrar.playToClient(SessionStatePayload.TYPE, SessionStatePayload.STREAM_CODEC,
            (payload, ctx) -> ClientConsent.onSessionState(payload));
    }

    // =====================================================================
    // Server handlers (run on the server thread)
    // =====================================================================

    private static void handleRequest(RequestIntimacyPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;

        // The client only names an entity. Everything else is decided here.
        Entity target = player.level().getEntity(payload.entityId());
        ConsentManager.RequestResult result = ConsentManager.request(player, target);

        String text = ConsentManager.describe(result);
        if (text != null) player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    private static void handleResponse(ConsentResponsePayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ConsentManager.respond(player, payload.requestId(), payload.accept());
    }

    private static void handleStop(StopSessionPayload payload, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) SessionManager.stop(player);
    }
}