package net.righthandedvoid.infidelity.affair;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.righthandedvoid.infidelity.InfidelityConfig;
import net.righthandedvoid.infidelity.MCARInfidelity;
import net.righthandedvoid.infidelity.intimacy.Privacy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Detects cheating, records it, and handles discovery and its consequences.
 *
 * Who counts as wronged: the partner (promised, engaged or married) of either participant, if that partner
 * is a villager. A player partner is left out on purpose: the mod doesn't report one player's private
 * activity to another; a child's family tree can still reveal it.
 *
 * Severity of one discovered incident = tier x who initiated x how it was found x repetition.
 * The wronged villager's grievance toward the cheater grows by that much, and toward the person they
 * cheated with by a share of it. Grievance costs hearts, blocks intimacy until it fades, and past a
 * threshold (scaled by her forgiveness) she ends the relationship.
 *
 * Server thread only.
 */
public final class AffairManager {

    /** Incidents being created by each active session, keyed by session id. */
    private static final Map<UUID, List<UUID>> SESSION_INCIDENTS = new HashMap<>();

    private AffairManager() {}

    // =====================================================================
    // Session hooks (called by SessionManager)
    // =====================================================================

    /** Records an incident for every villager who's being cheated on by this session. */
    public static void onSessionStart(UUID sessionId, UUID a, UUID b, UUID initiator, boolean protectionUsed,
                                      MinecraftServer server) {
        try {
            FamilyTree tree = FamilyTree.get(server.overworld());
            long time = server.overworld().getGameTime();
            List<UUID> ids = new ArrayList<>();
            AffairLedger ledger = AffairLedger.get(server);

            for (UUID[] pair : new UUID[][]{{a, b}, {b, a}}) {
                UUID self = pair[0];
                UUID with = pair[1];
                Optional<FamilyTreeNode> selfNode = tree.getOrEmpty(self);
                if (selfNode.isEmpty()) continue;

                UUID partner = selfNode.get().partner();
                if (partner == null || partner.equals(net.minecraft.Util.NIL_UUID) || partner.equals(with)) continue;

                Incident.Tier tier = tierOf(selfNode.get().getRelationshipState());
                if (tier == null) continue;   // single or widowed

                Optional<FamilyTreeNode> partnerNode = tree.getOrEmpty(partner);
                if (partnerNode.isEmpty() || partnerNode.get().isPlayer()) continue;   // player partners: their business

                boolean withIsPlayer = tree.getOrEmpty(with).map(FamilyTreeNode::isPlayer)
                    .orElse(server.getPlayerList().getPlayer(with) != null);

                Incident incident = new Incident(UUID.randomUUID(), time, partner, self, selfNode.get().isPlayer(),
                    with, withIsPlayer, tier, self.equals(initiator), protectionUsed, Set.of(), Incident.Channel.NONE);
                ledger.add(incident);
                ids.add(incident.id);
            }
            if (!ids.isEmpty()) SESSION_INCIDENTS.put(sessionId, ids);
        } catch (Throwable t) {
            MCARInfidelity.LOGGER.warn("Couldn't check a session for infidelity", t);
        }
    }

    /**
     * Once a second during a session: anyone watching? Villager onlookers are noted as witnesses (for gossip
     * later). If the wronged spouse is one of them, it's discovered on the spot and the session ends.
     */
    public static boolean checkWitnesses(UUID sessionId, LivingEntity a, LivingEntity b, MinecraftServer server) {
        List<UUID> ids = SESSION_INCIDENTS.get(sessionId);
        if (ids == null) return false;

        List<LivingEntity> watching = Privacy.onlookers(a, b);
        if (watching.isEmpty()) return false;

        AffairLedger ledger = AffairLedger.get(server);
        boolean caught = false;
        for (UUID id : ids) {
            Incident incident = ledger.get(id);
            if (incident == null || incident.discovered()) continue;

            for (LivingEntity onlooker : watching) {
                if (!(onlooker instanceof VillagerEntityMCA villager)) continue;
                if (villager.getUUID().equals(incident.wronged)) {
                    discover(incident, Incident.Channel.WITNESSED, server);
                    caught = true;
                } else if (incident.witnesses.add(villager.getUUID())) {
                    ledger.setDirty();
                }
            }
        }
        return caught;
    }

    public static void onSessionEnd(UUID sessionId) {
        SESSION_INCIDENTS.remove(sessionId);
    }

    public static void clear() {
        SESSION_INCIDENTS.clear();
    }

    // =====================================================================
    // Discovery and consequences
    // =====================================================================

