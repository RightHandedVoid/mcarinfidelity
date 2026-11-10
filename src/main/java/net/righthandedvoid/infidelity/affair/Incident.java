package net.righthandedvoid.infidelity.affair;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One act of cheating, from one wronged villager's point of view. A session can create two
 * (e.g. a married player with a married villager: each of their spouses is wronged).
 *
 * Every incident is recorded when the session starts, discovered or not. Witnessed discovery
 * (this step) can reveal it right away; gossip, evidence and chance (step 7) work from the
 * undiscovered ones, and gossip will use the witness list.
 */
public final class Incident {

    public enum Tier { PROMISED, COMMITTED }   // engaged and married count the same

    public enum Channel { NONE, WITNESSED, GOSSIP, EVIDENCE, CHANCE }

    public final UUID id;
    public final long gameTime;
    /** Always a villager: player spouses are left to the players. */
    public final UUID wronged;
    public final UUID cheater;
    public final boolean cheaterIsPlayer;
    /** The person they cheated with. */
    public final UUID other;
    public final boolean otherIsPlayer;
    public final Tier tier;
    public final boolean cheaterInitiated;
    public final boolean protectionUsed;

    final Set<UUID> witnesses;
    Channel discoveredBy;

    Incident(UUID id, long gameTime, UUID wronged, UUID cheater, boolean cheaterIsPlayer, UUID other,
             boolean otherIsPlayer, Tier tier, boolean cheaterInitiated, boolean protectionUsed,
             Set<UUID> witnesses, Channel discoveredBy) {
        this.id = id;
        this.gameTime = gameTime;
        this.wronged = wronged;
        this.cheater = cheater;
        this.cheaterIsPlayer = cheaterIsPlayer;
        this.other = other;
        this.otherIsPlayer = otherIsPlayer;
        this.tier = tier;
        this.cheaterInitiated = cheaterInitiated;
        this.protectionUsed = protectionUsed;
        this.witnesses = new HashSet<>(witnesses);
        this.discoveredBy = discoveredBy;
    }

    public boolean discovered() { return discoveredBy != Channel.NONE; }
    public Channel discoveredBy() { return discoveredBy; }
    public Set<UUID> witnesses() { return Set.copyOf(witnesses); }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putLong("time", gameTime);
        tag.putUUID("wronged", wronged);
        tag.putUUID("cheater", cheater);
        tag.putBoolean("cheaterIsPlayer", cheaterIsPlayer);
        tag.putUUID("other", other);
        tag.putBoolean("otherIsPlayer", otherIsPlayer);
        tag.putString("tier", tier.name());
        tag.putBoolean("cheaterInitiated", cheaterInitiated);
        tag.putBoolean("protection", protectionUsed);
        ListTag list = new ListTag();
        for (UUID w : witnesses) list.add(NbtUtils.createUUID(w));
        tag.put("witnesses", list);
        tag.putString("discoveredBy", discoveredBy.name());
        return tag;
    }

    static Incident load(CompoundTag tag) {
        Set<UUID> witnesses = new HashSet<>();
        for (Tag t : tag.getList("witnesses", Tag.TAG_INT_ARRAY)) witnesses.add(NbtUtils.loadUUID(t));
        return new Incident(tag.getUUID("id"), tag.getLong("time"), tag.getUUID("wronged"), tag.getUUID("cheater"),
            tag.getBoolean("cheaterIsPlayer"), tag.getUUID("other"), tag.getBoolean("otherIsPlayer"),
            parse(Tier.class, tag.getString("tier"), Tier.COMMITTED), tag.getBoolean("cheaterInitiated"),
            tag.getBoolean("protection"), witnesses, parse(Channel.class, tag.getString("discoveredBy"), Channel.NONE));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
