package net.righthandedvoid.infidelity.affair;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.core.UUIDUtil;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.righthandedvoid.infidelity.InfidelityConfig;
import net.righthandedvoid.infidelity.MCARInfidelity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * How hurt a villager is, toward each person who wronged her. Stored on the villager.
 *
 * The score fades over time (forgiveness), faster or slower depending on her personality; the offense
 * count never fades, so repeat cheating escalates. Server side only.
 */
public final class Grievances {

    public record Grievance(double score, int offenses, long updatedAt) {
        static final Codec<Grievance> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("score").forGetter(Grievance::score),
            Codec.INT.fieldOf("offenses").forGetter(Grievance::offenses),
            Codec.LONG.fieldOf("updated").forGetter(Grievance::updatedAt)
        ).apply(i, Grievance::new));
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MCARInfidelity.MODID);

    public static final Supplier<AttachmentType<Map<UUID, Grievance>>> GRIEVANCES = ATTACHMENT_TYPES.register("grievances",
        () -> AttachmentType.<Map<UUID, Grievance>>builder(() -> new HashMap<>())
            .serialize(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Grievance.CODEC).xmap(HashMap::new, m -> m))
            .build());

    private static final double TICKS_PER_DAY = 24000.0;

    private Grievances() {}

    /** Current score toward this person, after forgiveness over time. */
    public static double score(VillagerEntityMCA villager, UUID target) {
        return current(villager, target).score();
    }

    public static int offenses(VillagerEntityMCA villager, UUID target) {
        return current(villager, target).offenses();
    }

    /** Add hurt from a newly discovered incident. Returns the new score. */
    static double add(VillagerEntityMCA villager, UUID target, double amount, boolean countsAsOffense) {
        Grievance g = current(villager, target);
        Grievance updated = new Grievance(g.score() + amount, g.offenses() + (countsAsOffense ? 1 : 0), now(villager));
        villager.getData(GRIEVANCES).put(target, updated);
        villager.setData(GRIEVANCES, villager.getData(GRIEVANCES));   // mark changed
        return updated.score();
    }

    /** Too hurt to be intimate with this person. */
    public static boolean refusesIntimacy(VillagerEntityMCA villager, UUID target) {
        return villager.hasData(GRIEVANCES) && score(villager, target) >= InfidelityConfig.REFUSE_INTIMACY_GRIEVANCE.get();
    }

    public static Map<UUID, Grievance> all(VillagerEntityMCA villager) {
        if (!villager.hasData(GRIEVANCES)) return Map.of();
        Map<UUID, Grievance> out = new HashMap<>();
        for (UUID id : villager.getData(GRIEVANCES).keySet()) out.put(id, current(villager, id));
        return out;
    }

    public static void clear(VillagerEntityMCA villager) {
        villager.removeData(GRIEVANCES);
    }

    /**
     * Her forgiveness: scales both how fast hurt fades and how much she'll put up with before ending things.
     * Warm personalities forgive more easily, prickly ones less.
     */
    public static double forgiveness(VillagerEntityMCA villager) {
        Personality p;
        try {
            p = villager.getVillagerBrain().getPersonality();
        } catch (Throwable t) {
            return 1.0;
        }
        if (p == Personality.FRIENDLY || p == Personality.PEACEFUL || p == Personality.UPBEAT) return 1.4;
        if (p == Personality.RELAXED) return 1.2;
        if (p == Personality.ANXIOUS) return 0.8;
        if (p == Personality.CRABBY || p == Personality.GREEDY || p == Personality.GLOOMY) return 0.6;
        return 1.0;
    }

    private static Grievance current(VillagerEntityMCA villager, UUID target) {
        if (!villager.hasData(GRIEVANCES)) return new Grievance(0, 0, now(villager));
        Grievance g = villager.getData(GRIEVANCES).get(target);
        long now = now(villager);
        if (g == null) return new Grievance(0, 0, now);
        double days = Math.max(0, now - g.updatedAt()) / TICKS_PER_DAY;
        double decayed = Math.max(0, g.score() - days * InfidelityConfig.GRIEVANCE_DECAY_PER_DAY.get() * forgiveness(villager));
        return new Grievance(decayed, g.offenses(), now);
    }

    private static long now(VillagerEntityMCA villager) {
        var server = villager.getServer();
        return server != null ? server.overworld().getGameTime() : villager.level().getGameTime();
    }
}
