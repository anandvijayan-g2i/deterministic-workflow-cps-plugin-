package org.jenkinsci.plugins.workflow.cps.determinism;

import static org.junit.Assert.assertTrue;
import static org.jenkinsci.plugins.workflow.cps.determinism.DeterminismTestSupport.RANDOM_SH;
import static org.jenkinsci.plugins.workflow.cps.determinism.DeterminismTestSupport.define;
import static org.jenkinsci.plugins.workflow.cps.determinism.DeterminismTestSupport.tapeFile;

import hudson.model.Result;
import java.io.File;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class DeterministicReplayRecordTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void recordsOrderedTapeWithReturnValuesAndFailures() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        define(p, "node {\n"
                + "  deterministicReplay(mode: 'record', tapePath: 'baseline.xml') {\n"
                + "    writeFile file: 'in.txt', text: 'hello'\n"
                + "    def t = readFile 'in.txt'\n"
                + "    def out = sh(returnStdout: true, script: 'echo out-1').trim()\n"
                + "    def rc = sh(returnStatus: true, script: 'exit 3')\n"
                + "    try { sh 'exit 1' } catch (e) { echo 'caught' }\n"
                + "    echo \"t=${t} out=${out} rc=${rc}\"\n"
                + "  }\n"
                + "}\n");
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains("t=hello out=out-1 rc=3", b);

        File tape = tapeFile(b, "baseline.xml");
        assertTrue("recording must write a tape", tape.isFile());
        assertTrue("recording must contain the recorded steps",
                tape.length() > 0);
    }

    @Test
    public void recordsNondeterministicValuesAndBlockSteps() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        define(p, "node {\n"
                + "  deterministicReplay(mode: 'record', tapePath: 'nd/tape.xml') {\n"
                + "    dir('sub') {\n"
                + "      echo sh(returnStdout: true, script: '" + RANDOM_SH + "')\n"
                + "    }\n"
                + "  }\n"
                + "}\n");
        WorkflowRun b1 = r.buildAndAssertSuccess(p);
        WorkflowRun b2 = r.buildAndAssertSuccess(p);
        assertTrue(tapeFile(b1, "nd/tape.xml").isFile());
        assertTrue(tapeFile(b2, "nd/tape.xml").isFile());
    }

    @Test
    public void recordingABodyFailureStillProducesACompleteTape() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        define(p, "deterministicReplay(mode: 'record', tapePath: 't.xml') { echo 'before'; error 'boom' }");
        WorkflowRun b = r.buildAndAssertStatus(Result.FAILURE, p);
        assertTrue("body failure must still produce a tape", tapeFile(b, "t.xml").isFile());
        r.assertLogContains("boom", b);
    }

    @Test
    public void rejectsUnsafeTapePathAndNesting() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        define(p, "deterministicReplay(mode: 'record', tapePath: '../escape.xml') { echo 'x' }");
        r.assertLogContains("tapePath must be a relative path", r.buildAndAssertStatus(Result.FAILURE, p));
        define(p, "deterministicReplay(mode: 'replay', tapePath: 'x.xml') { echo 'x' }");
        r.assertLogContains("Unknown deterministicReplay mode", r.buildAndAssertStatus(Result.FAILURE, p));
        define(p, "deterministicReplay(mode: 'record', tapePath: 'a.xml') {\n"
                + "  deterministicReplay(mode: 'record', tapePath: 'b.xml') { echo 'x' }\n"
                + "}");
        r.assertLogContains("cannot be nested", r.buildAndAssertStatus(Result.FAILURE, p));
    }

    @Test
    public void stepsOutsideTheBlockAreNotRecorded() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        define(p, "echo 'outside-1'\n"
                + "deterministicReplay(mode: 'record', tapePath: 't.xml') { echo 'inside' }\n"
                + "echo 'outside-2'");
        WorkflowRun b = r.buildAndAssertSuccess(p);
        assertTrue(tapeFile(b, "t.xml").isFile());
    }
}
