# workflow-cps-deterministic-replay (Harbor task)

```
instruction.md          prompt given to the agent
task.toml               schema 1.3; verifier runs in the agent's environment (shared)
environment/Dockerfile  base snapshot in /app + offline Maven cache (network only at build time)
environment/repo/       jenkinsci/workflow-cps-plugin base snapshot
solution/solve.sh       oracle: git apply solution.patch
tests/test.sh           copies tests/src/determinism/*.java into the repo, runs them offline, writes reward
```

The verifier runs in the agent's container (`environment_mode = "shared"`):
`tests/test.sh` copies the hidden tests into `/app` and runs them with Maven offline.
Only image preparation needs network access, to download dependencies.

Build and run the solution in a container:
```bash
make build  # build the offline Maven environment image
make run    # apply the reference patch and compile
make test   # apply the patch and run the verifier test suite
make package-golden-solution  # create dist/golden-solution.zip
make clean  # remove Maven targets and generated output/work folders
```

`make test` also writes verifier output to `.make-logs/verifier/`. The Makefile
uses Docker when its CLI is installed and otherwise falls back to Podman.

To run either Harbor agent instead, use `make harbor-oracle` or `make harbor-nop`
(`make oracle` and `make nop` are aliases). These targets first verify that the
Harbor task runner is installed and supports `harbor run`.
