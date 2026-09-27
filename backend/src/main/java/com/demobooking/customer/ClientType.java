package com.demobooking.customer;

/**
 * The entry-flow choice a customer makes on the landing page. Unlike a service type's
 * ApplicableClientType, a customer is always exactly one of these two, never "both".
 */
public enum ClientType {
	NEW_CLIENT,
	EXISTING_CLIENT
}
