package com.demobooking.booking;

import java.time.Instant;

/** What a sent email said. Booking owns this shape so it can answer with the email without depending on the sender. */
public record SentEmail(String subject, String from, String body, String actionLink, Instant sentAt) {
}
