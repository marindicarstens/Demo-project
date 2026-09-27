package com.demobooking.branch;

import static org.assertj.core.api.Assertions.assertThat;

import com.demobooking.IntegrationTest;
import com.demobooking.branch.dto.AvailabilitySlotResponse;
import com.demobooking.branch.dto.BranchResponse;
import com.demobooking.branch.dto.ServiceTypeResponse;
import com.demobooking.common.AppTimeZone;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Against real Postgres + Redis (Testcontainers), not mocked.
 * Exercises the seed data (docs/SEED-DATA.md) and the generated availability window
 * (TimeSlotGenerationService) through the actual HTTP endpoints.
 */
@IntegrationTest
class BranchApiIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private BranchRepository branchRepository;

	@Test
	void listBranches_returnsAllSixSeededBranches() {
		ResponseEntity<BranchResponse[]> response = restTemplate.getForEntity("/api/v1/branches", BranchResponse[].class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).hasSize(6);
		assertThat(response.getBody()).extracting(BranchResponse::name).contains("Sandton City Branch", "Gateway Branch");
	}

	@Test
	void listServiceTypes_newClientFlow_excludesExistingClientOnlyTypes() {
		ResponseEntity<ServiceTypeResponse[]> response =
				restTemplate.getForEntity("/api/v1/service-types?clientType=NEW_CLIENT", ServiceTypeResponse[].class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<String> names = List.of(response.getBody()).stream().map(ServiceTypeResponse::name).toList();
		assertThat(names).contains("Open a new account", "New client consultation", "General enquiry");
		assertThat(names).doesNotContain("Loan consultation", "Dispute resolution");
	}

	@Test
	void listServiceTypes_existingClientFlow_excludesNewClientOnlyTypes() {
		ResponseEntity<ServiceTypeResponse[]> response =
				restTemplate.getForEntity("/api/v1/service-types?clientType=EXISTING_CLIENT", ServiceTypeResponse[].class);

		List<String> names = List.of(response.getBody()).stream().map(ServiceTypeResponse::name).toList();
		assertThat(names).contains("Loan consultation", "Dispute resolution", "General enquiry");
		assertThat(names).doesNotContain("Open a new account", "New client consultation");
	}

	@Test
	void getAvailability_returnsGeneratedSlotsWithRemainingCapacity() {
		UUID branchId = firstBranchId();
		UUID serviceTypeId = firstServiceTypeId("EXISTING_CLIENT");
		LocalDate nextMonday = LocalDate.now(AppTimeZone.ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

		ResponseEntity<AvailabilitySlotResponse[]> response = restTemplate.getForEntity(
				"/api/v1/branches/{id}/availability?date={date}&serviceTypeId={serviceTypeId}",
				AvailabilitySlotResponse[].class,
				branchId,
				nextMonday,
				serviceTypeId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotEmpty();
		assertThat(response.getBody()[0].remainingCapacity()).isGreaterThan(0);
		assertThat(response.getBody()[0].date()).isEqualTo(nextMonday);
	}

	@Test
	void getAvailability_unknownBranch_returns404ProblemDetail() {
		UUID unknownBranchId = UUID.randomUUID();
		UUID serviceTypeId = firstServiceTypeId("EXISTING_CLIENT");

		ResponseEntity<ProblemDetail> response = restTemplate.getForEntity(
				"/api/v1/branches/{id}/availability?date={date}&serviceTypeId={serviceTypeId}",
				ProblemDetail.class,
				unknownBranchId,
				LocalDate.now(AppTimeZone.ZONE).plusDays(1),
				serviceTypeId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void availability_inactiveBranch_returns404() {
		Branch branch = branchRepository.findByActiveTrueOrderByNameAsc().getLast();
		UUID serviceTypeId = firstServiceTypeId("EXISTING_CLIENT");
		ReflectionTestUtils.setField(branch, "active", false);
		branchRepository.saveAndFlush(branch);
		try {
			ResponseEntity<ProblemDetail> response = restTemplate.getForEntity(
					"/api/v1/branches/{id}/availability?date={date}&serviceTypeId={serviceTypeId}",
					ProblemDetail.class,
					branch.getId(),
					LocalDate.now(AppTimeZone.ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY)),
					serviceTypeId);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		} finally {
			// Shared database - other tests expect all six seeded branches active.
			ReflectionTestUtils.setField(branch, "active", true);
			branchRepository.saveAndFlush(branch);
		}
	}

	private UUID firstBranchId() {
		BranchResponse[] branches = restTemplate.getForObject("/api/v1/branches", BranchResponse[].class);
		return branches[0].id();
	}

	private UUID firstServiceTypeId(String clientType) {
		ServiceTypeResponse[] serviceTypes =
				restTemplate.getForObject("/api/v1/service-types?clientType=" + clientType, ServiceTypeResponse[].class);
		return serviceTypes[0].id();
	}

}
