#!/bin/bash
# Verify that coverage-report/pom.xml has dependencies on all project modules
# This script ensures the coverage aggregation stays in sync as modules are added/removed

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COVERAGE_POM="$SCRIPT_DIR/pom.xml"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "Verifying coverage-report dependencies are complete..."

# Extract all modules from the project (excluding pom packaging, war packaging, and special modules)
declare -A expected_modules=()

while IFS= read -r pom_file; do
    # Skip target directories and coverage-report itself
    [[ "$pom_file" =~ /target/ ]] && continue
    [[ "$pom_file" =~ /coverage-report/ ]] && continue

    # Extract module information using xmllint
    artifactId=$(xmllint --xpath "string(//*[local-name()='project']/*[local-name()='artifactId'])" "$pom_file" 2>/dev/null || echo "")
    packaging=$(xmllint --xpath "string(//*[local-name()='project']/*[local-name()='packaging'])" "$pom_file" 2>/dev/null || echo "jar")
    groupId=$(xmllint --xpath "string(//*[local-name()='project']/*[local-name()='groupId'])" "$pom_file" 2>/dev/null || echo "")

    # If groupId is empty, try parent groupId
    if [[ -z "$groupId" ]]; then
        groupId=$(xmllint --xpath "string(//*[local-name()='project']/*[local-name()='parent']/*[local-name()='groupId'])" "$pom_file" 2>/dev/null || echo "")
    fi

    # Skip if essential info is missing
    [[ -z "$artifactId" ]] && continue
    [[ -z "$groupId" ]] && continue

    # Skip root project and non-jar/non-war modules
    [[ "$artifactId" == "che-server" ]] && continue
    [[ "$packaging" == "pom" ]] && continue
    [[ "$packaging" == "war" ]] && continue

    # Add to expected modules
    expected_modules["$groupId:$artifactId"]=1
done < <(find "$PROJECT_ROOT" -name pom.xml -type f)

# Extract dependencies from coverage-report/pom.xml using XPath to count and iterate
declare -A actual_deps=()

# Count total dependencies
dep_count=$(xmllint --xpath "count(//*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency'])" "$COVERAGE_POM" 2>/dev/null)

for ((i=1; i<=dep_count; i++)); do
    groupId=$(xmllint --xpath "string(//*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency'][$i]/*[local-name()='groupId'])" "$COVERAGE_POM" 2>/dev/null || echo "")
    artifactId=$(xmllint --xpath "string(//*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency'][$i]/*[local-name()='artifactId'])" "$COVERAGE_POM" 2>/dev/null || echo "")

    [[ -z "$groupId" ]] && continue
    [[ -z "$artifactId" ]] && continue

    # Only track project dependencies (org.eclipse.che.*)
    [[ "$groupId" =~ ^org\.eclipse\.che ]] || continue

    actual_deps["$groupId:$artifactId"]=1
done

# Compare the two sets
missing=()
extra=()

for module in "${!expected_modules[@]}"; do
    if [[ -z "${actual_deps[$module]:-}" ]]; then
        missing+=("$module")
    fi
done

for dep in "${!actual_deps[@]}"; do
    if [[ -z "${expected_modules[$dep]:-}" ]]; then
        extra+=("$dep")
    fi
done

# Report results
exit_code=0

if [[ ${#missing[@]} -gt 0 ]]; then
    echo -e "${RED}ERROR: Missing dependencies in coverage-report/pom.xml:${NC}"
    printf '  %s\n' "${missing[@]}" | sort
    echo
    exit_code=1
fi

if [[ ${#extra[@]} -gt 0 ]]; then
    echo -e "${YELLOW}WARNING: Extra dependencies in coverage-report/pom.xml (modules may have been removed):${NC}"
    printf '  %s\n' "${extra[@]}" | sort
    echo
    exit_code=1
fi

if [[ $exit_code -eq 0 ]]; then
    echo -e "${GREEN}✓ Coverage dependencies are in sync (${#expected_modules[@]} modules)${NC}"
else
    echo -e "${RED}✗ Coverage dependencies are out of sync${NC}"
    echo
    echo "To fix this, update coverage-report/pom.xml to include all project modules."
    echo "Expected: ${#expected_modules[@]} modules, Found: ${#actual_deps[@]} dependencies"
fi

exit $exit_code
