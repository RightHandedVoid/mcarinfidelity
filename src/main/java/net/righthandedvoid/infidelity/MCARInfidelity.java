package net.righthandedvoid.infidelity;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(MCARInfidelity.MODID)
public class MCARInfidelity {
    public static final String MODID = "mcarinfidelity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MCARInfidelity(IEventBus modEventBus, ModContainer modContainer) {
    }
}
