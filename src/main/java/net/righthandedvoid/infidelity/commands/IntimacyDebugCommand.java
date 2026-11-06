package net.righthandedvoid.infidelity.commands; // change to your package

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.righthandedvoid.infidelity.EligibilityResult;
import net.righthandedvoid.infidelity.RelationshipContext;
import net.righthandedvoid.infidelity.intimacy.IntimacyRules;
import net.righthandedvoid.infidelity.MCARInfidelity; // change to your main mod class
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Development-only commands. Not registered in a built/production jar.
 *
 *   /intimacydebug check <target>          -> prints the EligibilityResult and the data behind it
 *   /intimacydebug hearts <villager> <n>   -> sets that villager's hearts toward you
 *
 * Example target: @e[type=mca:female_villager,sort=nearest,limit=1]
 */
@EventBusSubscriber(modid = MCARInfidelity.MODID)
public final class IntimacyDebugCommand {

    private IntimacyDebugCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;

        event.getDispatcher().register(Commands.literal("intimacydebug")
            .requires(src -> src.hasPermission(0))
            .then(Commands.literal("check")
                .then(Commands.argument("target", EntityArgument.entity())
                    .executes(ctx -> check(ctx.getSource(),
                        EntityArgument.getEntity(ctx, "target")))))
            .then(Commands.literal("hearts")
                .then(Commands.argument("target", EntityArgument.entity())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(-100, 1000))
                        .executes(ctx -> setHearts(ctx.getSource(),
                            EntityArgument.getEntity(ctx, "target"),
                            IntegerArgumentType.getInteger(ctx, "amount")))))));
    }

    private static int check(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (target instanceof VillagerEntityMCA villager) {
            EligibilityResult result = IntimacyRules.check(player, villager);
            var brain = villager.getVillagerBrain();
            RelationshipContext context = RelationshipContext.of(villager, player);

            reply(source, "Result: " + result);
            reply(source, "Adult: " + IntimacyRules.isAdultVillager(villager)
                + " | Hearts: " + brain.getMemoriesForPlayer(player).getHearts()
                + " | Mood: " + brain.getMoodValue()
                + " | State: " + villager.getRelationships().getRelationshipState());
            reply(source, "Context: " + context);
            return 1;
        }

        if (target instanceof ServerPlayer other) {
            reply(source, "Result: " + IntimacyRules.check(player, other));
            return 1;
        }

        reply(source, "Target must be an MCA villager or a player.");
        return 0;
    }

    private static int setHearts(CommandSourceStack source, Entity target, int amount) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!(target instanceof VillagerEntityMCA villager)) {
            reply(source, "Target must be an MCA villager.");
            return 0;
        }
        villager.getVillagerBrain().getMemoriesForPlayer(player).setHearts(amount);
        reply(source, "Hearts set to " + amount);
        return 1;
    }

    private static void reply(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
}