    /** Reveal an incident to the wronged villager and apply the consequences. Needs her loaded. */
    public static void discover(Incident incident, Incident.Channel channel, MinecraftServer server) {
        if (incident.discovered()) return;
        incident.discoveredBy = channel;
        AffairLedger.get(server).setDirty();

        Entity entity = findEntity(server, incident.wronged);
        if (!(entity instanceof VillagerEntityMCA wronged)) return;   // recorded as discovered; nothing to act on

        double severity = severity(incident, channel, Grievances.offenses(wronged, incident.cheater));
        double otherShare = severity * InfidelityConfig.OTHER_PARTY_SHARE.get();

        double cheaterScore = Grievances.add(wronged, incident.cheater, severity, true);
        Grievances.add(wronged, incident.other, otherShare, true);

        var brain = wronged.getVillagerBrain();
        brain.modifyMoodValue(-(int) Math.min(15, Math.round(severity * 2)));

        int heartsPerSeverity = InfidelityConfig.HEARTS_PER_SEVERITY.get();
        ServerPlayer cheaterPlayer = incident.cheaterIsPlayer ? server.getPlayerList().getPlayer(incident.cheater) : null;
        ServerPlayer otherPlayer = incident.otherIsPlayer ? server.getPlayerList().getPlayer(incident.other) : null;
        if (cheaterPlayer != null) brain.getMemoriesForPlayer(cheaterPlayer).modHearts(-(int) Math.round(severity * heartsPerSeverity));
        if (otherPlayer != null) brain.getMemoriesForPlayer(otherPlayer).modHearts(-(int) Math.round(otherShare * heartsPerSeverity));

        // A caught villager cheater feels it too.
        if (!incident.cheaterIsPlayer && findEntity(server, incident.cheater) instanceof VillagerEntityMCA cheaterVillager) {
            cheaterVillager.getVillagerBrain().modifyMoodValue(-3);
        }

        String wrongedName = wronged.getName().getString();
        String cheaterName = nameOf(server, incident.cheater);
        String otherName = nameOf(server, incident.other);
        String how = channel == Incident.Channel.WITNESSED ? " saw " : " found out about ";

        if (cheaterPlayer != null) red(cheaterPlayer, wrongedName + how + "you with " + otherName + ".");
        if (otherPlayer != null) {
            red(otherPlayer, wrongedName + ", " + cheaterName + "'s " + partnerWord(wronged, incident.tier) + "," + how
                + "the two of you" + (channel == Incident.Channel.WITNESSED ? " together." : "."));
        }

        double threshold = InfidelityConfig.BREAKUP_THRESHOLD.get() * Grievances.forgiveness(wronged);
        if (cheaterScore >= threshold) breakUp(wronged, incident, server, cheaterPlayer, otherPlayer, cheaterName);
    }

    public static double severity(Incident incident, Incident.Channel channel, int priorOffenses) {
        double base = incident.tier == Incident.Tier.PROMISED
            ? InfidelityConfig.PROMISED_SEVERITY.get()
            : InfidelityConfig.COMMITTED_SEVERITY.get();
        double initiated = incident.cheaterInitiated
            ? InfidelityConfig.INITIATED_MULTIPLIER.get()
            : InfidelityConfig.WAS_ASKED_MULTIPLIER.get();
        double found = channel == Incident.Channel.WITNESSED ? InfidelityConfig.WITNESSED_MULTIPLIER.get() : 1.0;
        double repeat = 1.0 + InfidelityConfig.REPEAT_STEP.get() * priorOffenses;
        return base * initiated * found * repeat;
    }

    private static void breakUp(VillagerEntityMCA wronged, Incident incident, MinecraftServer server,
                                ServerPlayer cheaterPlayer, ServerPlayer otherPlayer, String cheaterName) {
        FamilyTree tree = FamilyTree.get(server.overworld());
        Optional<FamilyTreeNode> node = tree.getOrEmpty(wronged.getUUID());
        // Still together? (Someone may have ended it already.)
        if (node.isEmpty() || !incident.cheater.equals(node.get().partner())) return;

        // Clears both sides in MCA's family tree, players included (same as MCA's own divorce).
        node.get().updatePartner(null, RelationshipState.SINGLE);

        String name = wronged.getName().getString();
        if (cheaterPlayer != null) red(cheaterPlayer, name + " has ended things with you.");
        if (otherPlayer != null) gray(otherPlayer, name + " has left " + cheaterName + ".");
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static Incident.Tier tierOf(RelationshipState state) {
        if (state == null) return null;
        return switch (state) {
            case PROMISED -> Incident.Tier.PROMISED;
            case ENGAGED, MARRIED_TO_VILLAGER, MARRIED_TO_PLAYER -> Incident.Tier.COMMITTED;
            default -> null;
        };
    }

    private static String partnerWord(VillagerEntityMCA wronged, Incident.Tier tier) {
        Gender g = wronged.getGenetics().getGender();
        if (tier == Incident.Tier.PROMISED) return "partner";
        return g == Gender.FEMALE ? "wife" : g == Gender.MALE ? "husband" : "spouse";
    }

    private static String nameOf(MinecraftServer server, UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) return player.getName().getString();
        Entity entity = findEntity(server, uuid);
        if (entity != null) return entity.getName().getString();
        return FamilyTree.get(server.overworld()).getOrEmpty(uuid).map(FamilyTreeNode::getName).orElse("someone");
    }

    static Entity findEntity(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e != null) return e;
        }
        return null;
    }

    private static void red(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.RED));
    }

    private static void gray(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }
}
