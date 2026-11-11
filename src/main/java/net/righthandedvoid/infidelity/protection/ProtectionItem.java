package net.righthandedvoid.infidelity.protection;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Single-use condoms. Used up automatically from either person's inventory or pelvis slots. */
public class ProtectionItem extends Item {

    private final String hint;

    public ProtectionItem(Properties properties, String hint) {
        super(properties);
        this.hint = hint;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(hint).withStyle(ChatFormatting.GRAY));
    }
}
