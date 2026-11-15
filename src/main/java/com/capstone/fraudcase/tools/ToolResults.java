package com.capstone.fraudcase.tools;

import com.capstone.fraudcase.domain.PaymentChannel;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.domain.TransactionStatus;
import com.capstone.fraudcase.knowledge.PolicyPassage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Typed, read-only tool result contracts (guide section 2.5). Provenance is attached by the ToolGateway envelope. */
public final class ToolResults {

    private ToolResults() {
    }

    public record TransactionDetails(boolean found, TransactionStatus status, OffsetDateTime timestamp, BigDecimal amount,
                                     String currency, PaymentChannel channel, String authOutcome, String maskedMerchant,
                                     boolean duplicateFingerprintMatch, String duplicateOfRef, List<String> observations) {
        public boolean authUnavailable() {
            return "UNAVAILABLE".equals(authOutcome);
        }
    }

    public record DeviceSignals(boolean deviceKnown, OffsetDateTime firstSeenAt, String trustLevel, List<String> observations) {
        public boolean trusted() {
            return deviceKnown && "HIGH".equals(trustLevel);
        }
    }

    public record LocationComparison(int distanceKm, long timeDeltaMinutes, String anomalyLevel, String currentRegion,
                                     String trustedRegion, List<String> observations) {
    }

    public record MerchantRiskProfile(String riskBand, boolean blocklistMatch, String category, List<String> observations) {
    }

    public record PriorDisputes(int count, int similarCount, int lookbackDays, List<String> observations) {
    }

    /** Transaction frequency in trailing windows; a burst of 5+ in 10 minutes is a recorded velocity indicator. */
    public record TransactionVelocity(int count10Min, int count60Min, int count24h, BigDecimal windowTotalAmount,
                                      boolean burst, List<String> observations) {
    }

    /** Synthetic sanctions/PEP screening outcome. CONFIRMED_MATCH recommends AML escalation; nothing is actioned. */
    public record ScreeningResult(String status, String listType, double matchScore, String screenedParty,
                                  List<String> observations) {
        public boolean confirmedMatch() {
            return "CONFIRMED_MATCH".equals(status);
        }

        public boolean potentialMatch() {
            return "POTENTIAL_MATCH".equals(status);
        }
    }

    public record CaseSla(Route queue, String priority, OffsetDateTime targetAt, String explanation) {
    }

    public record PolicyEvidence(String query, int topK, double threshold, boolean abstained, List<PolicyPassage> passages) {
    }
}
