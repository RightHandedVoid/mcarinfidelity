package net.righthandedvoid.infidelity.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * NeoForge's config screen, plus a "Reset everything" button on the top page. NeoForge's own Reset button
 * only resets the section you're looking at; this one resets every value in every section of the file.
 */
public final class InfidelityConfigScreen {

    private InfidelityConfigScreen() {}

    public static class TopSection extends ConfigurationScreen.ConfigurationSectionScreen {

        public TopSection(Screen parent, ModConfig.Type type, ModConfig modConfig, Component title) {
            super(parent, type, modConfig, title);
        }

        @Override
        protected Collection<? extends Element> createSyntheticValues() {
            List<Element> extra = new ArrayList<>(super.createSyntheticValues());
            Button button = Button.builder(Component.literal("Reset everything to defaults"), b -> confirm())
                .tooltip(Tooltip.create(Component.literal("Resets every setting in every section of this file.")))
                .width(Button.DEFAULT_WIDTH)
                .build();
            extra.add(new Element(null, null, button, false));
            return extra;
        }

        private void confirm() {
            minecraft.setScreen(new ConfirmScreen(yes -> {
                if (yes) resetAll();
                minecraft.setScreen(this);
            }, Component.literal("Reset all MCAR Infidelity settings?"),
                Component.literal("Every value in every section goes back to its default. This can't be undone here.")));
        }

        private void resetAll() {
            if (resetIn(context.modSpec().getValues())) {
                changed = true;            // saved when this screen closes, like any other edit
                sectionCache.clear();      // sub-pages were built with the old values
                rebuild();
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static boolean resetIn(UnmodifiableConfig config) {
            boolean any = false;
            for (Object value : config.valueMap().values()) {
                if (value instanceof ModConfigSpec.ConfigValue cv) {
                    if (!java.util.Objects.equals(cv.getRaw(), cv.getDefault())) {
                        cv.set(cv.getDefault());
                        any = true;
                    }
                } else if (value instanceof UnmodifiableConfig sub) {
                    any |= resetIn(sub);
                }
            }
            return any;
        }
    }
}
