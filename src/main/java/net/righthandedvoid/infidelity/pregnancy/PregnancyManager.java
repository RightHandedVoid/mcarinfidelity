package net.righthandedvoid.infidelity.pregnancy;

import com.aetherianartificer.townstead.root.reproduction.Fertility;
import com.aetherianartificer.townstead.root.reproduction.LitterSize;
import com.aetherianartificer.townstead.root.reproduction.SpeciesBreeding;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.item.BabyItem;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.righthandedvoid.infidelity.InfidelityConfig;
import net.righthandedvoid.infidelity.MCARInfidelity;

import java.util.ArrayList;
import java.util.List;

/**
 * Conception, gestation and birth for every pairing the mod allows:
 *   male player + female villager  -> she carries; at birth the baby item goes to the father
 *   female player + male villager  -> she carries; at birth the baby item goes to her
 *   female player + male player    -> she carries; at birth the baby item goes to her
 *
 * Conception reuses MCA's BabyItem.createItem, so genetics, traits and the real parents in the family
 * tree all come from MCA, and Townstead's inheritance runs when the baby grows up. Townstead's fertility
 * gene and species barrier are checked before anything happens.
 *
 * Server side only. Ticked from PregnancyEvents.
 */
public final class PregnancyManager {

    public enum Result {
        CONCEIVED, NO_CARRIER, ALREADY_PREGNANT, RECOVERING, INFERTILE, INCOMPATIBLE, CHANCE_FAILED, ERROR;

        public boolean conceived() { return this == CONCEIVED; }
    }

    private static final int TICKS_PER_DAY = 24000;
    private static final int MAX_LITTER = 8;

    private PregnancyManager() {}

    // =====================================================================
    // Conception
    // =====================================================================

