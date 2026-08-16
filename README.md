# Incident management

Incident management system is a help-desk / incident-ticketing backend for Amalitech Services. It allows regular users (employees) to raise incident tickets, which are routed to agents for resolution. Admins configure the system (agent groups, incident types, severities, statuses, locations) and govern what agents can do. User identity is **federated to an external ARMS SSO** — the system does not manage its own credential store for login.

**Core domain objects:** Incident · IncidentType · Agent · AgentGroup · Status · Severity · Location · Message · Media · IncidentLog · IncidentReportLog

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4 |
| Database | PostgreSQL |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Testing | JUnit 5, Spring Boot Test |
| Coverage | JaCoCo |
| Build | Maven |

---

## Prerequisites

Make sure you have the following installed before running the project:

- **Java 21** — [Download](https://adoptium.net/)
- **Maven 3.9+** — [Download](https://maven.apache.org/download.cgi)
- **PostgreSQL 15+** — [Download](https://www.postgresql.org/download/)
- **Git** — [Download](https://git-scm.com/)

---

## Getting Started

### 1. Clone the repository

```bash
git clone <repository-url>
cd hilfe
```

### 2. Set up the database

Create a PostgreSQL database named `hilfe`:

```sql
CREATE DATABASE hilfe;
```

### 3. Configure environment variables

The application reads its configuration from environment variables. You can set them in your shell, a `.env` file (with a tool like [direnv](https://direnv.net/)), or your IDE run configuration.

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/hilfe` | JDBC connection URL |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `SERVER_PORT` | `8080` | HTTP port the app listens on |

> **Note:** Never commit real credentials to version control. Use environment variables or a secrets manager.

### 4. Run the application

```bash
./mvnw spring-boot:run
```

Or with explicit environment variables:

```bash
DB_USERNAME=myuser DB_PASSWORD=secret ./mvnw spring-boot:run
```

The application will start on `http://localhost:8080` by default.

---

## API Documentation

Once the app is running, visit:

- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON:** [http://localhost:8080/api-docs](http://localhost:8080/api-docs)

---

## Health Check

Spring Boot Actuator is enabled. Check the app status at:

```
GET http://localhost:8080/actuator/health
```

---

## Running Tests

```bash
# Run all tests
./mvnw test

# Run tests and generate JaCoCo coverage report
./mvnw verify
```

The JaCoCo HTML coverage report is generated at:

```
target/site/jacoco/index.html
```

> A minimum line coverage of **70%** is enforced on `./mvnw verify`. The build will fail if coverage drops below this threshold.

---

## Generating JavaDocs

```bash
./mvnw javadoc:javadoc
```

The output is written to `target/site/apidocs/index.html`.

---

## Database Migrations

Flyway runs automatically on application startup and applies any pending migration scripts from:

```
src/main/resources/db/migration/
```

Migration file naming convention:

```
V{version}__{description}.sql
```

Examples:
- `V1__initial_schema.sql`
- `V2__add_roles_table.sql`
- `V3__add_user_role_fk.sql`

> **Important:** Never edit an already-applied migration file. Always create a new versioned migration.

---

## Additional docs

- [SLA Tracking and Enforcement](docs/SLA.md)

---

## Git Branching Strategy

This project follows a **Gitflow-based** branching model. All active development originates from the `develop` branch.

### Branch Types

| Branch | Purpose | Branches from | Merges into |
|---|---|---|---|
| `main` | Production-ready code. Always stable and deployable. | — | — |
| `develop` | Integration branch. All completed features land here first. | `main` | `main` (via release) |
| `feature/*` | New features or enhancements. | `develop` | `develop` |
| `bugfix/*` | Non-urgent bug fixes. | `develop` | `develop` |
| `hotfix/*` | Critical production fixes that cannot wait for a release. | `main` | `main` + `develop` |
| `release/*` | Release preparation (version bumps, last-minute fixes). | `develop` | `main` + `develop` |

### Branch Naming

Use lowercase kebab-case with a short descriptive name:

```
feature/user-authentication
feature/add-payment-module
bugfix/fix-null-pointer-on-login
hotfix/patch-security-vulnerability
release/v1.2.0
```

### Workflow

**Starting a new feature:**
```bash
git checkout develop
git pull origin develop
git checkout -b feature/your-feature-name
```

**Finishing a feature:**
```bash
# Push your branch and open a Pull Request into develop
git push origin feature/your-feature-name
# Open PR → develop on GitHub/GitLab
```

**Creating a release:**
```bash
git checkout develop
git pull origin develop
git checkout -b release/v1.0.0
# Bump version, update changelog, final testing
# Merge into main AND back into develop
```

**Hotfix for production:**
```bash
git checkout main
git pull origin main
git checkout -b hotfix/your-fix
# Apply fix, then merge into main AND develop
```

### Pull Request Rules

- Every branch must go through a **Pull Request (PR)** — direct pushes to `main` and `develop` are not allowed.
- PRs require at least **1 approving review** before merging.
- All CI checks (tests, coverage) must pass before merging.
- Delete the branch after merging.
- Write clear PR descriptions explaining *what* changed and *why*.

### Commit Message Convention

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short summary>
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

Examples:
```
feat(auth): add JWT authentication filter
fix(user): handle null email on registration
docs(readme): update setup instructions
test(user): add unit tests for UserService
```

---

## Project Structure

```
src/
├── main/
│   ├── java/com/amalitech/hilfe/
│   │   ├── HilfeApplication.java       # Entry point
│   │   ├── config/                     # Configuration classes (Swagger, Security, etc.)
│   │   ├── controller/                 # REST controllers
│   │   ├── service/                    # Business logic
│   │   ├── repository/                 # Spring Data JPA repositories
│   │   ├── model/                      # JPA entities
│   │   ├── dto/                        # Data Transfer Objects
│   │   └── exception/                  # Global exception handling
│   └── resources/
│       ├── application.yaml            # App configuration
│       └── db/migration/               # Flyway SQL migration scripts
└── test/
    └── java/com/amalitech/hilfe/       # Unit and integration tests
```

---

## Contributing

1. Fork/clone the repository.
2. Create a branch from `develop` following the naming conventions above.
3. Write your code and tests (aim for ≥70% coverage on new code).
4. Open a PR into `develop` with a clear description.
5. Address review comments, then your PR will be merged.

---

## License

This project is licensed under the MIT License.
