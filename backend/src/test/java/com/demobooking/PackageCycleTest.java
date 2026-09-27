package com.demobooking;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Top-level packages must not depend on each other in a cycle, so each one can be understood (and
 * changed) from its dependencies up. Sub-packages fold into their top-level slice, so the accepted
 * booking / booking.dto cycle (see booking/dto/package-info.java) is not checked here.
 */
@AnalyzeClasses(packages = "com.demobooking", importOptions = ImportOption.DoNotIncludeTests.class)
class PackageCycleTest {

	@ArchTest
	static final ArchRule topLevelPackagesAreFreeOfCycles = slices().matching("com.demobooking.(*)..").should().beFreeOfCycles();

}
