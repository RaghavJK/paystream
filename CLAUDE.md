# PayStream: instructions for AI coding assistants

Read `README.md` first. It has the architecture, the build roadmap and what is verified.

## What this project is for

A learning project. The owner builds each roadmap step himself to gain first-hand experience.
When asked to implement a roadmap step: explain the design first, implement in small commits,
and leave the owner something to do. Do not implement later steps unasked.

## Conventions

- Java 21, Spring Boot 3.5, Maven multi-module. No Lombok. Constructor injection only.
- Records for DTOs and events. Entities are classes with behaviour (`Payment.applyScore`), not setters.
- Each service keeps its own copy of event contracts. No shared library between services.
- Kafka: key every payment event by payment ID. Every consumer must be idempotent.
- Cloud and LLM calls sit behind a port with an offline adapter, so `mvn verify` needs no AWS and no Docker.
- Database changes only through Flyway migrations. Never edit an applied migration.
- Tests: plain unit tests by default. Integration tests use Testcontainers and the `*IT` suffix.
- Frontend: React function components, TypeScript strict mode, all HTTP calls in `web/src/api.ts`.

## Commands

- Backend build and tests: `mvn verify`
- One service: `mvn -pl payment-service spring-boot:run`
- Frontend: `cd web && npm install && npm run dev`
- Local infrastructure: `docker compose up -d`

## Do not

- Do not add agent tools that change data without a human-approval step.
- Do not call Amazon Bedrock outside the `bedrock` profile.
- Do not commit credentials, `.env` files or Terraform state.
