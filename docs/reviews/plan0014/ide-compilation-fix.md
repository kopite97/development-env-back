# IDE compilation guard correction

Date: 2026-09-17

The resource task used the release-stage guard, blocking IDE classes even though compilation does not connect to a database. The guard now belongs to bootJar, jar, test and bootRun. Resource packaging and the generated legacy-replay-disabled property are unchanged. No stage default was added.

Validation with Java 21 and Gradle 9.7.1, offline:

- classes without categoryStage: passed; processResources executed, classes succeeded.
- bootJar without categoryStage: rejected by validateCategoryStage, as expected, even with classes/resources up to date.
- All 18 source migration SHA-256 values match the Session 1 baseline.

No application startup, migration, DB access or deployment was performed by this fix. A native IDE Java launch does not execute bootRun and can trigger Flyway; follow the local DB transition procedure before launching it. PLAN-0014 remains active and bridge retirement is incomplete.
