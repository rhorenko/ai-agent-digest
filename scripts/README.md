# PowerShell helpers

These scripts support Windows 11 and work from any current directory. Each resolves the repository root from `$PSScriptRoot`, forwards remaining arguments, shows tool output, and exits non-zero when the underlying command fails.

```powershell
.\scripts\verify.ps1
.\scripts\run-local.ps1
.\scripts\build-container.ps1
.\scripts\run-container.ps1
```

Examples with forwarded arguments:

```powershell
.\scripts\verify.ps1 -DskipTests
.\scripts\build-container.ps1 --no-cache
.\scripts\run-container.ps1 --example-application-argument
```

The final example passes arguments to the executable JAR through Compose. Maven options passed to `run-local.ps1` are Maven/Spring Boot plugin arguments.
