package org.jenkinsci.plugins.workflow.cps.determinism;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.jenkinsci.plugins.workflow.cps.determinism.DeterminismTestSupport.RANDOM_SH;
import static org.jenkinsci.plugins.workflow.cps.determinism.DeterminismTestSupport.between;
import static org.jenkinsci.plugins.workflow.cps.determinism.DeterminismTestSupport.define;
import static org.jenkinsci.plugins.workflow.cps.determinism.DeterminismTestSupport.lines;
import static org.jenkinsci.plugins.workflow.cps.determinism.DeterminismTestSupport.q;
import static org.jenkinsci.plugins.workflow.cps.determinism.DeterminismTestSupport.tapeFile;

import hudson.model.Result;
import java.io.File;
import java.util.List;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class DeterministicReplayVerifyTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Same body in both modes; {@code data} is written outside the block so it really changes between builds. */
    private static String script(String mode, String data, File marker) {
        return "node {\n"
                + "  writeFile file: 'data.txt', text: '" + data + "'\n"
                + "  def out\n"
                + "  def content\n"
                + "  deterministicReplay(mode: '" + mode + "', tapePath: 'base.xml') {\n"
                + "    sh \"echo side-effect >> '" + q(marker) + "'\"\n"
                + "    out = sh(returnStdout: true, script: '" + RANDOM_SH + "').trim()\n"
                + "    content = readFile 'data.txt'\n"
                + "  }\n"
                + "  echo \"OUT=${out}|CONTENT=${content}|\"\n"
                + "}\n";
    }

    @Test
    public void replaysRecordedOutcomesWithoutReExecutingSideEffects() throws Exception {
        File marker = tmp.newFile("marker.log");
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        define(p, script("record", "v1", marker));
        WorkflowRun rec = r.buildAndAssertSuccess(p);
        assertEquals(1, lines(marker).size());
        String recordedOut = between(r.getLog(rec), "OUT=", "|");
        assertNotNull(recordedOut);

        define(p, script("verify", "v2", marker));
        WorkflowRun ver = r.buildAndAssertSuccess(p);
        String log = r.getLog(ver);
        assertEquals("non-deterministic stdout replayed from tape", recordedOut, between(log, "OUT=", "|"));
        assertEquals("readFile replayed from tape, not re-read", "v1", between(log, "CONTENT=", "|"));
        assertEquals("sh side effect must not run again", 1, lines(marker).size());
        assertTrue("recorded tape remains available", tapeFile(rec, "base.xml").isFile());
    }

    @Test
    public void replaysRecordedFailuresAndStatuses() throws Exception {
        File marker = tmp.newFile("marker.log");
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        String body = "  def rc\n  def caught = false\n"
                + "  deterministicReplay(mode: '%MODE%', tapePath: 'f.xml') {\n"
                + "    rc = sh(returnStatus: true, script: \"echo s >> '" + q(marker) + "'; exit 7\")\n"
                + "    try { sh \"echo f >> '" + q(marker) + "'; exit 1\" } catch (e) { caught = e.message }\n"
                + "  }\n"
                + "  echo \"RC=${rc}|CAUGHT=${caught}|\"\n";
        define(p, "node {\n" + body.replace("%MODE%", "record") + "}");
        WorkflowRun rec = r.buildAndAssertSuccess(p);
        define(p, "node {\n" + body.replace("%MODE%", "verify") + "}");
        WorkflowRun ver = r.buildAndAssertSuccess(p);
        assertEquals("7", between(r.getLog(ver), "RC=", "|"));
        assertEquals(between(r.getLog(rec), "CAUGHT=", "|"), between(r.getLog(ver), "CAUGHT=", "|"));
        assertEquals(2, lines(marker).size());
    }

    @Test
    public void bodyFailureIsReplayedAndReportedButIsNotADeterminismViolation() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        define(p, "deterministicReplay(mode: 'record', tapePath: 't.xml') { error 'recorded failure' }");
        r.buildAndAssertStatus(Result.FAILURE, p);
        define(p, "deterministicReplay(mode: 'verify', tapePath: 't.xml') { error 'recorded failure' }");
        WorkflowRun ver = r.buildAndAssertStatus(Result.FAILURE, p);
        r.assertLogContains("recorded failure", ver);
    }

    @Test
    public void verifyDefaultsToMostRecentRecording() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        String rec = "node { def o; deterministicReplay(mode: 'record', tapePath: 't.xml') {"
                + " o = sh(returnStdout: true, script: '" + RANDOM_SH + "').trim() }; echo \"OUT=${o}|\" }";
        define(p, rec);
        WorkflowRun b1 = r.buildAndAssertSuccess(p);
        WorkflowRun b2 = r.buildAndAssertSuccess(p);
        String out1 = between(r.getLog(b1), "OUT=", "|");
        String out2 = between(r.getLog(b2), "OUT=", "|");
        assertNotEquals(out1, out2);

        define(p, rec.replace("mode: 'record'", "mode: 'verify'"));
        WorkflowRun latest = r.buildAndAssertSuccess(p);
        assertEquals("defaults to the most recent recording", out2, between(r.getLog(latest), "OUT=", "|"));
    }

    @Test
    public void verifyWithoutRecordedTapeFails() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        define(p, "deterministicReplay(mode: 'verify', tapePath: 'missing.xml') { echo 'x' }");
        r.assertLogContains("recorded execution tape missing.xml", r.buildAndAssertStatus(Result.FAILURE, p));
    }

    @Test
    public void blockStepsAreValidatedButTheirBodiesStillRun() throws Exception {
        File marker = tmp.newFile("marker.log");
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        String body = "node { deterministicReplay(mode: '%MODE%', tapePath: 'b.xml') {"
                + " dir('sub') { sh \"echo x >> '" + q(marker) + "'\"; writeFile file: 'w.txt', text: 'w' } } }";
        define(p, body.replace("%MODE%", "record"));
        r.buildAndAssertSuccess(p);
        define(p, body.replace("%MODE%", "verify"));
        WorkflowRun ver = r.buildAndAssertSuccess(p);
        assertEquals(List.of("x"), lines(marker));
    }
}
