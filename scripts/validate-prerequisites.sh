#!/bin/bash

# TSD UI Agent - Prerequisites Validation Script
# This script validates that all prerequisites are met for running the application

# Note: We don't use 'set -e' because we want to run ALL checks and show a summary,
# not exit on the first failure

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Counters
CHECKS_PASSED=0
CHECKS_FAILED=0
CHECKS_OPTIONAL=0

# Print colored message
print_success() {
    echo -e "${GREEN}✓${NC} $1"
    ((CHECKS_PASSED++))
}

print_error() {
    echo -e "${RED}✗${NC} $1"
    ((CHECKS_FAILED++))
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1 (optional)"
    ((CHECKS_OPTIONAL++))
}

print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

print_header() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

# Check if a command exists
check_command() {
    local cmd=$1
    local required=$2
    local version_flag=${3:-"--version"}

    if command -v "$cmd" &> /dev/null; then
        local version_output=$($cmd $version_flag 2>&1 | head -1)
        print_success "$cmd is installed: $version_output"
        return 0
    else
        if [ "$required" = "true" ]; then
            print_error "$cmd is not installed (required)"
            return 1
        else
            print_warning "$cmd is not installed"
            return 0
        fi
    fi
}

# Check Java version
check_java_version() {
    if command -v java &> /dev/null; then
        local java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
        local major_version=$(echo $java_version | cut -d'.' -f1)

        if [ "$major_version" -eq 25 ]; then
            print_success "JDK 25 is installed: $java_version"
            return 0
        else
            print_error "Java is installed but version is $java_version (JDK 25 required)"
            return 1
        fi
    else
        print_error "Java is not installed (JDK 25 required)"
        return 1
    fi
}

# Check PostgreSQL
check_postgresql() {
    if command -v psql &> /dev/null; then
        local pg_version=$(psql --version 2>&1)
        print_success "PostgreSQL client installed: $pg_version"

        # Try to connect to check if server is running
        if psql -U postgres -d postgres -c '\q' 2>/dev/null; then
            print_success "PostgreSQL server is running"
        else
            print_info "PostgreSQL client found but server not running (Quarkus Dev Services will provide it)"
        fi
        return 0
    else
        print_info "PostgreSQL not installed (Quarkus Dev Services will provide it in dev mode)"
        return 0
    fi
}

# Check Docker or Podman
check_container_runtime() {
    local has_docker=false
    local has_podman=false

    if command -v docker &> /dev/null; then
        local docker_version=$(docker --version)
        print_success "Docker is installed: $docker_version"
        has_docker=true

        # Check Docker Compose
        if docker compose version &> /dev/null; then
            local compose_version=$(docker compose version)
            print_success "Docker Compose is installed: $compose_version"
        else
            print_warning "Docker Compose is not available"
        fi
    fi

    if command -v podman &> /dev/null; then
        local podman_version=$(podman --version)
        print_success "Podman is installed: $podman_version"
        has_podman=true
    fi

    if [ "$has_docker" = false ] && [ "$has_podman" = false ]; then
        print_warning "Neither Docker nor Podman is installed (required for Docker execution mode)"
    fi
}

# Check coding agents
check_coding_agents() {
    local has_agent=false

    if command -v claude &> /dev/null; then
        local claude_version=$(claude --version 2>&1)
        print_success "Claude CLI is installed: $claude_version"
        has_agent=true
    fi

    if command -v opencode &> /dev/null; then
        local opencode_version=$(opencode --version 2>&1)
        print_success "OpenCode CLI is installed: $opencode_version"
        has_agent=true
    fi

    if [ "$has_agent" = false ]; then
        print_warning "No coding agent (Claude or OpenCode) is installed (required for plan execution)"
    fi
}

# Check Maven wrapper
check_maven_wrapper() {
    if [ -f "./mvnw" ]; then
        print_success "Maven wrapper (mvnw) is present"

        if [ -x "./mvnw" ]; then
            print_success "Maven wrapper is executable"
        else
            print_warning "Maven wrapper is not executable. Run: chmod +x ./mvnw"
        fi
    else
        print_error "Maven wrapper (mvnw) is not found in current directory"
        print_info "Are you in the project root directory?"
        return 1
    fi
}

# Main validation flow
main() {
    echo ""
    echo "TSD UI Agent - Prerequisites Validation"
    echo "========================================"

    print_header "Common Requirements"
    check_command git true

    print_header "Filesystem Mode (Local Development with Git Worktrees)"
    print_info "Required for Local filesystem workspaces"
    check_java_version
    check_maven_wrapper
    check_postgresql
    check_coding_agents

    print_header "Docker Mode (Devcontainer-based Execution)"
    print_info "Required for Container workspaces"
    check_container_runtime
    check_command devcontainer false

    print_header "Summary"
    echo ""
    echo -e "${GREEN}Passed:${NC}   $CHECKS_PASSED"
    echo -e "${RED}Failed:${NC}   $CHECKS_FAILED"
    echo -e "${YELLOW}Optional:${NC} $CHECKS_OPTIONAL"
    echo ""

    if [ $CHECKS_FAILED -eq 0 ]; then
        echo -e "${GREEN}✓ All required prerequisites are met!${NC}"
        echo ""
        echo "You can start the application in different execution modes:"
        echo ""
        echo -e "${BLUE}Filesystem Mode (Local Development):${NC}"
        echo "  Start: ./mvnw quarkus:dev"
        echo "  Note: PostgreSQL provided by Quarkus Dev Services"
        echo ""
        echo "  Workspace types are selected per-workspace from the UI:"
        echo ""
        echo -e "${BLUE}Local filesystem:${NC}"
        echo "  Requires: JDK 25, Claude/OpenCode CLI"
        echo ""
        echo -e "${BLUE}Container (Devcontainer):${NC}"
        echo "  Requires: Docker/Podman, Devcontainer CLI"
        echo ""
        echo -e "${BLUE}Kubernetes (Eclipse Che/K8s):${NC}"
        echo "  Requires: Kubernetes cluster, Devfile support"
        exit 0
    else
        echo -e "${RED}✗ Some required prerequisites are missing.${NC}"
        echo ""
        echo "Note: Not all prerequisites are required for every execution mode."
        echo "Review the sections above to see which mode's requirements you need to fulfill."
        echo ""
        echo "For quick local development, consider FILESYSTEM mode which only requires:"
        echo "  - Git, JDK 25, Maven wrapper, Claude/OpenCode CLI"
        echo "  - PostgreSQL is automatically provided by Quarkus Dev Services"
        exit 1
    fi
}

# Run main function
main
