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
    public static final ModConfigSpec.DoubleValue PRIVACY_HOME_CHANCE;
    public static final ModConfigSpec.DoubleValue PRIVACY_ANYWHERE_CHANCE;
    public static final ModConfigSpec.IntValue ONLOOKER_RADIUS;
    public static final ModConfigSpec.BooleanValue PRIVACY_INTERRUPTS_SESSIONS;

    // Infidelity
    public static final ModConfigSpec.DoubleValue PROMISED_SEVERITY;
    public static final ModConfigSpec.DoubleValue COMMITTED_SEVERITY;
    public static final ModConfigSpec.DoubleValue INITIATED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WAS_ASKED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WITNESSED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue REPEAT_STEP;
    public static final ModConfigSpec.DoubleValue OTHER_PARTY_SHARE;
    public static final ModConfigSpec.IntValue HEARTS_PER_SEVERITY;
    public static final ModConfigSpec.DoubleValue BREAKUP_THRESHOLD;
    public static final ModConfigSpec.DoubleValue GRIEVANCE_DECAY_PER_DAY;
    public static final ModConfigSpec.DoubleValue REFUSE_INTIMACY_GRIEVANCE;
    public static final ModConfigSpec.DoubleValue EVIDENCE_MULTIPLIER;

    // Discovery
    public static final ModConfigSpec.IntValue GOSSIP_DELAY_MINUTES;
    public static final ModConfigSpec.DoubleValue GOSSIP_CHANCE_PER_MINUTE;
    public static final ModConfigSpec.IntValue GOSSIP_RADIUS;
    public static final ModConfigSpec.DoubleValue CHANCE_DISCOVERY_PER_DAY;
    public static final ModConfigSpec.IntValue CHANCE_WINDOW_DAYS;

    // Exile
    public static final ModConfigSpec.DoubleValue EXILE_THRESHOLD;
    public static final ModConfigSpec.IntValue EXILE_MIN_PARTNERS;
    public static final ModConfigSpec.BooleanValue EXILE_GUARDS_HOSTILE;
    public static final ModConfigSpec.IntValue EXILE_WARN_SECONDS;

    // Protection
    public static final ModConfigSpec.BooleanValue ALLOW_SAFE_DAYS;
    public static final ModConfigSpec.IntValue BIRTH_CONTROL_DAYS;
    public static final ModConfigSpec.IntValue DIAPHRAGM_STIRS;
    public static final ModConfigSpec.DoubleValue MALE_CONDOM_FAILURE;
    public static final ModConfigSpec.DoubleValue FEMALE_CONDOM_FAILURE;
    public static final ModConfigSpec.DoubleValue DIAPHRAGM_FAILURE;
    public static final ModConfigSpec.DoubleValue BIRTH_CONTROL_FAILURE;

    // Villager offers
    public static final ModConfigSpec.BooleanValue VILLAGER_OFFERS;
    public static final ModConfigSpec.IntValue OFFER_CHECK_SECONDS;
    public static final ModConfigSpec.DoubleValue SPONTANEOUS_OFFER_CHANCE;
    public static final ModConfigSpec.IntValue OFFER_SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue OFFER_COOLDOWN_MINUTES;
    public static final ModConfigSpec.DoubleValue JOURNAL_OFFER_CHANCE;
    public static final ModConfigSpec.IntValue ESCORT_TIMEOUT_SECONDS;

    // Pregnancy
    public static final ModConfigSpec.DoubleValue PEAK_CONCEPTION_CHANCE;
    public static final ModConfigSpec.DoubleValue BASE_CONCEPTION_CHANCE;
    public static final ModConfigSpec.IntValue GESTATION_DAYS;
    public static final ModConfigSpec.IntValue RECOVERY_DAYS;
    public static final ModConfigSpec.BooleanValue NOTIFY_PREGNANT_PLAYERS;

    // Cycle
    public static final ModConfigSpec.IntValue CYCLE_LENGTH_DAYS;
    public static final ModConfigSpec.IntValue CYCLE_SHARE_HEARTS;

    // Sessions
    public static final ModConfigSpec.IntValue SESSION_DURATION_SECONDS;
    public static final ModConfigSpec.IntValue SKIPPED_SESSION_SECONDS;
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

        PRIVACY_HOME_CHANCE = b
            .comment("Chance a villager only wants to be intimate at her own home (with nobody watching).",
                "Rolled once per villager. The rest want somewhere private, except those who don't care (below).",
                "Personality shifts these by up to 0.15. Only affects villagers that haven't rolled yet.")
            .translation(KEY + "privacyHomeChance")
            .defineInRange("privacyHomeChance", 0.45, 0.0, 1.0);

        PRIVACY_ANYWHERE_CHANCE = b
            .comment("Chance a villager doesn't care where, or who's around.")
            .translation(KEY + "privacyAnywhereChance")
            .defineInRange("privacyAnywhereChance", 0.15, 0.0, 1.0);

        ONLOOKER_RADIUS = b
            .comment("Blocks within which another player or villager with line of sight counts as watching.")
            .translation(KEY + "onlookerRadius")
            .defineInRange("onlookerRadius", 12, 2, 48);

        PRIVACY_INTERRUPTS_SESSIONS = b
            .comment("Villagers who care about privacy stop if someone comes into view during a session.")
            .translation(KEY + "privacyInterruptsSessions")
            .define("privacyInterruptsSessions", true);

        b.pop();

        b.comment("Cheating and its consequences. Severity of a discovered incident =",
                "tier x who initiated x how it was found x (1 + repeatStep x earlier offenses against the same partner).",
                "Only villager partners react; player partners are left to the players.")
            .translation(KEY + "infidelity")
            .push("infidelity");

        PROMISED_SEVERITY = b
            .comment("Base severity when the cheater was promised.")
            .translation(KEY + "promisedSeverity")
            .defineInRange("promisedSeverity", 1.0, 0.0, 10.0);

        COMMITTED_SEVERITY = b
            .comment("Base severity when the cheater was engaged or married (they count the same).")
            .translation(KEY + "committedSeverity")
            .defineInRange("committedSeverity", 2.0, 0.0, 10.0);

        INITIATED_MULTIPLIER = b
            .comment("Multiplier when the cheater asked (or offered) first.")
            .translation(KEY + "initiatedMultiplier")
            .defineInRange("initiatedMultiplier", 1.25, 0.0, 5.0);

        WAS_ASKED_MULTIPLIER = b
            .comment("Multiplier when the cheater was the one asked.")
            .translation(KEY + "wasAskedMultiplier")
            .defineInRange("wasAskedMultiplier", 0.85, 0.0, 5.0);

        WITNESSED_MULTIPLIER = b
            .comment("Multiplier when the partner saw it happen.")
            .translation(KEY + "witnessedMultiplier")
            .defineInRange("witnessedMultiplier", 1.5, 0.0, 5.0);

        REPEAT_STEP = b
            .comment("Added to the multiplier for each earlier discovered offense against the same partner. Never fades.")
            .translation(KEY + "repeatStep")
            .defineInRange("repeatStep", 0.5, 0.0, 5.0);

        OTHER_PARTY_SHARE = b
            .comment("Share of the severity the wronged partner also holds against the person their partner cheated with.")
            .translation(KEY + "otherPartyShare")
            .defineInRange("otherPartyShare", 0.6, 0.0, 2.0);

        HEARTS_PER_SEVERITY = b
            .comment("Hearts lost per point of severity (players only; MCA has no hearts between villagers).")
            .translation(KEY + "heartsPerSeverity")
            .defineInRange("heartsPerSeverity", 15, 0, 100);

        BREAKUP_THRESHOLD = b
            .comment("Grievance at which the wronged partner ends the relationship. Scaled by her forgiveness",
                "(personality: about 0.6 for prickly, 1.4 for warm). A first witnessed affair while married is about 3.75.")
            .translation(KEY + "breakupThreshold")
            .defineInRange("breakupThreshold", 5.0, 0.5, 50.0);

        GRIEVANCE_DECAY_PER_DAY = b
            .comment("Grievance that fades per Minecraft day, also scaled by forgiveness.")
            .translation(KEY + "grievanceDecayPerDay")
            .defineInRange("grievanceDecayPerDay", 0.25, 0.0, 10.0);

        REFUSE_INTIMACY_GRIEVANCE = b
            .comment("At or above this grievance, a villager won't be intimate with that person (or make offers to them).")
            .translation(KEY + "refuseIntimacyGrievance")
            .defineInRange("refuseIntimacyGrievance", 1.0, 0.0, 50.0);

        EVIDENCE_MULTIPLIER = b
            .comment("Multiplier when it came out through a child.")
            .translation(KEY + "evidenceMultiplier")
            .defineInRange("evidenceMultiplier", 1.25, 0.0, 5.0);

        b.pop();

        b.comment("How affairs come out besides being seen: gossip from villagers who saw it, a child, or plain chance.")
            .translation(KEY + "discovery")
            .push("discovery");

        GOSSIP_DELAY_MINUTES = b
            .comment("Real-time minutes before witnesses start talking.")
            .translation(KEY + "gossipDelayMinutes")
            .defineInRange("gossipDelayMinutes", 5, 0, 240);

        GOSSIP_CHANCE_PER_MINUTE = b
            .comment("Chance per minute that a witness who's near the wronged partner tells them.")
            .translation(KEY + "gossipChancePerMinute")
            .defineInRange("gossipChancePerMinute", 0.3, 0.0, 1.0);

        GOSSIP_RADIUS = b
            .comment("How close (blocks) a witness has to be to the wronged partner to tell them.")
            .translation(KEY + "gossipRadius")
            .defineInRange("gossipRadius", 8, 2, 48);

        CHANCE_DISCOVERY_PER_DAY = b
            .comment("Chance per Minecraft day that an undiscovered affair comes out anyway.")
            .translation(KEY + "chanceDiscoveryPerDay")
            .defineInRange("chanceDiscoveryPerDay", 0.03, 0.0, 1.0);

        CHANCE_WINDOW_DAYS = b
            .comment("Minecraft days after which an undiscovered affair can no longer come out by chance (gossip and children still can).")
            .translation(KEY + "chanceWindowDays")
            .defineInRange("chanceWindowDays", 30, 1, 365);

        b.pop();

        b.comment("Exile from a village: permanent. Can't talk to, trade with, marry or be intimate with its villagers,",
                "its guards turn hostile, and you're told to leave inside its borders. Ops can pardon with /intimacydebug pardon.")
            .translation(KEY + "exile")
            .push("exile");

        EXILE_THRESHOLD = b
            .comment("Total severity of a player's discovered offenses in one village that gets them exiled",
                "(as the person someone cheated with, the 'other party share' of it counts).")
            .translation(KEY + "exileThreshold")
            .defineInRange("exileThreshold", 12.0, 1.0, 200.0);

        EXILE_MIN_PARTNERS = b
            .comment("...and those offenses must involve at least this many different people.")
            .translation(KEY + "exileMinPartners")
            .defineInRange("exileMinPartners", 2, 1, 20);

        EXILE_GUARDS_HOSTILE = b
            .comment("Guards of the village attack exiled players inside its borders.")
            .translation(KEY + "exileGuardsHostile")
            .define("exileGuardsHostile", true);

        EXILE_WARN_SECONDS = b
            .comment("How often exiled players inside the village are told to leave.")
            .translation(KEY + "exileWarnSeconds")
            .defineInRange("exileWarnSeconds", 30, 5, 600);

        b.pop();

        b.comment("Protection items. A session 'with protection' uses birth control, a worn diaphragm, or a condom from",
                "either person; with none of those it can still go ahead on a safe day (outside her fertile window).")
            .translation(KEY + "protection")
            .push("protection");

        ALLOW_SAFE_DAYS = b
            .comment("Let 'with protection' go ahead without any item outside the fertile window, relying on the cycle.")
            .translation(KEY + "allowSafeDays")
            .define("allowSafeDays", true);

        BIRTH_CONTROL_DAYS = b
            .comment("Minecraft days one pill protects for.")
            .translation(KEY + "birthControlDays")
            .defineInRange("birthControlDays", 3, 1, 30);

        DIAPHRAGM_STIRS = b
            .comment("Right-clicks on a water cauldron needed to wash a diaphragm dropped into it.")
            .translation(KEY + "diaphragmStirs")
            .defineInRange("diaphragmStirs", 4, 1, 20);

        MALE_CONDOM_FAILURE = b
            .comment("Chance a condom fails. A failure means conception is rolled as normal.")
            .translation(KEY + "maleCondomFailure")
            .defineInRange("maleCondomFailure", 0.02, 0.0, 1.0);

        FEMALE_CONDOM_FAILURE = b
            .comment("Chance an internal condom fails.")
            .translation(KEY + "femaleCondomFailure")
            .defineInRange("femaleCondomFailure", 0.05, 0.0, 1.0);

        DIAPHRAGM_FAILURE = b
            .comment("Chance a diaphragm fails.")
            .translation(KEY + "diaphragmFailure")
            .defineInRange("diaphragmFailure", 0.06, 0.0, 1.0);

        BIRTH_CONTROL_FAILURE = b
            .comment("Chance birth control fails.")
            .translation(KEY + "birthControlFailure")
            .defineInRange("birthControlFailure", 0.01, 0.0, 1.0);

        b.pop();

        b.comment("Villagers making the first move. Players always get the normal prompt and can decline at no cost,",
                "and each player can turn offers off with /intimacy offers off.")
            .translation(KEY + "offers")
            .push("offers");

        VILLAGER_OFFERS = b
            .comment("Allow villagers to make offers at all.")
            .translation(KEY + "villagerOffers")
            .define("villagerOffers", true);

        OFFER_CHECK_SECONDS = b
            .comment("How often each player gets a roll for a spontaneous offer from a villager nearby.")
            .translation(KEY + "offerCheckSeconds")
            .defineInRange("offerCheckSeconds", 60, 10, 1200);

        SPONTANEOUS_OFFER_CHANCE = b
            .comment("Chance per roll, when an eligible villager who likes you is nearby and comfortable where she is.",
                "A bit higher around her ovulation.")
            .translation(KEY + "spontaneousOfferChance")
            .defineInRange("spontaneousOfferChance", 0.08, 0.0, 1.0);

        OFFER_SEARCH_RADIUS = b
            .comment("How far away (in blocks) a villager can be to walk over and make an offer.")
            .translation(KEY + "offerSearchRadius")
            .defineInRange("offerSearchRadius", 16, 4, 48);

        OFFER_COOLDOWN_MINUTES = b
            .comment("Real-time minutes before the same villager makes another spontaneous or journal offer.")
            .translation(KEY + "offerCooldownMinutes")
            .defineInRange("offerCooldownMinutes", 20, 1, 240);

        JOURNAL_OFFER_CHANCE = b
            .comment("Chance a villager in her fertile window offers after telling you about her cycle.")
            .translation(KEY + "journalOfferChance")
            .defineInRange("journalOfferChance", 0.35, 0.0, 1.0);

        ESCORT_TIMEOUT_SECONDS = b
            .comment("How long a villager leading you home waits for you before giving up.")
            .translation(KEY + "escortTimeoutSeconds")
            .defineInRange("escortTimeoutSeconds", 180, 30, 900);

        b.pop();

        b.comment("Conception and gestation. Times count while the carrier is loaded (villagers) or online (players).")
            .translation(KEY + "pregnancy")
            .push("pregnancy");

        PEAK_CONCEPTION_CHANCE = b
            .comment("Chance of conception after a completed, unprotected session on the day of ovulation.",
                "Falls to 40% of this at the edges of the fertile window.")
            .translation(KEY + "peakConceptionChance")
            .defineInRange("peakConceptionChance", 0.5, 0.0, 1.0);

        BASE_CONCEPTION_CHANCE = b
            .comment("Chance of conception outside the fertile window.")
            .translation(KEY + "baseConceptionChance")
            .defineInRange("baseConceptionChance", 0.02, 0.0, 1.0);

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

        b.comment("The fertility cycle of female players and villagers. Days are Minecraft days and follow the world clock,",
                "so sleeping advances it. Everyone starts at a random point.")
            .translation(KEY + "cycle")
            .push("cycle");

        CYCLE_LENGTH_DAYS = b
            .comment("Length of a cycle in Minecraft days. 30 days is about 10 hours of play.",
                "Menstruation, the fertile window and ovulation scale with it.")
            .translation(KEY + "cycleLengthDays")
            .defineInRange("cycleLengthDays", 30, 4, 60);

        CYCLE_SHARE_HEARTS = b
            .comment("Hearts a villager needs before telling a non-partner about her cycle (partners only need a decent mood).")
            .translation(KEY + "cycleShareHearts")
            .defineInRange("cycleShareHearts", 50, 0, 200);

        b.pop();

        b.comment("Active interaction settings.")
            .translation(KEY + "session")
            .push("session");

        SESSION_DURATION_SECONDS = b
            .comment("How long an interaction lasts when the scene is shown, in seconds. Either participant can stop at any time.")
            .translation(KEY + "sessionDurationSeconds")
            .defineInRange("sessionDurationSeconds", 20, 5, 600);

        SKIPPED_SESSION_SECONDS = b
            .comment("How long the fade to black lasts when the scene is skipped.")
            .translation(KEY + "skippedSessionSeconds")
            .defineInRange("skippedSessionSeconds", 5, 1, 60);

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
