package com.demobooking.branch;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

	List<Branch> findByActiveTrueOrderByNameAsc();

}
