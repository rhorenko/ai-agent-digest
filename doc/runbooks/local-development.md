# Local development

## Prerequisites

Install JDK 25 and verify `java -version`. Maven is provided by the wrapper. Open the repository root in IntelliJ IDEA and import `pom.xml`; select a JDK 25 project SDK and do not enable preview features.

## Build

From any directory on Windows:

```powershell
& '<repository>\scripts\verify.ps1'
```

From the repository root on other platforms:

```bash
./mvnw verify
```

## Run

```powershell
.\scripts\run-local.ps1
```

The process starts a non-web Spring context, executes the demonstration runner, prints a success message, closes the context, and returns exit code 0. Replace the runner with product behavior during adaptation; do not add a permanent loop unless the product requires a long-running worker.

## Configuration

The baseline uses no environment variables. When a feature adds configuration, document it in the root `.env.example`, bind it explicitly, and keep real `.env` files untracked. IntelliJ run configurations are local and not required for reproducible execution.
