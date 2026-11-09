package net.righthandedvoid.infidelity.cycle;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.righthandedvoid.infidelity.InfidelityConfig;
import net.righthandedvoid.infidelity.consent.VillagerOffers;
import net.righthandedvoid.infidelity.intimacy.IntimacyRules;
import net.righthandedvoid.infidelity.intimacy.Privacy;
import net.righthandedvoid.infidelity.optin.OptIn;
import net.righthandedvoid.infidelity.pregnancy.PregnancyData;

/** What the cycle journal says, for yourself or when you ask a villager. Server side only. */
public final class CycleTalk {

    private CycleTalk() {}

    public static void checkSelf(ServerPlayer player) {
        if (!OptIn.isOptedIn(player)) {
            gray(player, "The pages are blank. (Opt in with /intimacy optin to use this.)");
            return;
        }
        if (!FertilityCycle.hasCycle(player)) {
            gray(player, "You don't have a cycle to track.");
            return;
        }
        gray(player, switch (FertilityCycle.phase(player)) {
            case PAUSED -> PregnancyData.recoveryTicks(player) > 0 && !player.hasData(PregnancyData.GESTATION)
                ? "Your cycle hasn't come back since the birth yet."
                : "Your cycle is late. You might be pregnant.";
            default -> describe(player) + ".";
        });
    }

    /**
     * Asking a villager. Returns false if the journal shouldn't intercept the click at all
     * (not opted in, not an adult), so MCA's normal interaction happens instead.
     */
    public static boolean askVillager(ServerPlayer player, VillagerEntityMCA villager) {
        if (!OptIn.isOptedIn(player) || !IntimacyRules.isAdultVillager(villager)) return false;

        String name = villager.getName().getString();
        if (!FertilityCycle.hasCycle(villager)) {
            gray(player, name + " doesn't have a cycle to talk about.");
            return true;
        }
        if (!willingToShare(player, villager)) {
            gray(player, name + " would rather not talk about that.");
            return true;
        }

        String her = Privacy.possessive(villager);
        gray(player, switch (FertilityCycle.phase(villager)) {
            case PAUSED -> PregnancyData.recoveryTicks(villager) > 0 && !PregnancyData.isPregnant(villager)
                ? name + " says " + her + " cycle hasn't come back since the baby."
                : name + " lowers " + her + " voice: " + her + " cycle is late.";
            default -> name + " tells you: " + describe(villager) + ".";
        });
        // Around ovulation, a willing villager might take it from here.
        VillagerOffers.afterJournal(player, villager);
        return true;
    }

    /** Partners share easily; anyone else needs enough hearts and a decent mood. */
    private static boolean willingToShare(ServerPlayer player, VillagerEntityMCA villager) {
        try {
            var brain = villager.getVillagerBrain();
            int hearts = brain.getMemoriesForPlayer(player).getHearts();
            int mood = brain.getMoodValue();
            var rel = villager.getRelationships();
            boolean partner = rel.isMarriedTo(player.getUUID()) || rel.isEngagedWith(player.getUUID())
                || rel.isPromisedTo(player.getUUID());
            if (partner) return hearts >= 0 && mood >= -5;
            return hearts >= InfidelityConfig.CYCLE_SHARE_HEARTS.get() && mood >= 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String describe(net.minecraft.world.entity.LivingEntity entity) {
        return "day " + FertilityCycle.day(entity) + " of " + FertilityCycle.length()
            + " (" + FertilityCycle.phase(entity).label + ")";
    }

    private static void gray(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }
}
