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

# Function to extract project-level elements from POM
# Extracts elements that appear between </parent> and <dependencies> (or <properties>/<build>)
extract_pom_info() {
    local pom_file="$1"

    # Extract the section after </parent> (or start) but before <dependencies>/<properties>/<build>
    # This section contains the project-level artifactId, packaging, and optionally groupId
    local project_section=$(awk '
        /<\/parent>/ { in_project = 1; next }
        /<dependencies>/ { in_project = 0 }
        /<properties>/ { in_project = 0 }
        /<build>/ { in_project = 0 }
        in_project { print }
    ' "$pom_file")

    # If no parent found (root pom), try from beginning
    if [[ -z "$project_section" ]]; then
        project_section=$(awk '
            /<project/ { in_project = 1 }
            /<parent>/ { in_project = 0 }
            /<dependencies>/ { in_project = 0 }
            /<properties>/ { in_project = 0 }
            /<build>/ { in_project = 0 }
            /<\/parent>/ { in_project = 1 }
            in_project && !/<project/ { print }
        ' "$pom_file")
    fi

    # Extract fields from project section using grep with PCRE
    local artifactId=$(echo "$project_section" | grep -oP '<artifactId>\K[^<]+' | head -1)
    local packaging=$(echo "$project_section" | grep -oP '<packaging>\K[^<]+' | head -1)
    local groupId=$(echo "$project_section" | grep -oP '<groupId>\K[^<]+' | head -1)

    # Default packaging to jar if not specified
    packaging=${packaging:-jar}

    # If groupId is empty, extract from parent section
    if [[ -z "$groupId" ]]; then
        groupId=$(sed -n '/<parent>/,/<\/parent>/p' "$pom_file" | grep -oP '<groupId>\K[^<]+' | head -1)
    fi

    echo "$groupId:$artifactId:$packaging"
}

# Extract all modules from the project (excluding pom packaging, war packaging, and special modules)
declare -A expected_modules=()

while IFS= read -r pom_file; do
    # Skip target directories and coverage-report itself
    [[ "$pom_file" =~ /target/ ]] && continue
    [[ "$pom_file" =~ /coverage-report/ ]] && continue

    # Extract module information
    IFS=: read -r groupId artifactId packaging <<< "$(extract_pom_info "$pom_file")"

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

# Extract dependencies from coverage-report/pom.xml
declare -A actual_deps=()

# Use awk to parse dependency blocks
# Extract the <dependencies> section, then process each <dependency> block
while IFS= read -r dep; do
    actual_deps["$dep"]=1
done < <(awk '
    /<dependencies>/,/<\/dependencies>/ {
        if (/<dependency>/) {
            in_dep = 1
            group_id = ""
            artifact_id = ""
        }
        if (in_dep && /<groupId>/) {
            match($0, /<groupId>([^<]+)<\/groupId>/, arr)
            group_id = arr[1]
        }
        if (in_dep && /<artifactId>/) {
            match($0, /<artifactId>([^<]+)<\/artifactId>/, arr)
            artifact_id = arr[1]
        }
        if (/<\/dependency>/) {
            if (group_id != "" && artifact_id != "" && group_id ~ /^org\.eclipse\.che/) {
                print group_id ":" artifact_id
            }
            in_dep = 0
        }
    }
' "$COVERAGE_POM")

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
