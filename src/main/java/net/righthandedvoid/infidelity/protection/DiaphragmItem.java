package net.righthandedvoid.infidelity.protection;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.righthandedvoid.infidelity.item.ModComponents;

import java.util.List;

/**
 * Reusable: worn in a pelvis slot, gets dirty after each use and has to be washed in a water cauldron
 * (drop it in, then stir by right-clicking the cauldron) before it protects again. Wears out eventually.
 */
public class DiaphragmItem extends Item {

    public DiaphragmItem(Properties properties) {
        super(properties);
    }

    public static boolean isDirty(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModComponents.DIRTY.get()));
    }

    public static void setDirty(ItemStack stack, boolean dirty) {
        if (dirty) stack.set(ModComponents.DIRTY.get(), true);
        else stack.remove(ModComponents.DIRTY.get());
    }

    /** After a session: dirty, one use closer to wearing out. */
    static void afterUse(ItemStack stack, LivingEntity wearer) {
        setDirty(stack, true);
        int damage = stack.getDamageValue() + 1;
        if (damage >= stack.getMaxDamage()) {
            stack.shrink(1);
            if (wearer instanceof ServerPlayer player) Protection.gray(player, "Your diaphragm has worn out.");
        } else {
            stack.setDamageValue(damage);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (isDirty(stack)) {
            tooltip.add(Component.literal("Needs washing: drop it in a water cauldron and stir.").withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.literal("Clean. Wear it in a pelvis slot.").withStyle(ChatFormatting.GRAY));
        }
    }
}
