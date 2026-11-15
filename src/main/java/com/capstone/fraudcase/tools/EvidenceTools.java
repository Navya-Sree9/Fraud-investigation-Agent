package com.capstone.fraudcase.tools;

import com.capstone.fraudcase.config.CapstoneProperties;
import com.capstone.fraudcase.domain.CaseFacts;
import com.capstone.fraudcase.domain.Hashing;
import com.capstone.fraudcase.domain.RiskLevel;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.domain.ToolStatus;
import com.capstone.fraudcase.tools.SyntheticFixtureStore.Fixture;
import com.capstone.fraudcase.tools.ToolResults.CaseSla;
import com.capstone.fraudcase.tools.ToolResults.DeviceSignals;
import com.capstone.fraudcase.tools.ToolResults.LocationComparison;
import com.capstone.fraudcase.tools.ToolResults.MerchantRiskProfile;
import com.capstone.fraudcase.tools.ToolResults.PriorDisputes;
import com.capstone.fraudcase.tools.ToolResults.ScreeningResult;
import com.capstone.fraudcase.tools.ToolResults.TransactionDetails;
import com.capstone.fraudcase.tools.ToolResults.TransactionVelocity;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Framework-neutral, read-only synthetic evidence adapters. Every value is either a curated fixture override or
 * derived from a stable hash of (seed, reference) so the same input always produces the same evidence.
 * None of these methods can move funds, block cards, contact customers or file reports.
 */
@Component
public class EvidenceTools {

    private static final String[] REGIONS = {"IN-KA Bengaluru", "IN-DL Delhi", "SG Singapore", "AE-DU Dubai", "GB-LND London"};
    private static final String[] CATEGORIES = {"DIGITAL_GOODS", "ELECTRONICS_RETAIL", "TRAVEL", "GROCERY", "SUBSCRIPTION"};

    private final SyntheticFixtureStore fixtures;
    private final String seed;

    public EvidenceTools(SyntheticFixtureStore fixtures, CapstoneProperties properties) {
        this.fixtures = fixtures;
        this.seed = properties.tools().deterministicSeed();
    }

    public TransactionDetails getTransactionDetails(CaseFacts facts) {
        Fixture f = fixture(facts, "transaction");
        if (facts.transactionRef().startsWith("TXN-MISSING")) {
            throw new ToolUnavailableException(ToolStatus.NOT_FOUND, "No ledger record for reference");
        }
        double u = Hashing.unit(seed, "auth", facts.transactionRef());
        String auth = f.string("authOutcome", u < 0.55 ? "SUCCESS" : u < 0.85 ? "UNAVAILABLE" : "NOT_ATTEMPTED");
        boolean duplicate = f.bool("duplicateFingerprintMatch", false);
        List<String> obs = new ArrayList<>();
        obs.add("Ledger status " + facts.transactionStatus() + " via " + facts.paymentChannel());
        obs.add(switch (auth) {
            case "SUCCESS" -> "Strong customer authentication completed";
            case "UNAVAILABLE" -> "Authentication challenge outcome not available from source system";
            case "FAILED_THEN_BYPASSED" -> "Authentication challenge failed; transaction processed via fallback";
            default -> "No authentication challenge recorded";
        });
        if (duplicate) {
            obs.add("Payment fingerprint matches " + f.string("duplicateOfRef", "an earlier posting") + " (same amount, merchant, minute)");
        }
        return new TransactionDetails(true, facts.transactionStatus(), facts.occurredAt(), facts.amount(), facts.currency(),
                facts.paymentChannel(), auth, facts.maskedMerchant(), duplicate, f.string("duplicateOfRef", null), obs);
    }

