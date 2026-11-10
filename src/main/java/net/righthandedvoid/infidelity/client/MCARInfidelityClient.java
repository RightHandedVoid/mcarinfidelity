package net.righthandedvoid.infidelity.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.righthandedvoid.infidelity.MCARInfidelity;

/**
 * Client-only entry point. Loaded only on the physical client, so the GUI classes
 * referenced here never get touched on a dedicated server.
 */
@Mod(value = MCARInfidelity.MODID, dist = Dist.CLIENT)
public class MCARInfidelityClient {
    public MCARInfidelityClient(ModContainer container) {
        // Adds the "Config" button for this mod in the Mods list: NeoForge's built-in screen plus a reset-all button.
        container.registerExtensionPoint(IConfigScreenFactory.class,
            (mod, parent) -> new ConfigurationScreen(mod, parent, InfidelityConfigScreen.TopSection::new));
    }
}
