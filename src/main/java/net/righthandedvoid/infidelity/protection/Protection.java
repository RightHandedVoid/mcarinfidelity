package net.righthandedvoid.infidelity.protection;

import com.mojang.serialization.Codec;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.righthandedvoid.infidelity.InfidelityConfig;
import net.righthandedvoid.infidelity.MCARInfidelity;
import net.righthandedvoid.infidelity.cycle.FertilityCycle;
import net.righthandedvoid.infidelity.item.ModItems;
import net.righthandedvoid.infidelity.pregnancy.PregnancyManager;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Works out what protection a "with protection" session actually uses, and uses it up.
 *
 * In order of preference:
 *   BIRTH_CONTROL - the woman has taken a pill recently (nothing used up)
 *   DIAPHRAGM     - she's wearing a clean one in a pelvis slot (gets dirty, loses durability)
 *   MALE_CONDOM / FEMALE_CONDOM - either of them has one, in inventory or a pelvis slot (used up)
 *   SAFE_DAY      - nothing on hand, but she's outside her fertile window: they go ahead and rely on that
 *   (none)        - nothing on hand on a fertile day: the session doesn't happen unless they agree to go without
 *
 * NOT_NEEDED covers pairs where nobody can get pregnant. Each method has a small, configurable failure
 * chance; a failure (or relying on a safe day) means conception is rolled as normal, using the cycle.
 */
public final class Protection {

    public enum Method {
        BIRTH_CONTROL("birth control"),
        DIAPHRAGM("a diaphragm"),
        MALE_CONDOM("a condom"),
        FEMALE_CONDOM("an internal condom"),
        SAFE_DAY("no protection on hand; relying on it being a safe day"),
        NOT_NEEDED("not needed");

        public final String label;

        Method(String label) { this.label = label; }
    }

    /** A method found for a specific pair, plus how to use it up. */
    public record Plan(Method method, Runnable useUp) {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MCARInfidelity.MODID);

    /** Game time until which a pill protects her. */
    public static final Supplier<AttachmentType<Long>> BIRTH_CONTROL_UNTIL = ATTACHMENT_TYPES.register("birth_control_until",
        () -> AttachmentType.builder(() -> 0L)
            .serialize(Codec.LONG)
            .copyOnDeath()
            .build());

    private Protection() {}

    // =====================================================================
    // Finding a method
    // =====================================================================

    public static Optional<Plan> find(LivingEntity a, LivingEntity b) {
        Gender ga = PregnancyManager.genderOf(a);
        Gender gb = PregnancyManager.genderOf(b);
        LivingEntity female;
        if (ga == Gender.FEMALE && gb == Gender.MALE) female = a;
        else if (gb == Gender.FEMALE && ga == Gender.MALE) female = b;
        else return Optional.of(new Plan(Method.NOT_NEEDED, () -> {}));

        if (onBirthControl(female)) return Optional.of(new Plan(Method.BIRTH_CONTROL, () -> {}));

        Optional<SlotResult> diaphragm = curio(female, s -> s.is(ModItems.DIAPHRAGM.get()) && !DiaphragmItem.isDirty(s));
        if (diaphragm.isPresent()) {
            ItemStack stack = diaphragm.get().stack();
            return Optional.of(new Plan(Method.DIAPHRAGM, () -> DiaphragmItem.afterUse(stack, female)));
        }

        // Either person can bring a condom of either kind.
        for (Method method : new Method[]{Method.MALE_CONDOM, Method.FEMALE_CONDOM}) {
            Item item = method == Method.MALE_CONDOM ? ModItems.MALE_CONDOM.get() : ModItems.FEMALE_CONDOM.get();
            for (LivingEntity holder : new LivingEntity[]{a, b}) {
                Optional<ItemStack> stack = carried(holder, s -> s.is(item));
                if (stack.isPresent()) return Optional.of(new Plan(method, () -> stack.get().shrink(1)));
            }
        }

        if (InfidelityConfig.ALLOW_SAFE_DAYS.get() && !FertilityCycle.phase(female).fertile()) {
            return Optional.of(new Plan(Method.SAFE_DAY, () -> {}));
        }
        return Optional.empty();
    }

    /** Whether this method failed this time. SAFE_DAY always "fails": conception is rolled from the cycle as usual. */
    public static boolean failed(Method method, RandomSource random) {
        if (method == null) return true;
        double chance = switch (method) {
            case BIRTH_CONTROL -> InfidelityConfig.BIRTH_CONTROL_FAILURE.get();
            case DIAPHRAGM -> InfidelityConfig.DIAPHRAGM_FAILURE.get();
            case MALE_CONDOM -> InfidelityConfig.MALE_CONDOM_FAILURE.get();
            case FEMALE_CONDOM -> InfidelityConfig.FEMALE_CONDOM_FAILURE.get();
            case SAFE_DAY -> 1.0;
            case NOT_NEEDED -> 0.0;
        };
        return random.nextDouble() < chance;
    }

    // =====================================================================
    // Birth control
    // =====================================================================

    public static boolean onBirthControl(LivingEntity entity) {
        return entity.hasData(BIRTH_CONTROL_UNTIL) && entity.getData(BIRTH_CONTROL_UNTIL) > gameTime(entity);
    }

    /** Takes a pill: protected for the configured number of days from now (doesn't stack beyond that). */
    public static void takePill(LivingEntity entity) {
        long until = gameTime(entity) + InfidelityConfig.BIRTH_CONTROL_DAYS.get() * 24000L;
        entity.setData(BIRTH_CONTROL_UNTIL, Math.max(until, entity.hasData(BIRTH_CONTROL_UNTIL) ? entity.getData(BIRTH_CONTROL_UNTIL) : 0L));
    }

    public static long pillDaysLeft(LivingEntity entity) {
        if (!onBirthControl(entity)) return 0;
        return (entity.getData(BIRTH_CONTROL_UNTIL) - gameTime(entity) + 23999) / 24000L;
    }

    private static long gameTime(LivingEntity entity) {
        var server = entity.getServer();
        return server != null ? server.overworld().getGameTime() : entity.level().getGameTime();
    }

    // =====================================================================
    // Where items can be
    // =====================================================================

    /** In a player's inventory or pelvis slots, or in a villager's MCA inventory. */
    static Optional<ItemStack> carried(LivingEntity entity, Predicate<ItemStack> filter) {
        Optional<SlotResult> worn = curio(entity, filter);
        if (worn.isPresent()) return Optional.of(worn.get().stack());

        Container container = entity instanceof Player p ? p.getInventory()
            : entity instanceof VillagerEntityMCA v ? v.getInventory() : null;
        if (container == null) return Optional.empty();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && filter.test(stack)) return Optional.of(stack);
        }
        return Optional.empty();
    }

    static Optional<SlotResult> curio(LivingEntity entity, Predicate<ItemStack> filter) {
        try {
            return CuriosApi.getCuriosInventory(entity).flatMap(h -> h.findFirstCurio(filter));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    static void gray(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }
}