    public DeviceSignals getDeviceSignals(CaseFacts facts) {
        Fixture f = fixture(facts, "device");
        double u = Hashing.unit(seed, "device", facts.maskedCustomerRef(), facts.transactionRef());
        boolean known = f.bool("deviceKnown", u < 0.65);
        String trust = f.string("trustLevel", known ? (u < 0.4 ? "HIGH" : "MEDIUM") : "LOW");
        long minutesBefore = (long) f.number("firstSeenMinutesBefore",
                known ? Hashing.range(43_200, 600_000, seed, "dev-age", facts.transactionRef()) : Hashing.range(5, 2_880, seed, "dev-age", facts.transactionRef()));
        OffsetDateTime firstSeen = facts.occurredAt().minusMinutes(minutesBefore);
        List<String> obs = new ArrayList<>();
        obs.add(known ? "Device previously observed for this customer" : "Device not previously observed for this customer");
        obs.add("First seen " + humanDuration(Duration.ofMinutes(minutesBefore)) + " before the transaction");
        obs.add("Trust level " + trust);
        return new DeviceSignals(known, firstSeen, trust, obs);
    }

    public LocationComparison compareRecentLocations(CaseFacts facts) {
        Fixture f = fixture(facts, "location");
        double u = Hashing.unit(seed, "location", facts.maskedCustomerRef(), facts.transactionRef());
        String defaultLevel = u < 0.7 ? "NONE" : u < 0.9 ? "MEDIUM" : "HIGH";
        String level = f.string("anomalyLevel", defaultLevel);
        int distance = (int) f.number("distanceKm", switch (level) {
            case "NONE", "LOW" -> Hashing.range(0, 40, seed, "dist", facts.transactionRef());
            case "MEDIUM" -> Hashing.range(200, 600, seed, "dist", facts.transactionRef());
            default -> Hashing.range(900, 3000, seed, "dist", facts.transactionRef());
        });
        long delta = (long) f.number("timeDeltaMinutes", Hashing.range(30, 2_880, seed, "delta", facts.transactionRef()));
        String current = f.string("currentRegion", "NONE".equals(level) ? facts.homeRegion()
                : REGIONS[Hashing.range(0, REGIONS.length - 1, seed, "region", facts.transactionRef())]);
        List<String> obs = new ArrayList<>();
        obs.add("Transaction location " + current + " vs trusted region " + facts.homeRegion());
        obs.add("%d km from last trusted activity, %s earlier".formatted(distance, humanDuration(Duration.ofMinutes(delta))));
        if ("HIGH".equals(level) && delta > 0 && distance / (delta / 60.0) > 800) {
            obs.add("Implied travel speed exceeds plausible commercial travel");
        }
        return new LocationComparison(distance, delta, level, current, facts.homeRegion(), obs);
    }

    public MerchantRiskProfile getMerchantRiskProfile(CaseFacts facts) {
        Fixture f = fixture(facts, "merchant");
        double u = Hashing.unit(seed, "merchant", facts.maskedMerchant());
        String band = f.string("riskBand", u < 0.6 ? "LOW" : u < 0.9 ? "MEDIUM" : "HIGH");
        boolean blocklist = f.bool("blocklistMatch", u > 0.97);
        String category = f.string("category", CATEGORIES[Hashing.range(0, CATEGORIES.length - 1, seed, "cat", facts.maskedMerchant())]);
        List<String> obs = new ArrayList<>();
        obs.add("Training merchant risk band " + band + ", category " + category);
        obs.add(blocklist ? "Merchant matches training blocklist" : "No training blocklist match");
        return new MerchantRiskProfile(band, blocklist, category, obs);
    }

    public PriorDisputes getPriorDisputes(CaseFacts facts) {
        Fixture f = fixture(facts, "priorDisputes");
        int count = (int) f.number("count", Hashing.range(0, 2, seed, "disputes", facts.maskedCustomerRef()));
        int similar = (int) Math.min(count, f.number("similarCount", Hashing.range(0, count, seed, "similar", facts.maskedCustomerRef())));
        List<String> obs = List.of("%d earlier dispute(s) in 365 days, %d similar to this claim".formatted(count, similar));
        return new PriorDisputes(count, similar, 365, obs);
    }

