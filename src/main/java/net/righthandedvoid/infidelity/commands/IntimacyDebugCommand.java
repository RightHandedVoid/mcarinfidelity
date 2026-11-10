package net.righthandedvoid.infidelity.commands; // change to your package

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.righthandedvoid.infidelity.EligibilityResult;
import net.righthandedvoid.infidelity.RelationshipContext;
import net.righthandedvoid.infidelity.intimacy.IntimacyRules;
import net.righthandedvoid.infidelity.preference.ChildPreference;
import net.righthandedvoid.infidelity.preference.PrivacyPreference;
import net.righthandedvoid.infidelity.intimacy.Privacy;
import net.righthandedvoid.infidelity.preference.VillagerPreferences;
import net.righthandedvoid.infidelity.pregnancy.Gestation;
import net.righthandedvoid.infidelity.cycle.FertilityCycle;
import net.righthandedvoid.infidelity.consent.VillagerOffers;
import net.righthandedvoid.infidelity.affair.AffairLedger;
import net.righthandedvoid.infidelity.affair.Grievances;
import net.righthandedvoid.infidelity.affair.Incident;
import net.righthandedvoid.infidelity.pregnancy.PregnancyData;
import net.righthandedvoid.infidelity.pregnancy.PregnancyManager;
import net.minecraft.world.entity.LivingEntity;
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
 *   /intimacydebug wantschildren <villager> [wants|doesnt|reroll]  -> shows or sets the lasting preference
 *   /intimacydebug privacy <villager> [home|private|anywhere|reroll]  -> shows/sets place preference and checks it here
 *   /intimacydebug cycle <target> [day <n>]  -> shows the cycle and conception chance, or sets today's cycle day
 *   /intimacydebug offer <villager>          -> she walks over and makes an offer now (skips chance and cooldown)
 *   /intimacydebug affairs <villager>        -> her grievances and the incidents involving her
 *   /intimacydebug forgive <villager>        -> clears her grievances (offense history included)
 *   /intimacydebug conceive <target>        -> conception between you and target, ignoring chance (other gates apply)
 *   /intimacydebug pregnancy <target> [due|clear]  -> shows pregnancy state, skips to birth, or clears it
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
                            IntegerArgumentType.getInteger(ctx, "amount"))))))
            .then(Commands.literal("wantschildren")
                .then(Commands.argument("target", EntityArgument.entity())
                    .executes(ctx -> childPref(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), null))
                    .then(Commands.literal("wants")
                        .executes(ctx -> childPref(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), ChildPreference.WANTS)))
                    .then(Commands.literal("doesnt")
                        .executes(ctx -> childPref(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), ChildPreference.DOES_NOT_WANT)))
                    .then(Commands.literal("reroll")
                        .executes(ctx -> childPref(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), ChildPreference.UNDECIDED)))))
            .then(Commands.literal("privacy")
                .then(Commands.argument("target", EntityArgument.entity())
                    .executes(ctx -> privacy(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), null))
                    .then(Commands.literal("home")
                        .executes(ctx -> privacy(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), PrivacyPreference.HOME)))
                    .then(Commands.literal("private")
                        .executes(ctx -> privacy(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), PrivacyPreference.PRIVATE)))
                    .then(Commands.literal("anywhere")
                        .executes(ctx -> privacy(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), PrivacyPreference.ANYWHERE)))
                    .then(Commands.literal("reroll")
                        .executes(ctx -> privacy(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), PrivacyPreference.UNDECIDED)))))
            .then(Commands.literal("cycle")
                .then(Commands.argument("target", EntityArgument.entity())
                    .executes(ctx -> cycle(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), 0))
                    .then(Commands.literal("day")
                        .then(Commands.argument("day", IntegerArgumentType.integer(1, 60))
                            .executes(ctx -> cycle(ctx.getSource(), EntityArgument.getEntity(ctx, "target"),
                                IntegerArgumentType.getInteger(ctx, "day")))))))
            .then(Commands.literal("offer")
                .then(Commands.argument("target", EntityArgument.entity())
                    .executes(ctx -> forceOffer(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
            .then(Commands.literal("affairs")
                .then(Commands.argument("target", EntityArgument.entity())
                    .executes(ctx -> affairs(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
            .then(Commands.literal("forgive")
                .then(Commands.argument("target", EntityArgument.entity())
                    .executes(ctx -> forgive(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
            .then(Commands.literal("conceive")
                .then(Commands.argument("target", EntityArgument.entity())
                    .executes(ctx -> conceive(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
            .then(Commands.literal("pregnancy")
                .then(Commands.argument("target", EntityArgument.entity())
                    .executes(ctx -> pregnancy(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), null))
                    .then(Commands.literal("due")
                        .executes(ctx -> pregnancy(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), "due")))
                    .then(Commands.literal("clear")
                        .executes(ctx -> pregnancy(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), "clear"))))));
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
            reply(source, "Child preference: " + VillagerPreferences.childPreference(villager)
                + " | Personality: " + brain.getPersonality());
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

    private static int childPref(CommandSourceStack source, Entity target, ChildPreference set) {
        if (!(target instanceof VillagerEntityMCA villager)) {
            reply(source, "Target must be an MCA villager.");
            return 0;
        }
        if (set != null) VillagerPreferences.setChildPreference(villager, set);
        // Reading resolves UNDECIDED, so "reroll" shows the fresh result immediately.
        reply(source, villager.getName().getString() + ": " + VillagerPreferences.childPreference(villager));
        return 1;
    }

    private static int privacy(CommandSourceStack source, Entity target, PrivacyPreference set) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!(target instanceof VillagerEntityMCA villager)) {
            reply(source, "Target must be an MCA villager.");
            return 0;
        }
        if (set != null) VillagerPreferences.setPrivacyPreference(villager, set);
        reply(source, villager.getName().getString() + ": " + VillagerPreferences.privacyPreference(villager)
            + " | here: " + Privacy.check(villager, player)
            + " | has home: " + Privacy.homeBuilding(villager).isPresent()
            + " | at home: " + Privacy.atHome(villager)
            + " | sheltered: " + Privacy.isSheltered(villager));
        var watchers = Privacy.onlookers(villager, player);
        reply(source, "Onlookers: " + (watchers.isEmpty() ? "none"
            : String.join(", ", watchers.stream().map(e -> e.getName().getString()).toList())));
        return 1;
    }

    private static int cycle(CommandSourceStack source, Entity target, int setDay) {
        if (!(target instanceof LivingEntity living)) {
            reply(source, "Target must be a player or a villager.");
            return 0;
        }
        if (!FertilityCycle.hasCycle(living)) {
            reply(source, living.getName().getString() + " has no cycle (gender: " + PregnancyManager.genderOf(living) + ").");
            return 0;
        }
        if (setDay > 0) FertilityCycle.setDay(living, Math.min(setDay, FertilityCycle.length()));
        int length = FertilityCycle.length();
        reply(source, living.getName().getString() + ": day " + FertilityCycle.day(living) + "/" + length
            + " | " + FertilityCycle.phase(living)
            + " | ovulation day " + FertilityCycle.ovulationDay(length) + " +/- " + FertilityCycle.fertileWindow(length)
            + " | conception chance " + Math.round(FertilityCycle.conceptionChance(living) * 1000) / 10.0 + "%");
        return 1;
    }

    private static int forceOffer(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!(target instanceof VillagerEntityMCA villager)) {
            reply(source, "Target must be an MCA villager.");
            return 0;
        }
        // Report the gates up front so it's clear why nothing happens.
        reply(source, "Eligible (ignoring distance): " + IntimacyRules.check(player, villager, true)
            + " | place: " + Privacy.check(villager, player)
            + " | move state: " + villager.getVillagerBrain().getMoveState());
        reply(source, VillagerOffers.forceApproach(player, villager) ? "She's on her way." : "She's already walking somewhere.");
        return 1;
    }

    private static int affairs(CommandSourceStack source, Entity target) {
        if (!(target instanceof VillagerEntityMCA villager)) {
            reply(source, "Target must be an MCA villager.");
            return 0;
        }
        var server = source.getServer();
        var tree = net.conczin.mca.server.world.data.FamilyTree.get(server.overworld());
        java.util.function.Function<java.util.UUID, String> name = id -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) return p.getName().getString();
            return tree.getOrEmpty(id).map(n -> n.getName()).orElse(id.toString().substring(0, 8));
        };

        reply(source, villager.getName().getString() + " | forgiveness x" + Grievances.forgiveness(villager)
            + " | partner: " + villager.getRelationships().getPartnerUUID().map(name).orElse("none"));
        var grievances = Grievances.all(villager);
        if (grievances.isEmpty()) reply(source, "No grievances.");
        grievances.forEach((id, g) -> reply(source, "  vs " + name.apply(id) + ": "
            + Math.round(g.score() * 100) / 100.0 + " (offenses " + g.offenses() + ")"));

        for (Incident i : AffairLedger.get(server).involving(villager.getUUID())) {
            reply(source, "  incident: " + name.apply(i.cheater) + " cheated on " + name.apply(i.wronged)
                + " with " + name.apply(i.other) + " | " + i.tier + (i.cheaterInitiated ? ", initiated" : ", was asked")
                + " | " + (i.discovered() ? "discovered: " + i.discoveredBy() : "undiscovered")
                + " | witnesses: " + i.witnesses().size());
        }
        return 1;
    }

    private static int forgive(CommandSourceStack source, Entity target) {
        if (!(target instanceof VillagerEntityMCA villager)) {
            reply(source, "Target must be an MCA villager.");
            return 0;
        }
        Grievances.clear(villager);
        reply(source, villager.getName().getString() + " has forgiven everyone.");
        return 1;
    }

    private static int conceive(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!(target instanceof LivingEntity living) || target == player) {
            reply(source, "Target must be another player or a villager.");
            return 0;
        }
        PregnancyManager.Result result = PregnancyManager.tryConceive(player, living, true);
        reply(source, "Conception: " + result + " (you: " + PregnancyManager.genderOf(player)
            + ", target: " + PregnancyManager.genderOf(living) + ")");
        return result.conceived() ? 1 : 0;
    }

    private static int pregnancy(CommandSourceStack source, Entity target, String action) {
        if (!(target instanceof LivingEntity living)) {
            reply(source, "Target must be a player or a villager.");
            return 0;
        }
        if ("due".equals(action)) {
            reply(source, PregnancyManager.makeDue(living) ? "Due now; birth within a second." : "Not gestating.");
            return 1;
        }
        if ("clear".equals(action)) {
            PregnancyManager.clear(living);
            reply(source, "Cleared.");
            return 1;
        }
        String name = living.getName().getString();
        reply(source, name + " | gender: " + PregnancyManager.genderOf(living)
            + " | recovery ticks: " + PregnancyData.recoveryTicks(living)
            + " | MCA/any pregnant: " + PregnancyData.isPregnant(living));
        if (living.hasData(PregnancyData.GESTATION)) {
            Gestation g = living.getData(PregnancyData.GESTATION);
            reply(source, "Phase: " + g.phase() + " | " + g.elapsed() + "/" + g.duration() + " ticks ("
                + Math.round(g.progress() * 100) + "%) | babies: " + g.babyCount() + " | father: " + g.fatherName);
        }
        return 1;
    }

    private static void reply(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
}