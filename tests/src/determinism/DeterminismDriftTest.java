package org.jenkinsci.plugins.workflow.cps.determinism;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.jenkinsci.plugins.workflow.cps.determinism.DeterminismTestSupport.define;
import static org.jenkinsci.plugins.workflow.cps.determinism.DeterminismTestSupport.lines;
import static org.jenkinsci.plugins.workflow.cps.determinism.DeterminismTestSupport.q;

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

public class DeterminismDriftTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private WorkflowRun recordThenVerify(String recordBody, String verifyBody) throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        define(p, "node { deterministicReplay(mode: 'record', tapePath: 'd.xml') {\n" + recordBody + "\n} }");
        r.buildAndAssertSuccess(p);
        define(p, "node { deterministicReplay(mode: 'verify', tapePath: 'd.xml') {\n" + verifyBody + "\n} }");
        WorkflowRun b = r.waitForCompletion(p.scheduleBuild2(0).waitForStart());
        assertDriftStopped(b);
        return b;
    }

    /**
     * A drift may end the build either as ABORTED (FlowInterruptedException) or as FAILURE
     * caused by hudson.AbortException. Jenkins prints an AbortException as a bare
     * "ERROR: message" line with no stack trace, so any other exception (an NPE, a
     * serialization error, ...) still fails the test.
     */
    private static void assertDriftStopped(WorkflowRun b) throws Exception {
        Result result = b.getResult();
        String log = JenkinsRule.getLog(b);
        if (result == Result.ABORTED) {
            return;
        }
        assertEquals("unexpected build status; build log was:\n" + log, Result.FAILURE, result);
        assertTrue("FAILURE must come from an AbortException; build log was:\n" + log,
                log.contains("ERROR: ") && !log.contains("\tat "));
    }

    private static String appendTo(File marker, String text) {
        return "sh \"echo " + text + " >> '" + q(marker) + "'\"";
    }

    @Test
    public void mismatchedArgumentsAbortWithoutRunningTheStep() throws Exception {
        File marker = tmp.newFile("marker.log");
        recordThenVerify(appendTo(marker, "recorded"), appendTo(marker, "drifted"));
        assertEquals("the drifted command must never execute", List.of("recorded"), lines(marker));
    }

    @Test
    public void mismatchedStepNameAborts() throws Exception {
        File marker = tmp.newFile("marker.log");
        String later = "\n" + appendTo(marker, "later");
        recordThenVerify("sh 'true'" + later, "echo 'true'" + later);
        assertEquals("steps after a violation neither run nor replay", List.of("later"), lines(marker));
    }

    @Test
    public void extraStepAborts() throws Exception {
        recordThenVerify("sh 'true'", "sh 'true'\nsh 'echo extra'");
    }

    @Test
    public void missingStepAborts() throws Exception {
        recordThenVerify("sh 'true'\nsh 'echo second'", "sh 'true'");
    }

    @Test
    public void differentStepOrderAborts() throws Exception {
        File marker = tmp.newFile("marker.log");
        String one = appendTo(marker, "one");
        String two = appendTo(marker, "two");
        recordThenVerify(one + "\n" + two, two + "\n" + one);
        assertEquals("only the recording ran", List.of("one", "two"), lines(marker));
    }

    @Test
    public void violationCannotBeSwallowedByTryCatch() throws Exception {
        File marker = tmp.newFile("marker.log");
        recordThenVerify(
                "sh 'echo a'\n" + appendTo(marker, "later"),
                "try { sh 'echo b' } catch (e) { echo 'swallowed' }\n" + appendTo(marker, "later"));
        assertEquals("steps after a violation neither run nor replay", List.of("later"), lines(marker));
    }
}
