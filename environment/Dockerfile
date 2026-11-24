# Harbor agent environment: jenkinsci/workflow-cps-plugin at the base snapshot, with every Maven
# artifact needed to compile and test the `plugin` module pre-fetched.
FROM maven:3.9.11-eclipse-temurin-21@sha256:463a1849665463254b2dd56e3a5b316f1596bc93d0571065c06ea05bb48ab8f4

RUN set -eux; \
    apt_attempt=0; \
    until apt-get update; do \
        apt_attempt=$((apt_attempt + 1)); \
        [ "$apt_attempt" -lt 5 ] || exit 1; \
        sleep "$apt_attempt"; \
    done; \
    apt-get install -y --no-install-recommends \
        git=1:2.43.0-1ubuntu7.4 \
        patch=2.7.6-7build3; \
    apt-get clean; \
    rm -rf /var/lib/apt/lists/*

ENV MAVEN_OPTS="-Xmx4g" \
    MVN_FLAGS="-B -ntp -Dspotless.check.skip=true -Dspotbugs.skip=true -Denforcer.skip=true -Dcheckstyle.skip=true -Dskip.yarn=true -Dskip.installyarn=true -Dskip.installnodenpm=true -Dskip.npm=true -Djenkins.test.timeout=900"

WORKDIR /app
COPY repo/ /app/

# The source snapshot may contain a Corepack symlink pointing to the host machine.
RUN if [ -L plugin/node/corepack ] && [ ! -e plugin/node/corepack ]; then rm plugin/node/corepack; fi

# A git repository lets graders apply patches with `git apply` and diff the agent's work.
RUN git init -q && git add -A \
 && git -c user.email=task@benchpress -c user.name=benchpress commit -qm "base"

# 1. Build and install the sibling modules the plugin depends on (groovy-cps, dgm-builder).
RUN mvn $MVN_FLAGS -DskipTests install -pl dgm-builder,lib -am

# 2. Resolve the plugin's compile/test dependencies, the hpi + surefire plugins and the
#    JUnit 4 provider by running one fast existing test (online, at image build time only).
RUN mvn $MVN_FLAGS -pl plugin dependency:go-offline
RUN mvn $MVN_FLAGS -pl plugin test -Dtest=CpsStepContextTest -Dsurefire.failIfNoSpecifiedTests=false \
 && git clean -fdxq && git status --porcelain
