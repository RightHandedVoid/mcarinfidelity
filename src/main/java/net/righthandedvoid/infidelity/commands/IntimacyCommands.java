package net.righthandedvoid.infidelity.commands; // change to your package

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.righthandedvoid.infidelity.MCARInfidelity; // change to your main mod class
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.righthandedvoid.infidelity.consent.ConsentManager;
import net.righthandedvoid.infidelity.consent.SessionManager;

/**
 * Player-facing consent commands. The [Accept]/[Decline] chat buttons run these.
 *
 *   /intimacy request <target>   (temporary trigger until shift-right-click is added)
 *   /intimacy accept
 *   /intimacy decline
 *   /intimacy cancel
 *   /intimacy stop      (ends an active session; always allowed)
 */
@EventBusSubscriber(modid = MCARInfidelity.MODID)
public final class IntimacyCommands {

    private IntimacyCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("intimacy")
            .then(Commands.literal("request")
                .then(Commands.argument("target", EntityArgument.entity())
                    .executes(ctx -> request(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
            .then(Commands.literal("accept").executes(ctx -> respond(ctx.getSource(), true)))
            .then(Commands.literal("decline").executes(ctx -> respond(ctx.getSource(), false)))
            .then(Commands.literal("cancel").executes(ctx -> cancel(ctx.getSource())))
            .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource()))));
    }

    private static int request(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ConsentManager.RequestResult result = ConsentManager.request(player, target);

        String text = ConsentManager.describe(result);
        if (text != null) player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
        return result.outcome() == ConsentManager.Outcome.SENT ? 1 : 0;
    }

    private static int respond(CommandSourceStack source, boolean accept) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ConsentManager.respond(player, accept)) {
            player.sendSystemMessage(Component.literal("You have no pending request.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        return 1;
    }

    private static int cancel(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ConsentManager.cancel(player)) {
            player.sendSystemMessage(Component.literal("You have no outgoing request.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        return 1;
    }

    private static int stop(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!SessionManager.stop(player)) {
            player.sendSystemMessage(Component.literal("You're not in a session.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        return 1;
    }
}