package org.jenkinsci.plugins.workflow.cps.determinism;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/** Helpers shared by the determinism engine tests. */
final class DeterminismTestSupport {

    /** A shell command whose output differs on every invocation. */
    static final String RANDOM_SH = "cat /proc/sys/kernel/random/uuid";

    private DeterminismTestSupport() {}

    static void define(WorkflowJob p, String script) throws Exception {
        p.setDefinition(new CpsFlowDefinition(script, true));
    }

    static File tapeFile(WorkflowRun b, String tapePath) {
        return new File(new File(b.getRootDir(), "replay-tapes"), tapePath);
    }

    static List<String> lines(File f) throws IOException {
        return f.exists() ? Files.readAllLines(f.toPath(), StandardCharsets.UTF_8) : List.of();
    }

    /** Escapes a path for use inside a single-quoted shell word inside a double-quoted Groovy string. */
    static String q(File f) {
        return f.getAbsolutePath().replace("\\", "/");
    }

    static String between(String log, String start, String end) {
        int i = log.indexOf(start);
        if (i < 0) {
            return null;
        }
        int j = log.indexOf(end, i + start.length());
        return log.substring(i + start.length(), j < 0 ? log.length() : j);
    }
}
