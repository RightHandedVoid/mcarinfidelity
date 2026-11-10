package net.righthandedvoid.infidelity.affair;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.saveddata.SavedData;
import net.righthandedvoid.infidelity.InfidelityConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Exile from an MCA village. Permanent: there's no way back in normal play (ops can pardon with the debug
 * command). Exiled players can't talk to, trade with, marry or be intimate with that village's villagers,
 * its guards turn hostile, and they're told to leave whenever they're inside its borders.
 *
 * Triggered when a player's discovered offenses in one village add up past a threshold AND involve at least
 * a minimum number of different people: repeated cheating with multiple partners, not one bad mistake.
 */
public final class Exile extends SavedData {

    private static final String NAME = "mcarinfidelity_exiles";

    /** Village id -> exiled player UUIDs. */
    private final Map<Integer, Set<UUID>> exiles = new HashMap<>();
    /** Not saved: player -> server tick of their last "leave" warning. */
    private static final Map<UUID, Long> LAST_WARNING = new HashMap<>();

    public static Exile get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(new SavedData.Factory<>(Exile::new, Exile::load, null), NAME);
    }

    // =====================================================================
    // Queries
    // =====================================================================

    public boolean isExiled(UUID player, int villageId) {
        Set<UUID> set = exiles.get(villageId);
        return set != null && set.contains(player);
    }

    /** Whether this villager's home village has exiled the player. */
    public static boolean isExiledFrom(MinecraftServer server, UUID player, VillagerEntityMCA villager) {
        try {
            Optional<Village> village = villager.getResidency().getHomeVillage();
            return village.isPresent() && get(server).isExiled(player, village.get().getId());
        } catch (Throwable t) {
            return false;
        }
    }

    public Set<Integer> villagesExiling(UUID player) {
        Set<Integer> out = new HashSet<>();
        exiles.forEach((id, set) -> { if (set.contains(player)) out.add(id); });
        return out;
    }

    // =====================================================================
    // Changes
    // =====================================================================

    /**
     * Re-evaluate after a discovery in this village. Counts the player's discovered incidents there:
     * full severity as the cheater, the configured share as the person they cheated with.
     */
    static void evaluate(MinecraftServer server, UUID player, int villageId) {
        if (villageId < 0) return;
        Exile exile = get(server);
        if (exile.isExiled(player, villageId)) return;

        double total = 0;
        Set<UUID> partners = new HashSet<>();
        double share = InfidelityConfig.OTHER_PARTY_SHARE.get();
        for (Incident i : AffairLedger.get(server).involving(player)) {
            if (!i.discovered() || i.villageId != villageId) continue;
            if (i.cheater.equals(player)) {
                total += i.severity;
                partners.add(i.other);
            } else if (i.other.equals(player)) {
                total += i.severity * share;
                partners.add(i.cheater);
            }
        }
        if (total >= InfidelityConfig.EXILE_THRESHOLD.get() && partners.size() >= InfidelityConfig.EXILE_MIN_PARTNERS.get()) {
            exile.exiles.computeIfAbsent(villageId, k -> new HashSet<>()).add(player);
            exile.setDirty();
            ServerPlayer online = server.getPlayerList().getPlayer(player);
            if (online != null) {
                String name = villageName(server, villageId);
                online.sendSystemMessage(Component.literal("You have been exiled from " + name
                    + ". Nobody there will speak with you again.").withStyle(ChatFormatting.DARK_RED));
            }
        }
    }

    public boolean pardon(UUID player, Integer villageId) {
        boolean changed = false;
        for (Map.Entry<Integer, Set<UUID>> e : exiles.entrySet()) {
            if (villageId == null || e.getKey().equals(villageId)) changed |= e.getValue().remove(player);
        }
        if (changed) setDirty();
        return changed;
    }

    // =====================================================================
    // Enforcement (ticked from AffairEvents)
    // =====================================================================

    /** Every 2 seconds per exiled player inside a village that exiled them: warn, and set the guards on them. */
    static void enforce(MinecraftServer server, ServerPlayer player) {
        Exile exile = get(server);
        Set<Integer> villages = exile.villagesExiling(player.getUUID());
        if (villages.isEmpty() || player.isCreative() || player.isSpectator()) return;

        ServerLevel level = player.serverLevel();
        VillageManager manager = VillageManager.get(level);
        for (int id : villages) {
            Optional<Village> village = manager.getOrEmpty(id);
            if (village.isEmpty() || !village.get().isWithinBorder(player)) continue;

            long now = server.getTickCount();
            Long last = LAST_WARNING.get(player.getUUID());
            if (last == null || now - last >= InfidelityConfig.ticks(InfidelityConfig.EXILE_WARN_SECONDS)) {
                LAST_WARNING.put(player.getUUID(), now);
                player.displayClientMessage(Component.literal("You are not welcome in " + village.get().getName() + ". Leave.")
                    .withStyle(ChatFormatting.DARK_RED), true);
            }

            if (InfidelityConfig.EXILE_GUARDS_HOSTILE.get()) {
                for (VillagerEntityMCA resident : village.get().getResidents(level)) {
                    if (resident.isGuard() && resident.distanceTo(player) < 32) {
                        resident.setTarget(player);
                        resident.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, player);
                    }
                }
            }
        }
    }

    static void clearRuntime() {
        LAST_WARNING.clear();
    }

    public static String villageName(MinecraftServer server, int id) {
        for (ServerLevel level : server.getAllLevels()) {
            Optional<Village> v = VillageManager.get(level).getOrEmpty(id);
            if (v.isPresent()) return v.get().getName();
        }
        return "the village";
    }

    // =====================================================================
    // Saving
    // =====================================================================

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        exiles.forEach((id, set) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("village", id);
            ListTag players = new ListTag();
            for (UUID p : set) players.add(NbtUtils.createUUID(p));
            entry.put("players", players);
            list.add(entry);
        });
        tag.put("exiles", list);
        return tag;
    }

    public static Exile load(CompoundTag tag, HolderLookup.Provider provider) {
        Exile exile = new Exile();
        for (Tag t : tag.getList("exiles", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            Set<UUID> players = new HashSet<>();
            for (Tag p : entry.getList("players", Tag.TAG_INT_ARRAY)) players.add(NbtUtils.loadUUID(p));
            exile.exiles.put(entry.getInt("village"), players);
        }
        return exile;
    }
}
