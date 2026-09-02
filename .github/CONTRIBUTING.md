# Contributing to Java Portfolio & Enterprise Ecosystem

Thank you for your interest in contributing! We welcome contributions to enhance architecture, optimize performance, and improve documentation.

## Code Standards
1. **Language Standards**: Java 17/21 (Spring Boot 3.2), Python 3.11+.
2. **Defensive Programming**: All database, cache, and external LLM calls must have timeout, retry, and graceful fallback mechanisms.
3. **Zero Dirt & Clean Architecture**: Follow standard domain layered architecture, no hardcoded credentials, and ensure all tests pass with `mvn test` and `pytest`.
4. **Git Commit Style**: Conventional Commits format (`feat:`, `fix:`, `docs:`, `perf:`, `refactor:`, `test:`).
