package com.demobooking.customer;

import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Matches email + ID number + account number against the seeded {@link ExistingCustomer}
 * directory, treated as a PII-fishing target: a failed match looks identical whichever field was
 * wrong, and {@link DirectoryValidationRateLimiter} caps attempts per IP.
 *
 * The comparison is a plain string match, not constant-time: the directory is fictional data
 * published in docs/SEED-DATA.md, so a timing side-channel has nothing real to leak.
 */
@Service
public class DirectoryValidationService {

	private final ExistingCustomerRepository repository;

	DirectoryValidationService(ExistingCustomerRepository repository) {
		this.repository = repository;
	}

	public Optional<ExistingCustomer> validate(String email, String idNumber, String accountNumber) {
		return repository
				.findByEmailIgnoreCase(email)
				.filter(candidate -> candidate.getIdNumber().equals(idNumber))
				.filter(candidate -> candidate.getAccountNumber().equals(accountNumber));
	}

}
