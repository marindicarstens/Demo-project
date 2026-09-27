package com.demobooking.common;

import java.util.function.Supplier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Retries work that lost an optimistic-lock race, giving every attempt its own transaction.
 *
 * Retrying inside one transaction doesn't work: after the conflict the persistence context is
 * inconsistent. And a {@code @Transactional} helper called in a loop from the same class would
 * bypass Spring's proxy, so the loop drives a {@link TransactionTemplate} instead.
 *
 * The work must have no side effects outside its transaction (no emails sent, no Redis writes,
 * no state captured outside the call): a lost race rolls the transaction back and runs the whole
 * body again, so anything it did outside that transaction would happen once per attempt.
 */
public final class OptimisticRetry {

	/** How many attempts every caller gets: enough to ride out a burst of contention on one row. */
	public static final int DEFAULT_MAX_ATTEMPTS = 3;

	private OptimisticRetry() {
	}

	/**
	 * Returns the first attempt that commits; after {@code maxAttempts} lost races, rethrows the
	 * last conflict. Catches the {@link OptimisticLockingFailureException} supertype: Spring
	 * translates a lost race to different subtypes depending on where it is detected.
	 */
	public static <T> T execute(TransactionTemplate transactionTemplate, Supplier<T> work, int maxAttempts) {
		for (int attempt = 1;; attempt++) {
			try {
				return transactionTemplate.execute(status -> work.get());
			} catch (OptimisticLockingFailureException lostRace) {
				if (attempt >= maxAttempts) {
					throw lostRace;
				}
			}
		}
	}

}
