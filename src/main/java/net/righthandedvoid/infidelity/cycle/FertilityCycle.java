package net.righthandedvoid.infidelity.cycle;

import com.mojang.serialization.Codec;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.righthandedvoid.infidelity.InfidelityConfig;
import net.righthandedvoid.infidelity.MCARInfidelity;
import net.righthandedvoid.infidelity.pregnancy.PregnancyData;
import net.righthandedvoid.infidelity.pregnancy.PregnancyManager;

import java.util.function.Supplier;

/**
 * The fertility cycle for female players and villagers.
 *
 * Nothing ticks: the day is worked out from the world's day count plus a random per-person offset,
 * so everyone is at a different point, unloaded villagers stay in sync, and sleeping advances it.
 * The cycle pauses (reports PAUSED) while pregnant or recovering after a birth.
 *
 * Proportions are based on a 28-day cycle and scale with the configured length:
 * menstruation is the first ~17% of days, ovulation is mid-cycle, and the fertile window is
 * ovulation +/- ~10% of the cycle.
 */
public final class FertilityCycle {

    public enum Phase {
        MENSTRUAL("Menstruating"),
        FOLLICULAR("Before the fertile window"),
        FERTILE("Fertile"),
        OVULATION("Ovulating (most fertile)"),
        LUTEAL("After the fertile window"),
        /** Pregnant or recovering: no cycle right now. */
        PAUSED("No cycle right now"),
        /** Not female: no cycle at all. */
        NONE("No cycle");

        public final String label;

        Phase(String label) { this.label = label; }

        public boolean fertile() { return this == FERTILE || this == OVULATION; }
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MCARInfidelity.MODID);

    /** Random seed for where in the cycle this person is. -1 = not rolled yet. */
    public static final Supplier<AttachmentType<Integer>> OFFSET = ATTACHMENT_TYPES.register("cycle_offset",
        () -> AttachmentType.builder(() -> -1)
            .serialize(Codec.INT)
            .copyOnDeath()
            .build());

    private static final int OFFSET_RANGE = 10_000;   // taken mod the cycle length, so changing the length keeps people spread out

    private FertilityCycle() {}

    // =====================================================================
    // Where in the cycle
    // =====================================================================

    public static boolean hasCycle(LivingEntity entity) {
        return PregnancyManager.genderOf(entity) == Gender.FEMALE;
    }

    public static int length() {
        return InfidelityConfig.CYCLE_LENGTH_DAYS.get();
    }

    /** 1-based day of the cycle. Only meaningful if hasCycle(). */
    public static int day(LivingEntity entity) {
        long worldDay = worldDay(entity);
        return (int) Math.floorMod(worldDay + offset(entity), (long) length()) + 1;
    }

    public static Phase phase(LivingEntity entity) {
        if (!hasCycle(entity)) return Phase.NONE;
        if (PregnancyData.isPregnant(entity) || entity.hasData(PregnancyData.GESTATION)
            || PregnancyData.recoveryTicks(entity) > 0) {
            return Phase.PAUSED;
        }
        return phaseOfDay(day(entity), length());
    }

    public static Phase phaseOfDay(int day, int length) {
        int ovulation = ovulationDay(length);
        int window = fertileWindow(length);
        if (day == ovulation) return Phase.OVULATION;
        if (Math.abs(day - ovulation) <= window) return Phase.FERTILE;
        if (day <= Math.max(1, Math.round(length * 0.17f))) return Phase.MENSTRUAL;
        return day < ovulation ? Phase.FOLLICULAR : Phase.LUTEAL;
    }

    public static int ovulationDay(int length) {
        return Math.max(1, Math.round(length * 0.5f));
    }

    public static int fertileWindow(int length) {
        return Math.max(1, Math.round(length * 0.1f));
    }

    // =====================================================================
    // Effects
    // =====================================================================

    /**
     * Chance of conception right now. Peaks on ovulation day, drops to 40% of the peak at the edges
     * of the fertile window, and is the (small) base chance outside it.
     */
    public static double conceptionChance(LivingEntity carrier) {
        if (!hasCycle(carrier)) return 0;
        int length = length();
        int distance = Math.abs(day(carrier) - ovulationDay(length));
        int window = fertileWindow(length);
        if (distance > window) return InfidelityConfig.BASE_CONCEPTION_CHANCE.get();
        double factor = 1.0 - 0.6 * distance / window;
        return InfidelityConfig.PEAK_CONCEPTION_CHANCE.get() * factor;
    }

    /** Small nudge to how willing a villager is: a bit more around ovulation, a bit less while menstruating. */
    public static double willingnessModifier(LivingEntity entity) {
        return switch (phase(entity)) {
            case OVULATION -> 0.15;
            case FERTILE -> 0.08;
            case MENSTRUAL -> -0.15;
            default -> 0;
        };
    }

    // =====================================================================
    // Internals / debug
    // =====================================================================

    private static long worldDay(LivingEntity entity) {
        var server = entity.getServer();
        long time = server != null ? server.overworld().getDayTime() : entity.level().getDayTime();
        return time / 24000L;
    }

    private static int offset(LivingEntity entity) {
        int offset = entity.getData(OFFSET);
        if (offset < 0) {
            offset = entity.getRandom().nextInt(OFFSET_RANGE);
            entity.setData(OFFSET, offset);
        }
        return offset;
    }

    /** Debug: shift the offset so today is the given cycle day. */
    public static void setDay(LivingEntity entity, int day) {
        int length = length();
        int target = Math.floorMod(day - 1, length);
        int current = (int) Math.floorMod(worldDay(entity), (long) length);
        entity.setData(OFFSET, Math.floorMod(target - current, length));
    }
}
