package org.jenkinsci.plugins.workflow.cps.determinism;

import static org.junit.Assert.assertEquals;
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
        WorkflowRun b = r.buildAndAssertStatus(Result.ABORTED, p);
        r.assertLogContains("Determinism violation", b);
        return b;
    }

    @Test
    public void mismatchedArgumentsAbortWithoutRunningTheStep() throws Exception {
        File marker = tmp.newFile("marker.log");
        WorkflowRun b = recordThenVerify(
                "sh \"echo recorded >> '" + q(marker) + "'\"", "sh \"echo drifted >> '" + q(marker) + "'\"");
        assertEquals("the drifted command must never execute", List.of("recorded"), lines(marker));
        r.assertLogContains("arguments of step 'sh'", b);
        r.assertLogContains("echo recorded", b);
        r.assertLogContains("echo drifted", b);
    }

    @Test
    public void mismatchedStepIdAborts() throws Exception {
        WorkflowRun b = recordThenVerify("sh 'true'", "echo 'true'");
        r.assertLogContains("expected step 'sh' but got 'echo'", b);
    }

    @Test
    public void extraStepAborts() throws Exception {
        WorkflowRun b = recordThenVerify("sh 'true'", "sh 'true'\nsh 'echo extra'");
        r.assertLogContains("tape has no more entries", b);
    }

    @Test
    public void missingStepAborts() throws Exception {
        WorkflowRun b = recordThenVerify("sh 'true'\nsh 'echo second'", "sh 'true'");
        r.assertLogContains("next expected step 'sh'", b);
        r.assertLogContains("never ran", b);
    }

    @Test
    public void violationCannotBeSwallowedByTryCatch() throws Exception {
        File marker = tmp.newFile("marker.log");
        WorkflowRun b = recordThenVerify(
                "sh 'echo a'\nsh \"echo later >> '" + q(marker) + "'\"",
                "try { sh 'echo b' } catch (e) { echo 'swallowed' }\nsh \"echo later >> '" + q(marker) + "'\"");
        assertEquals(Result.ABORTED, b.getResult());
        assertEquals("steps after a violation neither run nor replay", List.of("later"), lines(marker));
    }

    @Test
    public void differentStepOrderAborts() throws Exception {
        WorkflowRun b = recordThenVerify("sh 'echo one'\nsh 'echo two'", "sh 'echo two'\nsh 'echo one'");
        r.assertLogContains("position 0", b);
    }
}
