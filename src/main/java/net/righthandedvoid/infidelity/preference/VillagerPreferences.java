package net.righthandedvoid.infidelity.preference;

import com.mojang.serialization.Codec;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.righthandedvoid.infidelity.InfidelityConfig;
import net.righthandedvoid.infidelity.MCARInfidelity;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Lasting per-villager preferences, stored as data attachments on the villager entity
 * so they save with it and don't touch MCA's own data.
 *
 * Server side only.
 */
public final class VillagerPreferences {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MCARInfidelity.MODID);

    public static final Supplier<AttachmentType<ChildPreference>> CHILD_PREFERENCE =
        ATTACHMENT_TYPES.register("child_preference", () -> AttachmentType.builder(() -> ChildPreference.UNDECIDED)
            .serialize(Codec.STRING.xmap(ChildPreference::parse, ChildPreference::name))
            .build());

    private VillagerPreferences() {}

    // =====================================================================
    // Wants children
    // =====================================================================

    /** The villager's lasting preference, rolling it on first use. */
    public static ChildPreference childPreference(VillagerEntityMCA villager) {
        ChildPreference pref = villager.getData(CHILD_PREFERENCE);
        if (pref == ChildPreference.UNDECIDED) {
            pref = roll(villager, villager.getRandom());
            villager.setData(CHILD_PREFERENCE, pref);
        }
        return pref;
    }

    public static boolean wantsChildren(VillagerEntityMCA villager) {
        return childPreference(villager) == ChildPreference.WANTS;
    }

    /** For debug commands. UNDECIDED makes it re-roll next time. */
    public static void setChildPreference(VillagerEntityMCA villager, ChildPreference pref) {
        villager.setData(CHILD_PREFERENCE, pref);
    }

    /** Base chance from config, nudged by MCA personality. */
    private static ChildPreference roll(VillagerEntityMCA villager, RandomSource random) {
        double chance = InfidelityConfig.WANTS_CHILDREN_BASE_CHANCE.get() + personalityModifier(villager);
        chance = Mth.clamp(chance, 0.05, 0.95);
        return random.nextDouble() < chance ? ChildPreference.WANTS : ChildPreference.DOES_NOT_WANT;
    }

    private static double personalityModifier(VillagerEntityMCA villager) {
        Personality p;
        try {
            p = villager.getVillagerBrain().getPersonality();
        } catch (Throwable t) {
            return 0;
        }
        if (p == Personality.FRIENDLY || p == Personality.UPBEAT || p == Personality.PEACEFUL) return 0.15;
        if (p == Personality.PLAYFUL || p == Personality.EXTROVERTED || p == Personality.RELAXED) return 0.05;
        if (p == Personality.GLOOMY || p == Personality.CRABBY || p == Personality.GREEDY) return -0.15;
        if (p == Personality.ANXIOUS || p == Personality.INTROVERTED) return -0.05;
        return 0;   // FLIRTY, ODD, and anything added by other mods
    }

    // =====================================================================
    // Protection
    // =====================================================================

    /**
     * Whether this villager insists on protection with this player, decided per request.
     *
     * A villager only goes without protection if she wants children AND either the player is her
     * engaged/married partner, or a (configurable, small) chance passes for anyone else.
     * Everyone else insists. Protection is never something a villager refuses.
     */
    public static boolean wantsProtection(VillagerEntityMCA villager, ServerPlayer player) {
        try {
            if (!wantsChildren(villager)) return true;

            UUID id = player.getUUID();
            var rel = villager.getRelationships();
            if (rel.isMarriedTo(id) || rel.isEngagedWith(id)) return false;

            return villager.getRandom().nextDouble() >= InfidelityConfig.NON_PARTNER_UNPROTECTED_CHANCE.get();
        } catch (Throwable t) {
            return true;   // fail toward protection
        }
    }
}
