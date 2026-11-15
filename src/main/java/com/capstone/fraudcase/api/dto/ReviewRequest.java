package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.domain.Disposition;
import com.capstone.fraudcase.domain.Route;

/** Reviewer disposition. Rationale is mandatory; missing information must be acknowledged when present. */
public record ReviewRequest(Disposition disposition, Route selectedRoute, String reason, boolean missingInfoAcknowledged) {
}
