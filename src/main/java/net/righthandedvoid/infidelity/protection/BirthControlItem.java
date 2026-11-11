package net.righthandedvoid.infidelity.protection;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.righthandedvoid.infidelity.InfidelityConfig;
import net.righthandedvoid.infidelity.intimacy.IntimacyRules;
import net.righthandedvoid.infidelity.intimacy.Privacy;
import net.righthandedvoid.infidelity.optin.OptIn;
import net.righthandedvoid.infidelity.pregnancy.PregnancyManager;
import net.righthandedvoid.infidelity.preference.VillagerPreferences;

import java.util.List;

/** A pill: protects the woman who takes it for a few days. Use it yourself, or offer it to a villager. */
public class BirthControlItem extends Item {

    public BirthControlItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!OptIn.isOptedIn(serverPlayer)) {
            Protection.gray(serverPlayer, "Opt in with /intimacy optin to use this.");
            return InteractionResultHolder.fail(stack);
        }
        if (PregnancyManager.genderOf(serverPlayer) != Gender.FEMALE) {
            Protection.gray(serverPlayer, "These aren't for you.");
            return InteractionResultHolder.fail(stack);
        }
        Protection.takePill(serverPlayer);
        stack.consume(1, serverPlayer);
        Protection.gray(serverPlayer, "You take a pill. Protected for " + Protection.pillDaysLeft(serverPlayer) + " day(s).");
        return InteractionResultHolder.consume(stack);
    }

    /**
     * Offering one to a villager (wired up in VillagerItemEvents). She decides: she trusts you enough
     * (partner, or enough hearts), and she isn't hoping for a baby with you.
     * Returns false if this shouldn't take the click at all (not opted in, not an adult).
     */
    public static boolean offerTo(ServerPlayer player, VillagerEntityMCA villager, ItemStack stack) {
        if (!OptIn.isOptedIn(player) || !IntimacyRules.isAdultVillager(villager)) return false;
        String name = villager.getName().getString();
        if (PregnancyManager.genderOf(villager) != Gender.FEMALE) {
            Protection.gray(player, name + " has no use for those.");
            return true;
        }

        var rel = villager.getRelationships();
        boolean partner = rel.isMarriedTo(player.getUUID()) || rel.isEngagedWith(player.getUUID());
        int hearts = villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
        if (partner && VillagerPreferences.wantsChildren(villager)) {
            Protection.gray(player, name + " shakes " + Privacy.possessive(villager) + " head. "
                + (Privacy.possessive(villager).equals("her") ? "She's" : "They're") + " hoping for a baby.");
            return true;
        }
        if (!partner && hearts < InfidelityConfig.CYCLE_SHARE_HEARTS.get()) {
            Protection.gray(player, name + " doesn't know you well enough for that.");
            return true;
        }

        Protection.takePill(villager);
        stack.consume(1, player);
        Protection.gray(player, name + " takes a pill.");
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Use to take one, or use on someone to offer one.").withStyle(ChatFormatting.GRAY));
    }
}
