package com.demobooking.booking.api;

import com.demobooking.booking.AppointmentLookupService;
import com.demobooking.booking.BookingService;
import com.demobooking.booking.CancellationService;
import com.demobooking.booking.IdempotentBookingService;
import com.demobooking.booking.RescheduleService;
import com.demobooking.booking.dto.AppointmentHoldResponse;
import com.demobooking.booking.dto.AppointmentResponse;
import com.demobooking.booking.dto.BookingRequest;
import com.demobooking.booking.dto.ConfirmationResultResponse;
import com.demobooking.booking.dto.RescheduleRequest;
import com.demobooking.booking.dto.RescheduleRequestResponse;
import com.demobooking.common.RateLimiter;
import com.demobooking.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AppointmentController {

	private final BookingService bookingService;
	private final AppointmentLookupService lookupService;
	private final RescheduleService rescheduleService;
	private final CancellationService cancellationService;
	private final IdempotentBookingService idempotentBookingService;
	private final RateLimiter rateLimiter;
	// Neither bucket is the PII-fishing target DirectoryValidationRateLimiter guards - these
	// ceilings are generous, general abuse resistance, not enumeration hardening.
	private final AppProperties.Limit bookingCreateLimit;
	private final AppProperties.Limit lookupLimit;

	AppointmentController(
			BookingService bookingService,
			AppointmentLookupService lookupService,
			RescheduleService rescheduleService,
			CancellationService cancellationService,
			IdempotentBookingService idempotentBookingService,
			RateLimiter rateLimiter,
			AppProperties appProperties) {
		this.bookingService = bookingService;
		this.lookupService = lookupService;
		this.rescheduleService = rescheduleService;
		this.cancellationService = cancellationService;
		this.idempotentBookingService = idempotentBookingService;
		this.rateLimiter = rateLimiter;
		this.bookingCreateLimit = appProperties.rateLimit().bookingCreate();
		this.lookupLimit = appProperties.rateLimit().lookup();
	}

	@PostMapping("/appointments")
	public ResponseEntity<AppointmentHoldResponse> createAppointment(
			@Valid @RequestBody BookingRequest request,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			HttpServletRequest httpRequest) {
		String clientIp = httpRequest.getRemoteAddr();
		rateLimiter.checkAndRecordAttempt("booking-create", clientIp, bookingCreateLimit.maxAttempts(), bookingCreateLimit.window());
		return ResponseEntity.status(HttpStatus.CREATED).body(idempotentBookingService.createHold(request, idempotencyKey, clientIp));
	}

	@PostMapping("/confirmations/{token}")
	public ConfirmationResultResponse confirm(@PathVariable String token) {
		return bookingService.confirm(token);
	}

	@GetMapping("/appointments/lookup")
	public AppointmentResponse lookup(@RequestParam String reference, @RequestParam String email, HttpServletRequest httpRequest) {
		rateLimiter.checkAndRecordAttempt("appointment-lookup", httpRequest.getRemoteAddr(), lookupLimit.maxAttempts(), lookupLimit.window());
		return lookupService.lookup(reference, email);
	}

	@PostMapping("/appointments/{appointmentId}/reschedule-request")
	public RescheduleRequestResponse requestReschedule(
			@PathVariable UUID appointmentId,
			@Valid @RequestBody RescheduleRequest request,
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam(required = false) String reference,
			@RequestParam(required = false) String email) {
		return rescheduleService.requestReschedule(appointmentId, request.newSlotId(), bearerTokenFrom(authorization), reference, email);
	}

	@PostMapping("/reschedule-confirmations/{token}")
	public AppointmentResponse confirmReschedule(@PathVariable String token) {
		return rescheduleService.confirmReschedule(token);
	}

	// Same credentials and 404 semantics as the DELETE below - lets a token holder re-fetch.
	@GetMapping("/appointments/{appointmentId}")
	public AppointmentResponse get(
			@PathVariable UUID appointmentId,
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam(required = false) String reference,
			@RequestParam(required = false) String email) {
		return lookupService.get(appointmentId, bearerTokenFrom(authorization), reference, email);
	}

	@DeleteMapping("/appointments/{appointmentId}")
	public ResponseEntity<Void> cancel(
			@PathVariable UUID appointmentId,
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam(required = false) String reference,
			@RequestParam(required = false) String email) {
		cancellationService.cancelFromWebsite(appointmentId, bearerTokenFrom(authorization), reference, email);
		return ResponseEntity.noContent().build();
	}

	private static String bearerTokenFrom(String authorizationHeader) {
		if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return null;
		}
		return authorizationHeader.substring(7);
	}

}
