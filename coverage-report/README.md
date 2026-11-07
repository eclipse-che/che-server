# Coverage Report Module

This module aggregates JaCoCo test coverage reports from all project modules.

## How It Works

The module uses JaCoCo's `report-aggregate` goal to combine coverage data from all project modules into a unified report. To do this, it must explicitly declare dependencies on every module that should be included in coverage.

## Dependency Synchronization

The `pom.xml` contains an explicit dependency list for all 67 project modules. This list must stay in sync as modules are added or removed from the project.

### Automatic Validation

The build automatically validates that the dependency list is complete using `verify-dependencies.sh`, which:
- Scans all modules in the project (excluding `pom` and `war` packaging)
- Compares them against dependencies declared in this module
- Fails the build if any module is missing or extra

This validation runs during the Maven `validate` phase whenever the `coverage` profile is active.

### Testing the Validation

To manually test the validation:

```bash
# Should pass with all dependencies present
./verify-dependencies.sh

# Test the build integration
mvn validate -Pcoverage -pl coverage-report
```

### Adding/Removing Modules

When you add or remove a module from the project:

1. **Add the dependency** to `coverage-report/pom.xml`:
   ```xml
   <dependency>
       <groupId>org.eclipse.che.*</groupId>
       <artifactId>module-name</artifactId>
       <version>${project.version}</version>
   </dependency>
   ```

2. **Run the validation** to confirm:
   ```bash
   ./verify-dependencies.sh
   ```

3. **The build will fail** if the list is out of sync, showing which modules are missing or extra.

## Coverage Exclusions

The following patterns are excluded from coverage reports:
- `**/generated/**/*.class` - Generated code
- `**/*DtoImpl.class` - DTO implementation classes

These exclusions are configured in both:
- Root `pom.xml` (coverage profile, applies to individual module reports)
- This module's `pom.xml` (applies to aggregated report)

## Generating Coverage Reports

```bash
# Build with coverage
mvn clean install -Pcoverage

# View aggregated report
open coverage-report/target/site/jacoco-aggregate/index.html
```

## Why Explicit Dependencies?

JaCoCo's `report-aggregate` goal does not auto-discover modules from the Maven reactor. The explicit dependency list is the standard approach used by most multi-module Maven projects with JaCoCo aggregation.

Alternative approaches were tested:
- **Auto-discovery**: JaCoCo doesn't support this
- **Assembly WAR dependencies**: WAR artifacts can't be resolved during reactor build
- **Current approach**: Explicit list with automated validation ✓

## Enforcer Plugin

The Maven Enforcer Plugin is skipped for this module (`<enforcer.skip>true</enforcer.skip>`) because:
- Coverage aggregators pull in many transitive dependencies
- Dependency convergence issues are expected and don't affect coverage reporting
- This is standard practice for coverage aggregator modules
