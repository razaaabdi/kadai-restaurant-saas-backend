# Repository instructions

These instructions apply to every coding agent working in this backend repository.

- Whenever application code, database migrations, configuration, dependencies, architecture, API contracts, security behavior, operational scripts, or developer workflow changes, update `BACKEND_DEVELOPER_WORKFLOW.md` in the same change.
- Update the relevant section rather than adding an unrelated timestamp or placeholder.
- Never expose credentials, tokens, customer data, or other secrets in documentation, logs, tests, or responses.
- Never edit an applied Flyway migration. Add a forward-only migration and document its operational impact.
- Documentation-only changes do not require another documentation update.
- Before handing off a backend change, run the relevant tests/build and report the result.

