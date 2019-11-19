/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.pipeline.modeldefinition;

import hudson.Extension;
import hudson.model.Item;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Phaser;

import static org.junit.Assert.*;

public class TriggersTest extends AbstractModelDefTest {

    @Test
    public void simpleTriggers() throws Exception {
        WorkflowRun b = expect("simpleTriggers")
                .logContains("[Pipeline] { (foo)", "hello")
                .logNotContains("[Pipeline] { (Post Actions)")
                .go();

        WorkflowJob p = b.getParent();

        PipelineTriggersJobProperty triggersJobProperty = p.getTriggersJobProperty();
        assertNotNull(triggersJobProperty);
        assertEquals(1, triggersJobProperty.getTriggers().size());
        TimerTrigger.DescriptorImpl timerDesc = j.jenkins.getDescriptorByType(TimerTrigger.DescriptorImpl.class);

        Trigger trigger = triggersJobProperty.getTriggerForDescriptor(timerDesc);
        assertNotNull(trigger);

        assertTrue(trigger instanceof TimerTrigger);
        TimerTrigger timer = (TimerTrigger) trigger;
        assertEquals("@daily", timer.getSpec());
    }

    @Test
    public void simpleTriggersWithOutsideVarAndFunc() throws Exception {
        WorkflowRun b = expect("simpleTriggersWithOutsideVarAndFunc")
                .logContains("[Pipeline] { (foo)", "hello")
                .logNotContains("[Pipeline] { (Post Actions)")
                .go();

        WorkflowJob p = b.getParent();

        PipelineTriggersJobProperty triggersJobProperty = p.getTriggersJobProperty();
        assertNotNull(triggersJobProperty);
        assertEquals(1, triggersJobProperty.getTriggers().size());
        TimerTrigger.DescriptorImpl timerDesc = j.jenkins.getDescriptorByType(TimerTrigger.DescriptorImpl.class);

        Trigger trigger = triggersJobProperty.getTriggerForDescriptor(timerDesc);
        assertNotNull(trigger);

        assertTrue(trigger instanceof TimerTrigger);
        TimerTrigger timer = (TimerTrigger) trigger;
        assertEquals("@daily", timer.getSpec());
    }

    @Ignore("Triggers are set before withEnv is called.")
    @Test
    public void envVarInTriggers() throws Exception {
        WorkflowRun b = expect("environment/envVarInTriggers")
                .logContains("[Pipeline] { (foo)", "hello")
                .logNotContains("[Pipeline] { (Post Actions)")
                .go();

        WorkflowJob p = b.getParent();

        PipelineTriggersJobProperty triggersJobProperty = p.getTriggersJobProperty();
        assertNotNull(triggersJobProperty);
        assertEquals(1, triggersJobProperty.getTriggers().size());
        TimerTrigger.DescriptorImpl timerDesc = j.jenkins.getDescriptorByType(TimerTrigger.DescriptorImpl.class);

        Trigger trigger = triggersJobProperty.getTriggerForDescriptor(timerDesc);
        assertNotNull(trigger);

        assertTrue(trigger instanceof TimerTrigger);
        TimerTrigger timer = (TimerTrigger) trigger;
        assertEquals("@daily", timer.getSpec());
    }

