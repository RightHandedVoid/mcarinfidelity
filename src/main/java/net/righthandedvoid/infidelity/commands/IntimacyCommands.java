package net.righthandedvoid.infidelity.commands; // change to your package

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.righthandedvoid.infidelity.MCARInfidelity; // change to your main mod class
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.righthandedvoid.infidelity.consent.ConsentManager;
import net.righthandedvoid.infidelity.consent.ConsentResponse;
import net.righthandedvoid.infidelity.consent.SessionManager;
import net.righthandedvoid.infidelity.optin.OptIn;

/**
 * Player-facing consent commands. The [Accept]/[Decline] chat buttons run these.
 *
 *   /intimacy request <target> [protected|unprotected]   (defaults to protected)
 *   /intimacy accept [protected]
 *   /intimacy decline
 *   /intimacy cancel
 *   /intimacy stop      (ends an active session; always allowed)
 *   /intimacy optin     (explains, then asks for confirmation)
 *   /intimacy optout    (immediate, no confirmation)
 *   /intimacy scenes [on|off]   (see scenes or skip them; in player-to-player, if either skips, both skip)
 *   /intimacy status
 */
@EventBusSubscriber(modid = MCARInfidelity.MODID)
public final class IntimacyCommands {

    private IntimacyCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("intimacy")
            .then(Commands.literal("request")
                .then(Commands.argument("target", EntityArgument.entity())
                    .executes(ctx -> request(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), true))
                    .then(Commands.literal("protected")
                        .executes(ctx -> request(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), true)))
                    .then(Commands.literal("unprotected")
                        .executes(ctx -> request(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), false)))))
            .then(Commands.literal("accept")
                .executes(ctx -> respond(ctx.getSource(), ConsentResponse.ACCEPT))
                .then(Commands.literal("protected")
                    .executes(ctx -> respond(ctx.getSource(), ConsentResponse.ACCEPT_WITH_PROTECTION))))
            .then(Commands.literal("decline").executes(ctx -> respond(ctx.getSource(), ConsentResponse.DECLINE)))
            .then(Commands.literal("cancel").executes(ctx -> cancel(ctx.getSource())))
            .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
            .then(Commands.literal("optin")
                .executes(ctx -> explainOptIn(ctx.getSource()))
                .then(Commands.literal("confirm").executes(ctx -> confirmOptIn(ctx.getSource()))))
            .then(Commands.literal("optout").executes(ctx -> optOut(ctx.getSource())))
            .then(Commands.literal("scenes")
                .executes(ctx -> scenes(ctx.getSource(), null))
                .then(Commands.literal("on").executes(ctx -> scenes(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> scenes(ctx.getSource(), false))))
            .then(Commands.literal("status").executes(ctx -> status(ctx.getSource()))));
    }

    private static int request(CommandSourceStack source, Entity target, boolean protection) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ConsentManager.RequestResult result = ConsentManager.request(player, target, protection);

        String text = ConsentManager.describe(result);
        if (text != null) player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
        return result.outcome() == ConsentManager.Outcome.SENT ? 1 : 0;
    }

    private static int respond(CommandSourceStack source, ConsentResponse response) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ConsentManager.respond(player, response)) {
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

    // ---- Opt-in ----

    private static int explainOptIn(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (OptIn.isOptedIn(player)) {
            gray(player, "You're already opted in. Use /intimacy optout to turn it off at any time.");
            return 1;
        }
        Component confirm = Component.literal("[Confirm]").withStyle(s -> s
            .withColor(ChatFormatting.GREEN)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/intimacy optin confirm"))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Opt in"))));

        gray(player, "Opting in enables adult content from this mod for you: you'll be able to send and "
            + "receive intimacy requests. Every request still needs a yes, and you can stop at any time.");
        gray(player, "You can opt out whenever you like with /intimacy optout.");
        player.sendSystemMessage(confirm);
        return 1;
    }

    private static int confirmOptIn(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        OptIn.set(player, true);
        gray(player, "You've opted in. Use /intimacy optout to turn it off at any time.");
        return 1;
    }

    private static int optOut(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        OptIn.set(player, false);
        // Pending requests and any active session end on their own within a second,
        // since IntimacyRules re-checks opt-in continuously.
        SessionManager.stop(player);
        gray(player, "You've opted out. You won't send or receive any requests.");
        return 1;
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        gray(player, OptIn.isOptedIn(player) ? "You're opted in." : "You're not opted in.");
        gray(player, OptIn.wantsScenes(player) ? "Scenes: shown." : "Scenes: skipped.");
        return 1;
    }

    private static int scenes(CommandSourceStack source, Boolean show) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (show == null) {
            gray(player, (OptIn.wantsScenes(player) ? "Scenes are shown." : "Scenes are skipped.")
                + " Use /intimacy scenes on|off to change it.");
            return 1;
        }
        OptIn.setWantsScenes(player, show);
        gray(player, show
            ? "Scenes will be shown. With another player, they're only shown if both of you have this on."
            : "Scenes will be skipped (fade to black).");
        return 1;
    }

    private static void gray(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }
}
