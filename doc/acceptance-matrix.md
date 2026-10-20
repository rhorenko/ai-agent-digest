# Acceptance matrix

Add one row per requirement or acceptance criterion. Multiple rows are appropriate when one criterion needs distinct positive, negative, or boundary checks. Use `Not run` until evidence exists.

| Requirement / criterion | Feature | Check | Evidence | Result |
|---|---|---|---|---|
| _REQ/AC ID_ | _Feature link_ | _Test or command_ | _Report/test link_ | Not run |

## Template maintenance checks

These rows track the repository initialization task separately from future product requirements.

| Check | Evidence | Result |
|---|---|---|
| Maven Wrapper verification | `doc/verification-report.md` | PASS |
| Packaged JAR one-shot launch | `doc/verification-report.md` | PASS |
| Docker image build | `doc/verification-report.md` | PASS |
| Container one-shot launch | `doc/verification-report.md` | PASS |
