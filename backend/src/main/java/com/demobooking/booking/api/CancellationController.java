package com.demobooking.booking.api;

import com.demobooking.booking.CancellationService;
import com.demobooking.booking.dto.CancellationPreviewResponse;
import com.demobooking.booking.dto.CancellationResultResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The email-cancellation-link pair: a safe GET preview and an explicit POST confirm - the GET
 * must stay read-only (email scanners/prefetchers GET every link automatically).
 */
@RestController
@RequestMapping("/api/v1/cancellations")
public class CancellationController {

	private final CancellationService cancellationService;

	CancellationController(CancellationService cancellationService) {
		this.cancellationService = cancellationService;
	}

	@GetMapping("/{token}")
	public CancellationPreviewResponse preview(@PathVariable String token) {
		return cancellationService.previewCancellation(token);
	}

	@PostMapping("/{token}")
	public CancellationResultResponse confirm(@PathVariable String token) {
		return cancellationService.confirmCancellation(token);
	}

}
