package net.righthandedvoid.infidelity.item;

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
import net.righthandedvoid.infidelity.cycle.CycleTalk;

import java.util.List;

/**
 * Use it to check your own cycle. Use it on a female villager to ask about hers; whether she tells you
 * depends on your relationship and her mood (handled in CycleTalk, wired up in CycleEvents because
 * MCA villagers grab right-clicks before items see them).
 */
public class CycleJournalItem extends Item {

    public CycleJournalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            CycleTalk.checkSelf(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Use to check your cycle.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Use on someone to ask about theirs.").withStyle(ChatFormatting.GRAY));
    }
}
