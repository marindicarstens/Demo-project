package com.demobooking.booking.dto;

import com.demobooking.customer.ClientType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.UUID;

/**
 * Matches the BookingRequest oneOf/discriminator in docs/api/openapi.yaml. Resolved to a
 * concrete subtype by the clientType property; {@code visible = true} keeps that same property
 * populated into the resolved record's own clientType component rather than being consumed
 * purely for type resolution.
 *
 * The annotations below are {@code com.fasterxml.jackson.annotation} (jackson-annotations), which
 * Jackson 3 keeps as-is - only jackson-databind/jackson-core moved to the {@code tools.jackson}
 * package namespace.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "clientType", visible = true)
@JsonSubTypes({
		@JsonSubTypes.Type(value = NewClientBookingRequest.class, name = "NEW_CLIENT"),
		@JsonSubTypes.Type(value = ExistingClientBookingRequest.class, name = "EXISTING_CLIENT")
})
public sealed interface BookingRequest permits NewClientBookingRequest, ExistingClientBookingRequest {

	ClientType clientType();

	UUID branchId();

	UUID serviceTypeId();

	UUID slotId();

}
