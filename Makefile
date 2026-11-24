PLUGIN_NAME ?= deterministic-workflow-cps-plugin
IMAGE ?= $(PLUGIN_NAME):local
IMAGE_TEST ?= $(PLUGIN_NAME)-test:local
HARBOR ?= harbor

CONTAINER_ENGINE ?= $(shell if command -v docker >/dev/null 2>&1; then echo docker; elif command -v podman >/dev/null 2>&1; then echo podman; else echo none; fi)
HARBOR_ENV ?= $(CONTAINER_ENGINE)

.PHONY: all check-engine check-harbor build run test clean package-golden-solution validate oracle nop harbor-oracle harbor-nop

all: test

validate: test

check-harbor:
	@if ! command -v "$(HARBOR)" >/dev/null 2>&1; then \
		echo "Harbor CLI '$(HARBOR)' was not found on PATH." >&2; \
		exit 1; \
	elif ! "$(HARBOR)" run --help >/dev/null 2>&1; then \
		echo "'$(HARBOR)' is not the Harbor task runner (it must support 'harbor run')." >&2; \
		exit 1; \
	fi

check-engine:
	@if [ "$(CONTAINER_ENGINE)" = none ]; then \
		echo "Docker or Podman is required to build the task image." >&2; \
		exit 1; \
	fi

build: check-engine
	$(CONTAINER_ENGINE) build -t "$(IMAGE)" -f environment/Dockerfile environment

build-test: check-engine
	$(CONTAINER_ENGINE) build -t "$(IMAGE_TEST)" -f tests/Dockerfile tests

run: build
	$(CONTAINER_ENGINE) run --rm -v "$$PWD/solution:/solution:ro" "$(IMAGE)" /solution/solve.sh

test: build-test
	@mkdir -p .make-logs/verifier
	$(CONTAINER_ENGINE) run --rm \
		-v "$$PWD/solution:/solution:ro" \
		-v "$$PWD/tests:/tests:ro" \
		-v "$$PWD/.make-logs/verifier:/logs/verifier" \
		"$(IMAGE_TEST)" bash -c '/solution/solve.sh && /tests/test.sh'

package-golden-solution:
	@command -v zip >/dev/null 2>&1 || { echo "zip is required to package the golden solution." >&2; exit 1; }
	@mkdir -p dist
	@rm -f dist/$(PLUGIN_NAME)-golden-solution.zip
	zip -q -r dist/$(PLUGIN_NAME)-golden-solution.zip \
		environment solution metadata tests instruction.md Makefile README.md task.toml .dockerignore \
		-x 'node_modules' 'node_modules/*' '*/node_modules' '*/node_modules/*' \
		   'target' 'target/*' '*/target' '*/target/*' \
		   'node' 'node/*' '*/node' '*/node/*' \
		   '.yarn' '.yarn/*' '*/.yarn' '*/.yarn/*'

clean:
	find . -type d \( -name node_modules -o -name node -o -name target \) -prune -exec rm -rf -- {} +
	rm -rf .make-logs dist work

oracle: harbor-oracle

nop: harbor-nop

harbor-oracle: check-harbor check-engine
	"$(HARBOR)" run -p . -a oracle -e "$(HARBOR_ENV)"

harbor-nop: check-harbor check-engine
	"$(HARBOR)" run -p . -a nop -e "$(HARBOR_ENV)"