    /**
     * Called after an unprotected session completes. Silent: nobody is told whether it worked.
     * ignoreChance is for debug commands; every other gate still applies.
     */
    public static Result tryConceive(LivingEntity a, LivingEntity b, boolean ignoreChance) {
        try {
            Gender ga = genderOf(a);
            Gender gb = genderOf(b);
            LivingEntity carrier;
            LivingEntity other;
            if (ga == Gender.FEMALE && gb == Gender.MALE) {
                carrier = a;
                other = b;
            } else if (gb == Gender.FEMALE && ga == Gender.MALE) {
                carrier = b;
                other = a;
            } else {
                return Result.NO_CARRIER;
            }

            if (PregnancyData.isPregnant(carrier) || carrier.hasData(PregnancyData.GESTATION)) {
                return Result.ALREADY_PREGNANT;
            }
            if (PregnancyData.recoveryTicks(carrier) > 0) return Result.RECOVERING;

            if (hasInfertileTrait(carrier) || hasInfertileTrait(other)) return Result.INFERTILE;
            if (!Fertility.canBreed(carrier, other)) return Result.INFERTILE;
            if (!SpeciesBreeding.sameSpecies(carrier, other)) return Result.INCOMPATIBLE;

            RandomSource random = carrier.getRandom();
            if (!ignoreChance && random.nextDouble() >= InfidelityConfig.CONCEPTION_CHANCE.get()) {
                return Result.CHANCE_FAILED;
            }

            int count = litterSize(carrier, random);
            List<ItemStack> babies = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                // Mother first, father second: MCA stores them in that order on the item.
                babies.add(BabyItem.createItem(carrier, other, random.nextLong()));
            }

            int duration = InfidelityConfig.GESTATION_DAYS.get() * TICKS_PER_DAY;
            carrier.setData(PregnancyData.GESTATION, new Gestation(other.getUUID(), other.getName().getString(),
                other instanceof Player, 0, duration, babies, false, Gestation.Phase.GESTATING));
            return Result.CONCEIVED;
        } catch (Throwable t) {
            // An MCA/Townstead API changed. Fail closed: no pregnancy rather than a broken one.
            MCARInfidelity.LOGGER.warn("Conception check failed", t);
            return Result.ERROR;
        }
    }

    public static Gender genderOf(LivingEntity entity) {
        if (entity instanceof VillagerEntityMCA villager) return villager.getGenetics().getGender();
        if (entity instanceof ServerPlayer player) return PlayerSaveData.get(player).getGender();
        return Gender.NEUTRAL;
    }

    private static boolean hasInfertileTrait(LivingEntity entity) {
        return entity instanceof VillagerEntityMCA villager && villager.getTraits().hasTrait(Traits.INFERTILE);
    }

    /** Townstead's litter gene if it has an opinion, else MCA's twin chance. */
    private static int litterSize(LivingEntity carrier, RandomSource random) {
        try {
            return Mth.clamp(LitterSize.roll(carrier, random), 1, MAX_LITTER);
        } catch (Throwable t) {
            int count = 1;
            while (count < MAX_LITTER && random.nextFloat() < Config.getInstance().twinBabyChance) count++;
            return count;
        }
    }

    // =====================================================================
    // Ticking
    // =====================================================================

    static void tick(LivingEntity entity) {
        if (entity.level().isClientSide) return;

        if (entity.hasData(PregnancyData.RECOVERY)) {
            int left = entity.getData(PregnancyData.RECOVERY) - 1;
            if (left <= 0) entity.removeData(PregnancyData.RECOVERY);
            else entity.setData(PregnancyData.RECOVERY, left);
        }

        if (!entity.hasData(PregnancyData.GESTATION)) return;
        Gestation g = entity.getData(PregnancyData.GESTATION);

        if (g.phase == Gestation.Phase.GESTATING) {
            g.elapsed++;
            if (entity.tickCount % 20 != 0) return;

            if (!g.noticed && g.elapsed >= g.duration / 4) {
                g.noticed = true;
                if (entity instanceof ServerPlayer player && InfidelityConfig.NOTIFY_PREGNANT_PLAYERS.get()) {
                    gray(player, "You've been feeling different lately...");
                }
            }
            if (g.elapsed >= g.duration) giveBirth(entity, g);
        } else if (entity.tickCount % 20 == 0) {
            tryHandOver(entity, g);
        }
    }

    /** Debug: jump to the end of gestation. Birth happens within a second. */
    public static boolean makeDue(LivingEntity entity) {
        if (!PregnancyData.isGestating(entity)) return false;
        Gestation g = entity.getData(PregnancyData.GESTATION);
        g.elapsed = g.duration;
        return true;
    }

    /** Debug: remove all pregnancy state. The unborn baby items are discarded. */
    public static void clear(LivingEntity entity) {
        entity.removeData(PregnancyData.GESTATION);
        entity.removeData(PregnancyData.RECOVERY);
    }

    // =====================================================================
    // Birth
    // =====================================================================

    private static void giveBirth(LivingEntity mother, Gestation g) {
        startRecovery(mother);

        if (mother instanceof ServerPlayer player) {
            deliver(player, g.babies);
            mother.removeData(PregnancyData.GESTATION);
            gold(player, "You gave birth to " + describe(g.babies) + "!");
            return;
        }

        // Villager mother: MCA's convention for player fathers is that the baby goes to the father.
        g.phase = Gestation.Phase.AWAITING_FATHER;
        tryHandOver(mother, g);
    }

    /** Villager mothers: give the baby to the father once he's online. Waits otherwise. */
    private static void tryHandOver(LivingEntity mother, Gestation g) {
        if (!g.fatherIsPlayer) {
            // Not reachable with current pairings; keep the baby with the mother rather than lose it.
            g.babies.forEach(mother::spawnAtLocation);
            mother.removeData(PregnancyData.GESTATION);
            return;
        }
        if (mother.getServer() == null) return;
        ServerPlayer father = mother.getServer().getPlayerList().getPlayer(g.father);
        if (father == null) return;

        deliver(father, g.babies);
        mother.removeData(PregnancyData.GESTATION);
        gold(father, mother.getName().getString() + " gave birth to " + describe(g.babies)
            + ", and the baby is now in your care.");
    }

    private static void deliver(ServerPlayer player, List<ItemStack> babies) {
        for (ItemStack stack : babies) {
            ItemStack copy = stack.copy();
            // Spawn as an item entity if the inventory is full; MCA blocks the normal "drop" path for babies.
            if (!player.getInventory().add(copy) && !copy.isEmpty()) player.spawnAtLocation(copy);
        }
    }

    private static void startRecovery(LivingEntity mother) {
        int ticks = InfidelityConfig.RECOVERY_DAYS.get() * TICKS_PER_DAY;
        if (ticks > 0) mother.setData(PregnancyData.RECOVERY, ticks);
    }

    private static String describe(List<ItemStack> babies) {
        int boys = 0;
        for (ItemStack stack : babies) {
            if (stack.getItem() instanceof BabyItem baby && baby.getGender() == Gender.MALE) boys++;
        }
        int girls = babies.size() - boys;
        if (babies.size() == 1) return boys == 1 ? "a baby boy" : "a baby girl";
        String kind = babies.size() == 2 ? "twins" : babies.size() == 3 ? "triplets" : babies.size() + " babies";
        return kind + " (" + boys + (boys == 1 ? " boy, " : " boys, ") + girls + (girls == 1 ? " girl)" : " girls)");
    }

    private static void gray(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    private static void gold(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GOLD));
    }
}
