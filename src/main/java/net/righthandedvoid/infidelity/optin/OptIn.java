package net.righthandedvoid.infidelity.optin;

import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.righthandedvoid.infidelity.MCARInfidelity;

import java.util.function.Supplier;

/**
 * Per-player opt-in for intimate content. Off by default.
 *
 * Stored as a data attachment on the player, so it's saved with their player data and survives
 * death and relogging. Read only on the server. Players who haven't opted in can't send or
 * receive requests, and opting out ends any pending request or active session within a second,
 * because IntimacyRules re-checks it continuously.
 */
public final class OptIn {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MCARInfidelity.MODID);

    public static final Supplier<AttachmentType<Boolean>> OPTED_IN = ATTACHMENT_TYPES.register("opted_in",
            () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .copyOnDeath()
                    .build());

    private OptIn() {}

    public static boolean isOptedIn(ServerPlayer player) {
        return player != null && player.getData(OPTED_IN);
    }

    public static void set(ServerPlayer player, boolean optedIn) {
        player.setData(OPTED_IN, optedIn);
    }
}
