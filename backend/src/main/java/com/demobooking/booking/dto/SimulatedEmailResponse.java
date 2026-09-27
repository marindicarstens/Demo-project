package com.demobooking.booking.dto;

import com.demobooking.booking.SentEmail;
import java.time.Instant;

// Matches the SimulatedEmail schema in docs/api/openapi.yaml.
public record SimulatedEmailResponse(String subject, String from, String bodyText, String actionLink, Instant sentAt) {

	public static SimulatedEmailResponse from(SentEmail email) {
		return new SimulatedEmailResponse(email.subject(), email.from(), email.body(), email.actionLink(), email.sentAt());
	}

}
