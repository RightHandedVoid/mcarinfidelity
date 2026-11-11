package net.righthandedvoid.infidelity.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.righthandedvoid.infidelity.InfidelityConfig;
import net.righthandedvoid.infidelity.MCARInfidelity;
import net.righthandedvoid.infidelity.item.ModItems;

import java.util.List;

/**
 * Washing a dirty diaphragm: drop it into a water cauldron, then right-click the cauldron with an empty hand
 * to stir. After enough stirs it's clean, and the cauldron loses one level of water.
 */
@EventBusSubscriber(modid = MCARInfidelity.MODID)
public final class CauldronWashing {

    private static final String STIRS = "mcarinfidelity_stirs";

    private CauldronWashing() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()) return;
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        if (!state.is(Blocks.WATER_CAULDRON)) return;

        List<ItemEntity> inside = event.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos),
            e -> e.getItem().is(ModItems.DIAPHRAGM.get()) && DiaphragmItem.isDirty(e.getItem()));
        if (inside.isEmpty()) return;

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4F, 1.2F);
        level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 8, 0.2, 0.05, 0.2, 0.1);

        ItemEntity item = inside.get(0);
        int stirs = item.getPersistentData().getInt(STIRS) + 1;
        if (stirs < InfidelityConfig.DIAPHRAGM_STIRS.get()) {
            item.getPersistentData().putInt(STIRS, stirs);
            return;
        }
        item.getPersistentData().remove(STIRS);
        var stack = item.getItem().copy();
        DiaphragmItem.setDirty(stack, false);
        item.setItem(stack);
        level.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 12, 0.2, 0.05, 0.2, 0.05);
        LayeredCauldronBlock.lowerFillLevel(state, level, pos);
    }
}