    @Issue("JENKINS-44149")
    @Test
    public void triggersRemoved() throws Exception {
        WorkflowRun b = getAndStartNonRepoBuild("simpleTriggers");
        j.assertBuildStatusSuccess(j.waitForCompletion(b));

        WorkflowJob job = b.getParent();
        PipelineTriggersJobProperty triggersJobProperty = job.getProperty(PipelineTriggersJobProperty.class);
        assertNotNull(triggersJobProperty);
        assertEquals(1, triggersJobProperty.getTriggers().size());

        job.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources("propsTriggersParamsRemoved"), true));
        j.buildAndAssertSuccess(job);

        assertNull(job.getProperty(PipelineTriggersJobProperty.class));
    }

    @Issue("JENKINS-44621")
    @Test
    public void externalTriggersNotRemoved() throws Exception {
        WorkflowRun b = getAndStartNonRepoBuild("simpleTriggers");
        j.assertBuildStatusSuccess(j.waitForCompletion(b));

        WorkflowJob job = b.getParent();
        PipelineTriggersJobProperty triggersJobProperty = job.getProperty(PipelineTriggersJobProperty.class);
        assertNotNull(triggersJobProperty);
        assertEquals(1, triggersJobProperty.getTriggers().size());

        List<Trigger> newTriggers = new ArrayList<>();
        newTriggers.addAll(triggersJobProperty.getTriggers());
        newTriggers.add(new SCMTrigger("1 1 1 * *"));
        job.removeProperty(triggersJobProperty);
        job.addProperty(new PipelineTriggersJobProperty(newTriggers));

        job.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources("propsTriggersParamsRemoved"), true));
        j.buildAndAssertSuccess(job);

        PipelineTriggersJobProperty newProp = job.getProperty(PipelineTriggersJobProperty.class);
        assertNotNull(newProp);
        assertEquals(1, newProp.getTriggers().size());
        Trigger t = newProp.getTriggers().get(0);
        assertNotNull(t);
        assertTrue(t instanceof SCMTrigger);
    }

    @Issue("JENKINS-47780")
    @Test
    public void actualTriggerCorrectScope() throws Exception {
        WorkflowRun b = getAndStartNonRepoBuild("simpleTriggers");
        j.assertBuildStatusSuccess(j.waitForCompletion(b));

        expect("actualTriggerCorrectScope")
                .go();
    }

    @LocalData
    @Test
    public void doNotRestartEqualTriggers() throws Exception {

        // Create the first build. The DeclarativeJobPropertyTrackerAction action will be created.
        WorkflowRun b = getAndStartNonRepoBuild("simplePipelineWithTestTrigger");
        j.assertBuildStatusSuccess(j.waitForCompletion(b));

        // get trigger from job
        WorkflowJob job = b.getParent();
        PipelineTriggersJobProperty triggersJobProperty = job.getProperty(PipelineTriggersJobProperty.class);
        TestTrigger myTrigger1 = (TestTrigger)triggersJobProperty.getTriggers().get(0);

        System.out.println("after build " + b.getId() + ": myTrigger1 starts(): " + myTrigger1.getPhaser().getPhase());

        // Since the tracker action was not previously available,
        // the trigger will get restarted and the phaser will be incremented
        assertTrue(myTrigger1.getPhaser().getPhase() == 1);
        assertTrue(myTrigger1.isStarted);

        // Build it again.
        b = j.buildAndAssertSuccess(job);
        triggersJobProperty = job.getProperty(PipelineTriggersJobProperty.class);
        myTrigger1 = (TestTrigger)triggersJobProperty.getTriggers().get(0);
        System.out.println("after build " + b.getId() + ": myTrigger1 starts(): " + myTrigger1.getPhaser().getPhase());

        // Since the trigger is the same (the config was not changed between builds),
        // it will not get restarted,
        // and the phaser will NOT be incremented.
        assertTrue(myTrigger1.getPhaser().getPhase() == 1);
        assertTrue(myTrigger1.isStarted);

        // Let simulate someone changing the trigger config
        triggersJobProperty = job.getProperty(PipelineTriggersJobProperty.class);
        job.removeProperty(triggersJobProperty);

        List newTriggers = new ArrayList<>();
        TestTrigger myTrigger2 = new TestTrigger();
        myTrigger2.setName("myTrigger2");
        newTriggers.add(myTrigger2);
        job.addProperty(new PipelineTriggersJobProperty(newTriggers));

        // Build it again with a new trigger config
        b = j.buildAndAssertSuccess(job);
        triggersJobProperty = job.getProperty(PipelineTriggersJobProperty.class);
        List<Trigger<?>> triggerList = triggersJobProperty.getTriggers();
        for (Trigger t: triggerList) {
            TestTrigger testT = (TestTrigger)t;
            String name = testT.name;
            if (name.equals("myTrigger1")) {
                // myTrigger1 is being removed. it will NOT be restarted.
                System.out.println("after build " + b.getId() + ": myTrigger1 starts(): " + testT.getPhaser().getPhase());
                assertTrue(testT.getPhaser().getPhase() == 1);
                // it should have been stopped!
                assertFalse(testT.isStarted);
            }
            if (name.equals("myTrigger2")) {
                // myTrigger2 is being added. it will be restarted.
                System.out.println("after build " + b.getId() + ": myTrigger2 starts(): " + testT.getPhaser().getPhase());
                assertTrue(testT.getPhaser().getPhase() == 1);
                assertTrue(testT.isStarted);
            }
        }

        // Let simulate someone adding a new trigger
        triggersJobProperty = job.getProperty(PipelineTriggersJobProperty.class);
        List triggers = triggersJobProperty.getTriggers();
        TestTrigger myOtherTrigger = new TestTrigger();
        myOtherTrigger.setName("myOtherTrigger");
        triggers.add(myOtherTrigger);
        job.removeProperty(triggersJobProperty);
        job.addProperty(new PipelineTriggersJobProperty(triggers));

        // Build it again with a new trigger config
        b = j.buildAndAssertSuccess(job);
        triggersJobProperty = job.getProperty(PipelineTriggersJobProperty.class);
        triggerList = triggersJobProperty.getTriggers();
        for (Trigger t: triggerList) {
            TestTrigger testT = (TestTrigger)t;
            String name = testT.name;
            if (name.equals("myOtherTrigger")) {
                // myOtherTrigger has been added. it will get restarted
                System.out.println("after build " + b.getId() + ": myOtherTrigger starts(): " + testT.getPhaser().getPhase());
                // should be 1
                assertTrue(testT.getPhaser().getPhase() == 1);
                assertTrue(testT.isStarted);
            }
            if (name.equals("myTrigger2")) {
                // myTrigger2 is being added. it will be restarted.
                System.out.println("after build " + b.getId() + ": myTrigger2 starts(): " + testT.getPhaser().getPhase());
                assertTrue(testT.getPhaser().getPhase() == 1);
                assertTrue(testT.isStarted);
            }
        }
    }

    @TestExtension("doNotRestartEqualTriggers")
    public static class TestTrigger extends Trigger {

        protected String name;
        protected boolean isStarted = false;
        private transient Phaser phaser;

        public String getName() {
            return name;
        }

        public Phaser getPhaser() {
            return phaser;
        }

        @DataBoundSetter
        public void setName(String name) {
            this.name = name;
        }

        @DataBoundConstructor
        public TestTrigger() {
            phaser = new Phaser(1);
        }

        @Override
        public void start(Item project, boolean newInstance) {
            super.start(project, newInstance);
            System.out.println("Calling START() for " + name);
            phaser.arrive();
            synchronized (this) {
                isStarted = true;
            }
        }

        public boolean isStarted() {
            return isStarted;
        }

        @Override
        public void stop() {
            super.stop();
            System.out.println("Calling STOP() for " + name);
            synchronized (this) {
                isStarted = false;
            }
        }

        @Override
        public TriggerDescriptor getDescriptor() {
            return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
        }

        @Extension
        @Symbol("testtrigger")
        public static final class DescriptorImpl extends TriggerDescriptor {
            @Override
            public boolean isApplicable(Item item) {
                return true;
            }
        }
    }

}