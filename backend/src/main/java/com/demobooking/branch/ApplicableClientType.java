package com.demobooking.branch;

import com.demobooking.customer.ClientType;

/**
 * Which entry flow a service type is offered to. Enforced server-side wherever the catalog is
 * filtered, not just hidden in the UI.
 */
public enum ApplicableClientType {
	NEW_CLIENT,
	EXISTING_CLIENT,
	BOTH;

	// An explicit mapping, not a name() comparison, so renaming a constant can't silently break it.
	public boolean covers(ClientType clientType) {
		return switch (this) {
			case NEW_CLIENT -> clientType == ClientType.NEW_CLIENT;
			case EXISTING_CLIENT -> clientType == ClientType.EXISTING_CLIENT;
			case BOTH -> true;
		};
	}
}
