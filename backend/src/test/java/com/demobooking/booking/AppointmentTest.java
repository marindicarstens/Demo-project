package com.demobooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.demobooking.common.IllegalAppointmentTransitionException;
import com.demobooking.common.UnprocessableEntityException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Plain unit test of the status machine: each transition is only legal from one status. */
class AppointmentTest {

	private static Appointment pending() {
		return new Appointment(null, null, "REF-TEST", Instant.EPOCH);
	}

	private static Appointment confirmed() {
		Appointment appointment = pending();
		appointment.confirm();
		return appointment;
	}

	@Test
	void legalTransitions_succeed() {
		assertThat(confirmed().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);

		Appointment expired = pending();
		expired.markExpired();
		assertThat(expired.getStatus()).isEqualTo(AppointmentStatus.EXPIRED);

		Appointment cancelled = confirmed();
		cancelled.cancel();
		assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
	}

	@Test
	void confirm_requiresPendingConfirmation() {
		assertThatThrownBy(() -> confirmed().confirm()).isInstanceOf(IllegalAppointmentTransitionException.class);

		Appointment expired = pending();
		expired.markExpired();
		assertThatThrownBy(expired::confirm).isInstanceOf(IllegalAppointmentTransitionException.class);
		assertThat(expired.getStatus()).isEqualTo(AppointmentStatus.EXPIRED);
	}

	@Test
	void markExpired_requiresPendingConfirmation() {
		Appointment appointment = confirmed();
		assertThatThrownBy(appointment::markExpired).isInstanceOf(IllegalAppointmentTransitionException.class);
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);

		Appointment cancelled = confirmed();
		cancelled.cancel();
		assertThatThrownBy(cancelled::markExpired).isInstanceOf(IllegalAppointmentTransitionException.class);
	}

	@Test
	void cancel_requiresConfirmed() {
		assertThatThrownBy(() -> pending().cancel()).isInstanceOf(IllegalAppointmentTransitionException.class);

		Appointment cancelled = confirmed();
		cancelled.cancel();
		assertThatThrownBy(cancelled::cancel).isInstanceOf(IllegalAppointmentTransitionException.class);

		Appointment expired = pending();
		expired.markExpired();
		assertThatThrownBy(expired::cancel).isInstanceOf(IllegalAppointmentTransitionException.class);
	}

	@Test
	void reschedule_requiresConfirmed() {
		assertThatThrownBy(() -> pending().reschedule(null)).isInstanceOf(IllegalAppointmentTransitionException.class);

		Appointment cancelled = confirmed();
		cancelled.cancel();
		assertThatThrownBy(() -> cancelled.reschedule(null)).isInstanceOf(IllegalAppointmentTransitionException.class);
	}

	@Test
	void requireModifiable_acceptsOnlyConfirmed() {
		confirmed().requireModifiable();

		assertThatThrownBy(() -> pending().requireModifiable())
				.isInstanceOf(UnprocessableEntityException.class)
				.hasMessage("This appointment can no longer be modified");
		Appointment cancelled = confirmed();
		cancelled.cancel();
		assertThatThrownBy(cancelled::requireModifiable).isInstanceOf(UnprocessableEntityException.class);
	}

}
