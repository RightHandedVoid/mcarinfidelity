package net.righthandedvoid.infidelity;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side gameplay tunables. Registered as a SERVER config, so each world gets its own copy
 * (world/serverconfig/mcarinfidelity-server.toml) and a dedicated server's values are the ones that apply.
 *
 * Values are read with .get() at the moment they're used, so edits take effect immediately.
 *
 * Deliberately NOT configurable: the adult check, the relatives block, and opt-in.
 * Those are hard rules in IntimacyRules and must never get a toggle here.
 */
public final class InfidelityConfig {

    private static final String KEY = MCARInfidelity.MODID + ".configuration.";

    public static final ModConfigSpec SPEC;

    // Eligibility
    public static final ModConfigSpec.IntValue MIN_HEARTS_NON_PARTNER;
    public static final ModConfigSpec.IntValue MIN_HEARTS_PARTNER;
    public static final ModConfigSpec.IntValue MIN_MOOD;
    public static final ModConfigSpec.DoubleValue MAX_DISTANCE;
    public static final ModConfigSpec.IntValue RECENT_HURT_SECONDS;

    // Consent requests
    public static final ModConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS;
    public static final ModConfigSpec.IntValue VILLAGER_THINK_SECONDS;
    public static final ModConfigSpec.IntValue DECLINE_COOLDOWN_SECONDS;

    // Villager preferences
    public static final ModConfigSpec.DoubleValue WANTS_CHILDREN_BASE_CHANCE;
    public static final ModConfigSpec.DoubleValue NON_PARTNER_UNPROTECTED_CHANCE;

    // Pregnancy
    public static final ModConfigSpec.DoubleValue CONCEPTION_CHANCE;
    public static final ModConfigSpec.IntValue GESTATION_DAYS;
    public static final ModConfigSpec.IntValue RECOVERY_DAYS;
    public static final ModConfigSpec.BooleanValue NOTIFY_PREGNANT_PLAYERS;

    // Sessions
    public static final ModConfigSpec.IntValue SESSION_DURATION_SECONDS;
    public static final ModConfigSpec.IntValue HEARTS_ON_COMPLETE;
    public static final ModConfigSpec.IntValue MOOD_ON_COMPLETE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Who is eligible and when. Age, family, and opt-in checks are fixed and not listed here.")
            .translation(KEY + "eligibility")
            .push("eligibility");

        MIN_HEARTS_NON_PARTNER = b
            .comment("Hearts a villager needs with a player who is not their partner. MCA reference: friend = 40, married = 100.")
            .translation(KEY + "minHeartsNonPartner")
            .defineInRange("minHeartsNonPartner", 75, 0, 200);

        MIN_HEARTS_PARTNER = b
            .comment("Hearts needed when the player is the villager's partner (promised, engaged, or married).")
            .translation(KEY + "minHeartsPartner")
            .defineInRange("minHeartsPartner", 25, 0, 200);

        MIN_MOOD = b
            .comment("Minimum villager mood. MCA mood runs roughly -15 to 15 in normal play.")
            .translation(KEY + "minMood")
            .defineInRange("minMood", 0, -15, 15);

        MAX_DISTANCE = b
            .comment("Maximum distance in blocks between the two participants.")
            .translation(KEY + "maxDistance")
            .defineInRange("maxDistance", 4.0, 1.0, 16.0);

        RECENT_HURT_SECONDS = b
            .comment("Seconds after taking damage during which someone counts as busy.")
            .translation(KEY + "recentHurtSeconds")
            .defineInRange("recentHurtSeconds", 10, 0, 60);

        b.pop();

        b.comment("Request and response timing.")
            .translation(KEY + "consent")
            .push("consent");

        REQUEST_TIMEOUT_SECONDS = b
            .comment("Seconds before an unanswered player-to-player request expires.")
            .translation(KEY + "requestTimeoutSeconds")
            .defineInRange("requestTimeoutSeconds", 30, 5, 300);

        VILLAGER_THINK_SECONDS = b
            .comment("Seconds a villager waits before answering a request.")
            .translation(KEY + "villagerThinkSeconds")
            .defineInRange("villagerThinkSeconds", 2, 0, 30);

        DECLINE_COOLDOWN_SECONDS = b
            .comment("Seconds before a player can ask the same target again after being declined.")
            .translation(KEY + "declineCooldownSeconds")
            .defineInRange("declineCooldownSeconds", 60, 0, 3600);

        b.pop();

        b.comment("Villagers' own preferences.")
            .translation(KEY + "villagers")
            .push("villagers");

        WANTS_CHILDREN_BASE_CHANCE = b
            .comment("Chance a villager wants children, rolled once per villager and kept. Personality shifts it by up to 0.15.",
                "Changing this only affects villagers that haven't rolled yet.")
            .translation(KEY + "wantsChildrenBaseChance")
            .defineInRange("wantsChildrenBaseChance", 0.5, 0.0, 1.0);

        NON_PARTNER_UNPROTECTED_CHANCE = b
            .comment("For villagers who want children: chance per request that they'll go without protection with someone",
                "who isn't their engaged/married partner. Villagers who don't want children always insist on protection.")
            .translation(KEY + "nonPartnerUnprotectedChance")
            .defineInRange("nonPartnerUnprotectedChance", 0.05, 0.0, 1.0);

        b.pop();

        b.comment("Conception and gestation. Times count while the carrier is loaded (villagers) or online (players).")
            .translation(KEY + "pregnancy")
            .push("pregnancy");

        CONCEPTION_CHANCE = b
            .comment("Chance of conception after a completed, unprotected session between a fertile female and male.",
                "Flat for now; the fertility cycle will replace this later.")
            .translation(KEY + "conceptionChance")
            .defineInRange("conceptionChance", 0.3, 0.0, 1.0);

        GESTATION_DAYS = b
            .comment("Length of a pregnancy in Minecraft days (1 day = 20 minutes).")
            .translation(KEY + "gestationDays")
            .defineInRange("gestationDays", 4, 1, 60);

        RECOVERY_DAYS = b
            .comment("Minecraft days after a birth before the mother can conceive again.")
            .translation(KEY + "recoveryDays")
            .defineInRange("recoveryDays", 2, 0, 30);

        NOTIFY_PREGNANT_PLAYERS = b
            .comment("Give pregnant players a subtle hint a quarter of the way through. Otherwise they find out at birth.")
            .translation(KEY + "notifyPregnantPlayers")
            .define("notifyPregnantPlayers", true);

        b.pop();

        b.comment("Active interaction settings.")
            .translation(KEY + "session")
            .push("session");

        SESSION_DURATION_SECONDS = b
            .comment("How long an interaction lasts, in seconds. Either participant can still stop at any time.")
            .translation(KEY + "sessionDurationSeconds")
            .defineInRange("sessionDurationSeconds", 20, 5, 600);

        HEARTS_ON_COMPLETE = b
            .comment("Hearts a villager gains when an interaction completes.")
            .translation(KEY + "heartsOnComplete")
            .defineInRange("heartsOnComplete", 5, 0, 50);

        MOOD_ON_COMPLETE = b
            .comment("Mood a villager gains when an interaction completes.")
            .translation(KEY + "moodOnComplete")
            .defineInRange("moodOnComplete", 3, 0, 15);

        b.pop();

        SPEC = b.build();
    }

    private InfidelityConfig() {}

    public static int ticks(ModConfigSpec.IntValue seconds) {
        return seconds.get() * 20;
    }
}
