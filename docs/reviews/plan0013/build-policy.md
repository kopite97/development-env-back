# Final-only build policy validation

Date: 2026-09-16

Historical Session 1 checkpoint, before Widget implementation. The current V18 artifact and completed validation are recorded in [Session 5](session-5.md) and [final artifact inspection](final-artifact.json); the V17-only digest below is intentionally preserved as earlier evidence.

The user approved final-only Widget releases with a preserved pre-Widget bridge artifact/source. This resolves the artifact-policy question in Session 1, not the entire contract gate. No shared agent guide change is necessary: the existing JPA, Flyway and schema-validation rules remain applicable.

## Preserved bridge

Before editing build.gradle or Dockerfile, built `bootJar -PcategoryStage=bridge --no-daemon` from application/build source at commit `a80fec5781943c34adc6457213d4c366009013d1`. Existing uncommitted planning documents and test-only configuration were not part of the JAR.

- Local archive: `.gradle/plan0013/bridge-a80fec5781943c34adc6457213d4c366009013d1/devspace-bridge.jar` (ignored, not a durable off-machine backup).
- SHA-256: `56087B56B6FA3EC717428E8A1B3D5C7849D49A179B1643F5D05B154E9AFE692A`.
- Inspected ZIP entries: V15/V16 present, V17 absent.
- This is a locally rebuilt artifact, not a verified copy of a deployed production artifact. Preserve deployed digests and backups separately before an approved production cutover. No Git tag, remote branch or deployment was created.

## Initial build-policy checkpoint

Gradle requires explicit `-PcategoryStage=final` for configured tasks, including tests. Docker requires `--build-arg CATEGORY_STAGE=final` and checks it before Gradle. Missing, bridge and invalid values fail; neither build path silently defaults to final. Gradle always includes the existing contract migration directory and generates `app.category-transition.legacy-replay-enabled=false`. Applied SQL files and V17 drain checks are unchanged.

Commands used Java 21.0.10 via `-Dorg.gradle.java.home="C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot"` and the existing Gradle wrapper:

```text
./gradlew.bat help --no-daemon
./gradlew.bat help -PcategoryStage=bridge --no-daemon
./gradlew.bat help -PcategoryStage=invalid --no-daemon
./gradlew.bat -PcategoryStage=final -I docs/reviews/plan0013/isolated-tests.init.gradle bootJar test --tests com.kopite.devspace.FinalCategoryRuntimeTests --tests com.kopite.devspace.CategoryCutoverMigrationTests --no-daemon
```

The first three commands failed with the intended final-only message: [omitted](build-omitted.txt), [bridge](build-bridge.txt), [invalid](build-invalid.txt). The final command succeeded. Seven migration checks and one final-runtime check passed with zero failures/errors/skips against isolated PostgreSQL 16.4 containers, including V17 startup/Hibernate validation and rejection before the durable drain deadline/live legacy expiry. See [test summary](final-build-tests.json). No application.yml/.env or production database was used.

Final JAR inspection confirmed 17 migrations (V1-V17) and replay-enabled=false. SHA-256: `C45B4D84DC2E512DA1D65685F9C583EACE928DF78DDBB6A89AA0E33338CA7F84`. No Widget migration or API is present yet. Dockerfile guard and argument forwarding were reviewed statically; a full Docker image build was not run. Full application regression remains a later session check. Markdown links and `git diff --check` were checked separately.

No production migration/deployment, frontend work, dependency change, environment-variable update or legacy JSONB/mapping cleanup occurred.

## Follow-up: Gradle project import

The user reported the configuration-time exception at build.gradle line 55. Reproduced with `help` without categoryStage: project evaluation failed before any task could run, also preventing ordinary IDE model import. This was an overly broad placement of the guard, not a Groovy syntax error.

Moved validation into `validateCategoryStage`, a dependency of `processResources`. Project configuration and metadata tasks are available without a release choice; normal application build/test/run task graphs still validate explicit final selection before resources are prepared. The validation task has no outputs, so existing up-to-date final resources do not bypass it. No default final value, migration change, dependency, IDE-specific setting or shared-guide change was introduced.

Follow-up command results are recorded in [Gradle import checks](gradle-import-checks.json): configuration-only `help` without an option, explicit-final `bootJar`, then omitted/bridge/invalid `bootJar` against the existing outputs. All commands use Java 21 and offline Gradle; no application, tests, database or deployment is started. The historical `help` failures above describe the initial implementation and are superseded by this correction.
