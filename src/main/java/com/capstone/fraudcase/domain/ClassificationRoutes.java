package com.capstone.fraudcase.domain;

import java.util.Map;

/** Classification to default route mapping (guide section 3.1). MANUAL_REVIEW is always an acceptable safer route. */
public final class ClassificationRoutes {

    private static final Map<Classification, Route> DEFAULT_ROUTES = Map.of(
            Classification.UNAUTHORISED_TRANSACTION, Route.FRAUD_INVESTIGATION,
            Classification.MERCHANT_DISPUTE, Route.DISPUTE_OPERATIONS,
            Classification.DUPLICATE_PROCESSING, Route.PAYMENT_OPERATIONS,
            Classification.SUSPICIOUS_TRANSACTION, Route.FRAUD_INVESTIGATION,
            Classification.INSUFFICIENT_EVIDENCE, Route.MANUAL_REVIEW,
            Classification.GENUINE_TRANSACTION, Route.MANUAL_REVIEW);

    private ClassificationRoutes() {
    }

    public static Route defaultRoute(Classification classification) {
        return DEFAULT_ROUTES.get(classification);
    }

    /**
     * MANUAL_REVIEW is always an acceptable safer route. AML_INVESTIGATION is permitted for fraud classifications
     * when a synthetic screening match is present; the route is still only a recommendation for human review.
     */
    public static boolean isPermitted(Classification classification, Route route) {
        if (route == Route.MANUAL_REVIEW || DEFAULT_ROUTES.get(classification) == route) {
            return true;
        }
        return route == Route.AML_INVESTIGATION
                && (classification == Classification.UNAUTHORISED_TRANSACTION || classification == Classification.SUSPICIOUS_TRANSACTION);
    }
}
