package com.demobooking.notification;

import com.demobooking.booking.Appointment;
import com.demobooking.booking.NotificationSender;
import com.demobooking.booking.SentEmail;
import com.demobooking.branch.TimeSlot;
import com.demobooking.config.AppProperties;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Builds the exact payload a real email provider would receive and hands it back instead of
 * sending it; booking persists it. Never sent over a network, so this can never fail for a
 * network reason. The one home of everything an email says: subject lines, sender address,
 * bodies and action links.
 */
@Service
class SimulatedNotificationSender implements NotificationSender {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH);
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
	private static final String FROM_ADDRESS = "no-reply@demobooking.simulated";

	private final String frontendOrigin;
	private final Clock clock;

	SimulatedNotificationSender(AppProperties appProperties, Clock clock) {
		this.frontendOrigin = appProperties.frontendOrigin();
		this.clock = clock;
	}

	@Override
	public SentEmail sendConfirmationRequest(Appointment appointment, String rawToken) {
		TimeSlot slot = appointment.getTimeSlot();
		String link = frontendOrigin + "/confirm/" + rawToken;
		String body = """
				SIMULATED EMAIL - no message was actually sent.

				Hi %s,

				Please confirm your appointment at %s:
				  %s at %s on %s

				Confirm your appointment: %s

				This link expires in a few minutes. If it expires, your slot will be released and \
				you'll need to book again.""".formatted(
				appointment.getCustomer().getFullName(),
				slot.getBranch().getName(),
				slot.getServiceType().getName(),
				slot.getStartTime().format(TIME_FORMAT),
				slot.getSlotDate().format(DATE_FORMAT),
				link);

		return email("Confirm your branch appointment", body, link);
	}

	@Override
	public SentEmail sendBookingReceipt(Appointment appointment, String rawCancellationToken) {
		TimeSlot slot = appointment.getTimeSlot();
		String cancelLink = frontendOrigin + "/cancellations/" + rawCancellationToken;
		String body = """
				SIMULATED EMAIL - no message was actually sent.

				Hi %s,

				Your appointment is confirmed:
				  Reference: %s
				  %s at %s on %s
				  %s

				Need to cancel? %s""".formatted(
				appointment.getCustomer().getFullName(),
				appointment.getReferenceCode(),
				slot.getServiceType().getName(),
				slot.getStartTime().format(TIME_FORMAT),
				slot.getSlotDate().format(DATE_FORMAT),
				slot.getBranch().getName(),
				cancelLink);

		return email("Your appointment is confirmed", body, cancelLink);
	}

	@Override
	public SentEmail sendExpiryNotice(Appointment appointment) {
		TimeSlot slot = appointment.getTimeSlot();
		String body = """
				SIMULATED EMAIL - no message was actually sent.

				Hi %s,

				Your appointment request for %s at %s on %s could not be confirmed in time, so the \
				slot has been released. If you'd still like to book, please start again.""".formatted(
				appointment.getCustomer().getFullName(),
				slot.getServiceType().getName(),
				slot.getStartTime().format(TIME_FORMAT),
				slot.getSlotDate().format(DATE_FORMAT));

		return email("Your appointment could not be confirmed", body, null);
	}

	@Override
	public SentEmail sendRescheduleRequest(Appointment appointment, TimeSlot newSlot, String rawToken) {
		TimeSlot currentSlot = appointment.getTimeSlot();
		String link = frontendOrigin + "/reschedule-confirm/" + rawToken;
		String body = """
				SIMULATED EMAIL - no message was actually sent.

				Hi %s,

				Please confirm moving your appointment at %s:
				  From %s at %s on %s
				  To   %s at %s on %s

				Confirm this reschedule: %s

				This link expires in a few minutes. If it expires, the new time will be released \
				and your original appointment will stay exactly as it is.""".formatted(
				appointment.getCustomer().getFullName(),
				currentSlot.getBranch().getName(),
				currentSlot.getServiceType().getName(),
				currentSlot.getStartTime().format(TIME_FORMAT),
				currentSlot.getSlotDate().format(DATE_FORMAT),
				newSlot.getServiceType().getName(),
				newSlot.getStartTime().format(TIME_FORMAT),
				newSlot.getSlotDate().format(DATE_FORMAT),
				link);

		return email("Confirm your appointment reschedule", body, link);
	}

	@Override
	public SentEmail sendRescheduleExpiryNotice(Appointment appointment, TimeSlot newSlot) {
		TimeSlot currentSlot = appointment.getTimeSlot();
		String body = """
				SIMULATED EMAIL - no message was actually sent.

				Hi %s,

				Your request to move your appointment to %s at %s on %s could not be confirmed in \
				time, so that hold has been released. Your original appointment is unchanged:
				  %s at %s on %s""".formatted(
				appointment.getCustomer().getFullName(),
				newSlot.getServiceType().getName(),
				newSlot.getStartTime().format(TIME_FORMAT),
				newSlot.getSlotDate().format(DATE_FORMAT),
				currentSlot.getServiceType().getName(),
				currentSlot.getStartTime().format(TIME_FORMAT),
				currentSlot.getSlotDate().format(DATE_FORMAT));

		return email("Your reschedule request expired", body, null);
	}

	private SentEmail email(String subject, String body, String actionLink) {
		return new SentEmail(subject, FROM_ADDRESS, body, actionLink, clock.instant());
	}

}
