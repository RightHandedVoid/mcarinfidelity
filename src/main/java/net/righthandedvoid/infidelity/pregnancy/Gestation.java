package net.righthandedvoid.infidelity.pregnancy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One pregnancy, stored as a data attachment on the carrier (player or villager).
 *
 * The babies are created as MCA baby items at the moment of conception, while both parents are
 * present. That locks in genetics, traits and the real parents (MCA records them by UUID), so birth
 * works even if the father is offline or unloaded by then. Townstead's inheritance still applies
 * when the baby item grows up.
 *
 * Mutable on purpose: it's updated every tick. Server side only.
 */
public final class Gestation {

    public enum Phase {
        GESTATING,
        /** Villager mothers only: born, waiting for the father to be online to receive the baby. */
        AWAITING_FATHER;

        static Phase parse(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException e) {
                return GESTATING;
            }
        }
    }

    public static final Codec<Gestation> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("father").forGetter(g -> g.father),
        Codec.STRING.fieldOf("father_name").forGetter(g -> g.fatherName),
        Codec.BOOL.fieldOf("father_is_player").forGetter(g -> g.fatherIsPlayer),
        Codec.INT.fieldOf("elapsed").forGetter(g -> g.elapsed),
        Codec.INT.fieldOf("duration").forGetter(g -> g.duration),
        ItemStack.CODEC.listOf().fieldOf("babies").forGetter(g -> g.babies),
        Codec.BOOL.optionalFieldOf("noticed", false).forGetter(g -> g.noticed),
        Codec.STRING.xmap(Phase::parse, Phase::name).optionalFieldOf("phase", Phase.GESTATING).forGetter(g -> g.phase)
    ).apply(i, Gestation::new));

    public final UUID father;
    public final String fatherName;
    public final boolean fatherIsPlayer;
    int elapsed;
    final int duration;
    final List<ItemStack> babies;
    boolean noticed;
    Phase phase;

    Gestation(UUID father, String fatherName, boolean fatherIsPlayer, int elapsed, int duration,
              List<ItemStack> babies, boolean noticed, Phase phase) {
        this.father = father;
        this.fatherName = fatherName;
        this.fatherIsPlayer = fatherIsPlayer;
        this.elapsed = elapsed;
        this.duration = Math.max(1, duration);
        this.babies = new ArrayList<>(babies);
        this.noticed = noticed;
        this.phase = phase;
    }

    /** Never stored; only exists because attachment types need a default. Always check hasData first. */
    static Gestation placeholder() {
        return new Gestation(new UUID(0, 0), "", false, 0, 1, List.of(), false, Phase.GESTATING);
    }

    public int elapsed() { return elapsed; }
    public int duration() { return duration; }
    public int babyCount() { return babies.size(); }
    public Phase phase() { return phase; }
    public float progress() { return Math.min(1f, elapsed / (float) duration); }
}
