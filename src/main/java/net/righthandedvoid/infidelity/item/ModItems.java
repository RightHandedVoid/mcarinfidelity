package net.righthandedvoid.infidelity.item;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.righthandedvoid.infidelity.MCARInfidelity;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MCARInfidelity.MODID);

    public static final DeferredItem<CycleJournalItem> CYCLE_JOURNAL = ITEMS.registerItem("cycle_journal",
        CycleJournalItem::new, new Item.Properties().stacksTo(1));

    private ModItems() {}

    @EventBusSubscriber(modid = MCARInfidelity.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Tabs {
        @SubscribeEvent
        public static void addToTabs(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(CYCLE_JOURNAL.get());
        }
    }
}
