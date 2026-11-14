# GitHub Copilot instructions for PayStream

The project conventions live in `CLAUDE.md` at the repository root. Follow them.

Short version: Java 21, Spring Boot 3.5, no Lombok, constructor injection, records for DTOs and events,
idempotent Kafka consumers keyed by payment ID, Flyway for schema changes, ports and adapters around
every cloud or LLM call, React with strict TypeScript.

When generating code for a roadmap step in `README.md`, generate tests with it and keep changes small.