    /** Transaction frequency over trailing windows. Read-only; a burst is an indicator, never an action. */
    public TransactionVelocity getTransactionVelocity(CaseFacts facts) {
        Fixture f = fixture(facts, "velocity");
        int ten = (int) f.number("count10Min", Hashing.range(0, 3, seed, "vel10", facts.maskedCustomerRef()));
        int hour = (int) f.number("count60Min", Math.max(ten, Hashing.range(ten, ten + 5, seed, "vel60", facts.maskedCustomerRef())));
        int day = (int) f.number("count24h", Math.max(hour, Hashing.range(hour, hour + 9, seed, "vel24", facts.maskedCustomerRef())));
        boolean burst = ten >= 5;
        BigDecimal windowTotal = facts.amount().multiply(BigDecimal.valueOf(Math.max(ten, 1))).setScale(2, java.math.RoundingMode.HALF_UP);
        List<String> obs = new ArrayList<>();
        obs.add("%d transaction(s) in 10 min, %d in 60 min, %d in 24 h for this customer".formatted(ten, hour, day));
        obs.add(burst ? "Velocity burst: 5 or more transactions within 10 minutes"
                : "Transaction frequency within normal bounds");
        return new TransactionVelocity(ten, hour, day, windowTotal, burst, obs);
    }

    /**
     * Synthetic sanctions/PEP-style screening of the case merchant. CONFIRMED_MATCH recommends AML escalation as a
     * routing suggestion only; no report is filed and no party is contacted.
     */
    public ScreeningResult runComplianceScreening(CaseFacts facts) {
        Fixture f = fixture(facts, "screening");
        double u = Hashing.unit(seed, "screening", facts.maskedMerchant(), facts.maskedCustomerRef());
        String status = f.string("status", u < 0.97 ? "CLEAR" : "POTENTIAL_MATCH");
        String list = f.string("listType", "NONE");
        double score = f.number("matchScore", switch (status) {
            case "CONFIRMED_MATCH" -> 0.9 + u * 0.09;
            case "POTENTIAL_MATCH" -> 0.7 + u * 0.15;
            default -> u * 0.4;
        });
        List<String> obs = new ArrayList<>();
        obs.add("Screened " + facts.maskedMerchant() + " against synthetic training lists");
        obs.add(switch (status) {
            case "CONFIRMED_MATCH" -> "Confirmed match on " + list + " (score %.2f): AML escalation recommended for human decision".formatted(score);
            case "POTENTIAL_MATCH" -> "Potential " + list + " match (score %.2f): requires human adjudication".formatted(score);
            default -> "No synthetic sanctions or PEP list match";
        });
        return new ScreeningResult(status, list, Math.round(score * 1000) / 1000.0, facts.maskedMerchant(), obs);
    }

    /** Training SLA targets; the queue is a recommendation only and is never actioned automatically. */
    public CaseSla calculateCaseSla(Route route, RiskLevel risk, OffsetDateTime reportedAt) {
        String priority = switch (risk) {
            case CRITICAL -> "P1";
            case HIGH -> "P2";
            case MEDIUM -> "P3";
            case LOW -> "P4";
        };
        Duration target = switch (priority) {
            case "P1" -> Duration.ofHours(4);
            case "P2" -> Duration.ofHours(24);
            case "P3" -> Duration.ofHours(72);
            default -> Duration.ofDays(5);
        };
        if (route == Route.PAYMENT_OPERATIONS) {
            target = target.plusHours(24);
        }
        return new CaseSla(route, priority, reportedAt.plus(target),
                "Training target for %s at %s risk: %s from report (POL-ESC-011 synthetic SLA table)".formatted(route, risk, humanDuration(target)));
    }

    private Fixture fixture(CaseFacts facts, String tool) {
        Fixture f = fixtures.forTransaction(facts.transactionRef(), tool);
        String simulate = f.simulate();
        if ("NOT_FOUND".equals(simulate)) {
            throw new ToolUnavailableException(ToolStatus.NOT_FOUND, "Synthetic source returned no record");
        }
        if ("TIMEOUT".equals(simulate)) {
            simulateTimeout();
        }
        if ("FAILED".equals(simulate)) {
            throw new ToolUnavailableException(ToolStatus.FAILED, "Synthetic source error");
        }
        return f;
    }

    private static void simulateTimeout() {
        try {
            Thread.sleep(Duration.ofSeconds(30));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new ToolUnavailableException(ToolStatus.TIMEOUT, "Synthetic source timed out");
    }

    static String humanDuration(Duration d) {
        if (d.toDays() >= 2) {
            return d.toDays() + " days";
        }
        if (d.toHours() >= 2) {
            return d.toHours() + " hours";
        }
        return d.toMinutes() + " minutes";
    }
}
