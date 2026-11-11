package net.righthandedvoid.infidelity.item;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.righthandedvoid.infidelity.MCARInfidelity;

import java.util.function.Supplier;

public final class ModComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MCARInfidelity.MODID);

    /** A used diaphragm that hasn't been washed yet. */
    public static final Supplier<DataComponentType<Boolean>> DIRTY = COMPONENTS.register("dirty",
        () -> DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

    private ModComponents() {}
}
