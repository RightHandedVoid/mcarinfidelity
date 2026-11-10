package net.righthandedvoid.infidelity.affair;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Every recorded incident in the world, saved with the overworld. Server side only. */
public final class AffairLedger extends SavedData {

    private static final String NAME = "mcarinfidelity_affairs";
    private static final int MAX_INCIDENTS = 2000;

    private final Map<UUID, Incident> incidents = new LinkedHashMap<>();   // oldest first

    public static AffairLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(new SavedData.Factory<>(AffairLedger::new, AffairLedger::load, null), NAME);
    }

    void add(Incident incident) {
        incidents.put(incident.id, incident);
        prune();
        setDirty();
    }

    public Incident get(UUID id) {
        return incidents.get(id);
    }

    public Collection<Incident> all() {
        return incidents.values();
    }

    public List<Incident> involving(UUID uuid) {
        List<Incident> out = new ArrayList<>();
        for (Incident i : incidents.values()) {
            if (i.wronged.equals(uuid) || i.cheater.equals(uuid) || i.other.equals(uuid)) out.add(i);
        }
        return out;
    }

    /** Over the cap: drop the oldest discovered incidents first; undiscovered ones are still "live". */
    private void prune() {
        if (incidents.size() <= MAX_INCIDENTS) return;
        Iterator<Incident> it = incidents.values().iterator();
        while (incidents.size() > MAX_INCIDENTS && it.hasNext()) {
            if (it.next().discovered()) it.remove();
        }
        it = incidents.values().iterator();
        while (incidents.size() > MAX_INCIDENTS && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Incident i : incidents.values()) list.add(i.save());
        tag.put("incidents", list);
        return tag;
    }

    public static AffairLedger load(CompoundTag tag, HolderLookup.Provider provider) {
        AffairLedger ledger = new AffairLedger();
        for (Tag t : tag.getList("incidents", Tag.TAG_COMPOUND)) {
            try {
                Incident i = Incident.load((CompoundTag) t);
                ledger.incidents.put(i.id, i);
            } catch (Exception ignored) {
                // skip a corrupted entry rather than losing the whole ledger
            }
        }
        return ledger;
    }
}
