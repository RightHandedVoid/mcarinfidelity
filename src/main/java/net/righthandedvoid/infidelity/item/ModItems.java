package net.righthandedvoid.infidelity.item;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.righthandedvoid.infidelity.MCARInfidelity;
import net.righthandedvoid.infidelity.protection.BirthControlItem;
import net.righthandedvoid.infidelity.protection.DiaphragmItem;
import net.righthandedvoid.infidelity.protection.ProtectionItem;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MCARInfidelity.MODID);

    public static final DeferredItem<CycleJournalItem> CYCLE_JOURNAL = ITEMS.registerItem("cycle_journal",
        CycleJournalItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<ProtectionItem> MALE_CONDOM = ITEMS.registerItem("male_condom",
        p -> new ProtectionItem(p, "Single use. Works from either person's inventory or a pelvis slot."),
        new Item.Properties().stacksTo(16));

    public static final DeferredItem<ProtectionItem> FEMALE_CONDOM = ITEMS.registerItem("female_condom",
        p -> new ProtectionItem(p, "Internal condom. Single use. Works from either person's inventory or a pelvis slot."),
        new Item.Properties().stacksTo(16));

    public static final DeferredItem<DiaphragmItem> DIAPHRAGM = ITEMS.registerItem("diaphragm",
        DiaphragmItem::new, new Item.Properties().durability(20));

    public static final DeferredItem<BirthControlItem> BIRTH_CONTROL = ITEMS.registerItem("birth_control",
        BirthControlItem::new, new Item.Properties().stacksTo(16));

    private ModItems() {}

    @EventBusSubscriber(modid = MCARInfidelity.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Tabs {
        @SubscribeEvent
        public static void addToTabs(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(CYCLE_JOURNAL.get());
                event.accept(MALE_CONDOM.get());
                event.accept(FEMALE_CONDOM.get());
                event.accept(DIAPHRAGM.get());
                event.accept(BIRTH_CONTROL.get());
            }
        }
    }
}
