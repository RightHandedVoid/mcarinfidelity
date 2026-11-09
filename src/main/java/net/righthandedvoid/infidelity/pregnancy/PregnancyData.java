package net.righthandedvoid.infidelity.pregnancy;

import com.mojang.serialization.Codec;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.righthandedvoid.infidelity.MCARInfidelity;

import java.util.function.Supplier;

/** Attachments for pregnancy state. Present only while relevant; absent means "nothing going on". */
public final class PregnancyData {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MCARInfidelity.MODID);

    public static final Supplier<AttachmentType<Gestation>> GESTATION = ATTACHMENT_TYPES.register("gestation",
        () -> AttachmentType.builder(Gestation::placeholder)
            .serialize(Gestation.CODEC)
            .copyOnDeath()
            .build());

    /** Ticks left before the carrier can conceive again after a birth. */
    public static final Supplier<AttachmentType<Integer>> RECOVERY = ATTACHMENT_TYPES.register("recovery",
        () -> AttachmentType.builder(() -> 0)
            .serialize(Codec.INT)
            .copyOnDeath()
            .build());

    private PregnancyData() {}

    /** Our own gestation, actively growing (not the "waiting to hand over" phase). */
    public static boolean isGestating(LivingEntity entity) {
        return entity.hasData(GESTATION) && entity.getData(GESTATION).phase == Gestation.Phase.GESTATING;
    }

    /** Pregnant by any means: ours, or MCA's own villager-villager gestation. */
    public static boolean isPregnant(LivingEntity entity) {
        if (isGestating(entity)) return true;
        if (entity instanceof VillagerEntityMCA villager) {
            try {
                return villager.getRelationships().getPregnancy().isPregnant();
            } catch (Throwable t) {
                return true;   // can't tell: treat as pregnant so nothing stacks on top
            }
        }
        return false;
    }

    public static int recoveryTicks(LivingEntity entity) {
        return entity.hasData(RECOVERY) ? entity.getData(RECOVERY) : 0;
    }
}
