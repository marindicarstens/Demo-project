package com.demobooking.booking;

import com.demobooking.booking.dto.AppointmentHoldResponse;
import com.demobooking.booking.dto.BookingRequest;
import com.demobooking.common.ConflictException;
import com.demobooking.common.UnprocessableEntityException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Hold creation behind an optional Idempotency-Key: a retry with the same key and body replays
 * the original hold instead of reserving a second slot. See {@link IdempotencyService} for the
 * claim/store/release protocol this drives.
 */
@Service
public class IdempotentBookingService {

	private final BookingService bookingService;
	private final IdempotencyService idempotencyService;
	private final ObjectMapper objectMapper;

	IdempotentBookingService(BookingService bookingService, IdempotencyService idempotencyService, ObjectMapper objectMapper) {
		this.bookingService = bookingService;
		this.idempotencyService = idempotencyService;
		this.objectMapper = objectMapper;
	}

	/** Without a key this is a plain createHold; with one, a replay returns the stored response. */
	public AppointmentHoldResponse createHold(BookingRequest request, String idempotencyKey, String clientIp) {
		if (idempotencyKey == null) {
			return bookingService.createHold(request, clientIp);
		}

		String requestHash = idempotencyService.hashOf(request);
		IdempotencyService.ClaimResult claim = idempotencyService.claim(idempotencyKey);
		if (claim.status() == IdempotencyService.ClaimStatus.COMPLETED) {
			IdempotencyService.StoredRecord record = claim.existing();
			if (!record.requestHash().equals(requestHash)) {
				throw new UnprocessableEntityException("This Idempotency-Key was already used with a different request");
			}
			return objectMapper.readValue(record.responseBodyJson(), AppointmentHoldResponse.class);
		}
		if (claim.status() == IdempotencyService.ClaimStatus.IN_PROGRESS) {
			throw new ConflictException("A request with this Idempotency-Key is already being processed - please retry shortly");
		}

		AppointmentHoldResponse response;
		try {
			response = bookingService.createHold(request, clientIp);
		} catch (RuntimeException e) {
			// A failed attempt must not block a legitimate retry for the rest of the TTL.
			idempotencyService.release(idempotencyKey);
			throw e;
		}
		// The stored response carries the raw confirm token in actionLink - the bounded exception
		// to hash-only storage described in IdempotencyService's Javadoc.
		idempotencyService.store(idempotencyKey, new IdempotencyService.StoredRecord(requestHash, objectMapper.writeValueAsString(response)));
		return response;
	}

}
