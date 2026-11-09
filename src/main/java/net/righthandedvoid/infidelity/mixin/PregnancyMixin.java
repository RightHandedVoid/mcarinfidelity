package net.righthandedvoid.infidelity.mixin;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Pregnancy;
import net.minecraft.world.entity.Entity;
import net.righthandedvoid.infidelity.pregnancy.PregnancyData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps MCA's own reproduction from stacking on top of one of our pregnancies:
 * no villager-villager gestation and no instant baby from the procreate interaction while she's carrying.
 */
@Mixin(value = Pregnancy.class, remap = false)
public abstract class PregnancyMixin {

    @Shadow @Final private VillagerEntityMCA mother;

    @Inject(method = "tryStartGestation", at = @At("HEAD"), cancellable = true)
    private void mcarinfidelity$blockGestation(CallbackInfoReturnable<Boolean> cir) {
        if (PregnancyData.isGestating(mother)) cir.setReturnValue(false);
    }

    @Inject(method = "procreate", at = @At("HEAD"), cancellable = true)
    private void mcarinfidelity$blockProcreate(Entity spouse, CallbackInfo ci) {
        if (PregnancyData.isGestating(mother)) ci.cancel();
    }
}
