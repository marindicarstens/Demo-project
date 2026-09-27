package com.demobooking.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class OptimisticRetryTest {

	/** Counts transactions so the test can prove each attempt got a fresh one. */
	private static final class CountingTransactionManager extends AbstractPlatformTransactionManager {

		final AtomicInteger begun = new AtomicInteger();

		@Override
		protected Object doGetTransaction() {
			return new Object();
		}

		@Override
		protected void doBegin(Object transaction, TransactionDefinition definition) {
			begun.incrementAndGet();
		}

		@Override
		protected void doCommit(DefaultTransactionStatus status) {
		}

		@Override
		protected void doRollback(DefaultTransactionStatus status) {
		}
	}

	private final CountingTransactionManager transactionManager = new CountingTransactionManager();
	private final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

	private static ObjectOptimisticLockingFailureException lostRace() {
		return new ObjectOptimisticLockingFailureException(Object.class, "id");
	}

	@Test
	void retriesALostRaceInAFreshTransaction() {
		AtomicInteger calls = new AtomicInteger();

		String result = OptimisticRetry.execute(transactionTemplate, () -> {
			if (calls.incrementAndGet() < 3) {
				throw lostRace();
			}
			return "done";
		}, 3);

		assertThat(result).isEqualTo("done");
		assertThat(calls).hasValue(3);
		assertThat(transactionManager.begun).hasValue(3);
	}

	@Test
	void retriesAnyOptimisticLockingFailureSubtype() {
		AtomicInteger calls = new AtomicInteger();

		String result = OptimisticRetry.execute(transactionTemplate, () -> {
			if (calls.incrementAndGet() < 2) {
				throw new OptimisticLockingFailureException("lost at commit");
			}
			return "done";
		}, OptimisticRetry.DEFAULT_MAX_ATTEMPTS);

		assertThat(result).isEqualTo("done");
		assertThat(calls).hasValue(2);
	}

	@Test
	void rethrowsTheConflictOnceAttemptsRunOut() {
		AtomicInteger calls = new AtomicInteger();

		assertThatThrownBy(() -> OptimisticRetry.execute(transactionTemplate, () -> {
			calls.incrementAndGet();
			throw lostRace();
		}, 3)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
		assertThat(calls).hasValue(3);
	}

	@Test
	void doesNotRetryOtherFailures() {
		AtomicInteger calls = new AtomicInteger();

		assertThatThrownBy(() -> OptimisticRetry.execute(transactionTemplate, () -> {
			calls.incrementAndGet();
			throw new SlotFullException("full");
		}, 3)).isInstanceOf(SlotFullException.class);
		assertThat(calls).hasValue(1);
	}

}
