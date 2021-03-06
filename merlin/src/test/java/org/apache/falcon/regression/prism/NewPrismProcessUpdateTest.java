/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.falcon.regression.prism;


import org.apache.falcon.regression.Entities.ProcessMerlin;
import org.apache.falcon.regression.core.bundle.Bundle;
import org.apache.falcon.regression.core.generated.dependencies.Frequency;
import org.apache.falcon.regression.core.generated.dependencies.Frequency.TimeUnit;
import org.apache.falcon.regression.core.generated.feed.ClusterType;
import org.apache.falcon.regression.core.generated.process.ExecutionType;
import org.apache.falcon.regression.core.generated.process.Process;
import org.apache.falcon.regression.core.helpers.ColoHelper;
import org.apache.falcon.regression.core.helpers.PrismHelper;
import org.apache.falcon.regression.core.response.APIResult;
import org.apache.falcon.regression.core.response.ServiceResponse;
import org.apache.falcon.regression.core.enumsAndConstants.ENTITY_TYPE;
import org.apache.falcon.regression.core.supportClasses.HadoopFileEditor;
import org.apache.falcon.regression.core.util.AssertUtil;
import org.apache.falcon.regression.core.util.BundleUtil;
import org.apache.falcon.regression.core.util.HadoopUtil;
import org.apache.falcon.regression.core.util.InstanceUtil;
import org.apache.falcon.regression.core.util.OSUtil;
import org.apache.falcon.regression.core.util.OozieUtil;
import org.apache.falcon.regression.core.util.TimeUtil;
import org.apache.falcon.regression.core.util.Util;
import org.apache.falcon.regression.core.util.Util.URLS;
import org.apache.falcon.regression.core.util.XmlUtil;
import org.apache.falcon.regression.testHelper.BaseTestClass;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.security.authentication.client.AuthenticationException;
import org.apache.log4j.Logger;
import org.apache.oozie.client.BundleJob;
import org.apache.oozie.client.CoordinatorAction;
import org.apache.oozie.client.CoordinatorJob;
import org.apache.oozie.client.Job;
import org.apache.oozie.client.OozieClient;
import org.apache.oozie.client.OozieClientException;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Minutes;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;
import java.util.Random;

@Test(groups = "distributed")
public class NewPrismProcessUpdateTest extends BaseTestClass {

    String baseTestDir = baseHDFSDir + "/NewPrismProcessUpdateTest";
    String inputFeedPath = baseTestDir + "/${YEAR}/${MONTH}/${DAY}/${HOUR}/${MINUTE}";
    String WORKFLOW_PATH = baseTestDir + "/falcon-oozie-wf";
    String WORKFLOW_PATH2 = baseTestDir + "/falcon-oozie-wf2";
    String aggregatorPath = baseTestDir + "/aggregator";
    String aggregator1Path = baseTestDir + "/aggregator1";
    ColoHelper cluster1 = servers.get(0);
    ColoHelper cluster2 = servers.get(1);
    ColoHelper cluster3 = servers.get(2);
    FileSystem cluster1FS = serverFS.get(0);
    OozieClient cluster2OC = serverOC.get(1);
    OozieClient cluster3OC = serverOC.get(2);
    private static final Logger logger = Logger.getLogger(NewPrismProcessUpdateTest.class);

    @BeforeMethod(alwaysRun = true)
    public void testSetup(Method method) throws Exception {
        logger.info("test name: " + method.getName());
        Bundle b = (Bundle) Bundle.readBundle("updateBundle")[0][0];
        b.generateUniqueBundle();
        bundles[0] = new Bundle(b, cluster1);
        bundles[1] = new Bundle(b, cluster2);
        bundles[2] = new Bundle(b, cluster3);
        setBundleWFPath(bundles[0], bundles[1], bundles[2]);
        bundles[1].addClusterToBundle(bundles[2].getClusters().get(0),
            ClusterType.TARGET, null, null);
        usualGrind(cluster3, bundles[1]);
    }

    @BeforeClass(alwaysRun = true)
    public void setup() throws Exception {
        for (String wfPath : new String[]{WORKFLOW_PATH, WORKFLOW_PATH2, aggregatorPath,
            aggregator1Path}) {
            uploadDirToClusters(wfPath, OSUtil.RESOURCES_OOZIE);
        }
        Util.restartService(cluster3.getClusterHelper());

    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        removeBundles();
    }

    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessFrequencyInEachColoWithOneProcessRunning_Monthly()
        throws Exception {
        final String START_TIME = TimeUtil.getTimeWrtSystemTime(-20);
        String endTime = TimeUtil.getTimeWrtSystemTime(4000 * 60);
        bundles[1].setProcessPeriodicity(1, TimeUnit.months);
        bundles[1].setOutputFeedPeriodicity(1, TimeUnit.months);
        bundles[1].setProcessValidity(START_TIME, endTime);

        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);

        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);
        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);

        List<String> oldNominalTimes = OozieUtil.getActionsNominalTime(cluster3, oldBundleId,
            ENTITY_TYPE.PROCESS);


        String updatedProcess = InstanceUtil
            .setProcessFrequency(bundles[1].getProcessData(),
                new Frequency(5, TimeUnit.minutes));

        logger.info("updated process: " + updatedProcess);

        //now to update
        while (Util
            .parseResponse(prism.getProcessHelper()
                .update((bundles[1].getProcessData()), updatedProcess))
            .getStatus() != APIResult.Status.SUCCEEDED) {
            logger.info("update didnt SUCCEED in last attempt");
            Thread.sleep(10000);
        }

        String prismString = getResponse(prism, bundles[1], true);
        Assert.assertEquals(Util.getProcessObject(prismString).getFrequency(),
            Util.getProcessObject(updatedProcess).getFrequency());
        Thread.sleep(60000);
        dualComparison(bundles[1], cluster3);
        //ensure that the running process has new coordinators created; while the submitted
        // one is updated correctly.
        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(), true, false);
        waitingForBundleFinish(cluster3, oldBundleId, 5);
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 1, 10);
        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            Util.readEntityName(bundles[1].getProcessData()), true, true);

    }

    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessRollStartTimeForwardInEachColoWithOneProcessRunning()
        throws Exception {
        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);

        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);

        List<String> oldNominalTimes =
            OozieUtil.getActionsNominalTime(cluster3, oldBundleId, ENTITY_TYPE.PROCESS);

        String newStartTime = TimeUtil.addMinsToTime(TimeUtil.dateToOozieDate(
            bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                .getStart()
        ), 20);
        String newEndTime = TimeUtil.addMinsToTime(TimeUtil.dateToOozieDate(
            bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                .getStart()
        ), 30);

        bundles[1].setProcessValidity(newStartTime, newEndTime);

        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);

        logger.info("updated process: " + bundles[1].getProcessData());
        while (Util.parseResponse(
            prism.getProcessHelper()
                .update(bundles[1].getProcessData(), bundles[1].getProcessData()))
            .getStatus() != APIResult.Status.SUCCEEDED) {
            logger.info("update didnt SUCCEED in last attempt");
            Thread.sleep(10000);
        }

        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(), true, false);

        dualComparison(bundles[1], cluster3);
        while (!OozieUtil.isBundleOver(cluster3, oldBundleId)) {
            Thread.sleep(20000);
        }
        //ensure that the running process has new coordinators created; while the submitted
        // one is updated correctly.
        int finalNumberOfInstances =
            InstanceUtil.getProcessInstanceListFromAllBundles(cluster3,
                Util.getProcessName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS).size();
        Assert.assertEquals(finalNumberOfInstances,
            getExpectedNumberOfWorkflowInstances(TimeUtil
                    .dateToOozieDate(
                        bundles[1].getProcessObject().getClusters().getCluster().get(0)
                            .getValidity().getStart()
                    ),
                TimeUtil
                    .dateToOozieDate(
                        bundles[1].getProcessObject().getClusters().getCluster()
                            .get(0).getValidity()
                            .getEnd()
                    )));
        AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);
        int expectedNumberOfWorkflows =
            getExpectedNumberOfWorkflowInstances(newStartTime, TimeUtil
                .dateToOozieDate(
                    bundles[1].getProcessObject().getClusters().getCluster().get(0)
                        .getValidity().getEnd()
                ));
        Assert.assertEquals(OozieUtil.getNumberOfWorkflowInstances(cluster3, oldBundleId),
            expectedNumberOfWorkflows);
    }

    @Test(groups = {"multiCluster"}, timeOut = 1800000)
    public void updateProcessConcurrencyWorkflowExecutionInEachColoWithOneColoDown()
        throws Exception {
        try {
            //bundles[1].generateUniqueBundle();
            bundles[1].submitBundle(prism);
            //now to schedule in 1 colo and let it remain in another
            AssertUtil.assertSucceeded(
                cluster3.getProcessHelper()
                    .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
            String oldBundleId = InstanceUtil
                .getLatestBundleID(cluster3,
                    Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);
            Thread.sleep(25000);

            int initialConcurrency = bundles[1].getProcessObject().getParallel();

            bundles[1].setProcessConcurrency(bundles[1].getProcessObject().getParallel() + 3);
            bundles[1].setProcessWorkflow(WORKFLOW_PATH2);
            bundles[1].getProcessObject().setOrder(getRandomExecutionType(bundles[1]));
            //suspend
            Util.shutDownService(cluster3.getProcessHelper());
            AssertUtil.assertPartial(
                prism.getProcessHelper()
                    .update(bundles[1].getProcessData(), bundles[1].getProcessData()));
            //now to update

            String prismString = getResponse(prism, bundles[1], true);
            Assert.assertEquals(Util.getProcessObject(prismString).getParallel(),
                initialConcurrency);
            Assert.assertEquals(Util.getProcessObject(prismString).getWorkflow().getPath(),
                WORKFLOW_PATH);
            Assert.assertEquals(Util.getProcessObject(prismString).getOrder(),
                bundles[1].getProcessObject().getOrder());

            String coloString = getResponse(cluster2, bundles[1], true);
            Assert.assertEquals(Util.getProcessObject(coloString).getWorkflow().getPath(),
                WORKFLOW_PATH2);

            Util.startService(cluster3.getProcessHelper());

            dualComparisonFailure(bundles[1], cluster3);
            //ensure that the running process has new coordinators created; while the submitted
            // one is updated correctly.
            AssertUtil
                .checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);

            waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);
            while (Util.parseResponse(
                prism.getProcessHelper()
                    .update(bundles[1].getProcessData(), bundles[1].getProcessData()))
                .getStatus() != APIResult.Status.SUCCEEDED) {
                logger.info("WARNING: update did not scceed, retyring ");
                Thread.sleep(20000);
            }
            prismString = getResponse(prism, bundles[1], true);
            Assert.assertEquals(Util.getProcessObject(prismString).getParallel(),
                initialConcurrency + 3);
            Assert.assertEquals(Util.getProcessObject(prismString).getWorkflow().getPath(),
                WORKFLOW_PATH2);
            Assert.assertEquals(Util.getProcessObject(prismString).getOrder(),
                bundles[1].getProcessObject().getOrder());
            dualComparison(bundles[1], cluster3);
            AssertUtil
                .checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);
            waitingForBundleFinish(cluster3, oldBundleId);
            int finalNumberOfInstances =
                InstanceUtil.getProcessInstanceListFromAllBundles(cluster3,
                    Util.getProcessName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS).size();

            int expectedInstances =
                getExpectedNumberOfWorkflowInstances(TimeUtil.dateToOozieDate(
                        bundles[1].getProcessObject().getClusters().getCluster().get(0)
                            .getValidity().getStart()
                    ),
                    TimeUtil
                        .dateToOozieDate(
                            bundles[1].getProcessObject().getClusters().getCluster()
                                .get(0).getValidity()
                                .getEnd()
                        ));
            Assert.assertEquals(finalNumberOfInstances, expectedInstances,
                "number of instances doesnt match :(");
        } finally {
            Util.restartService(cluster3.getClusterHelper());
        }
    }


    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessFrequencyInEachColoWithOneProcessRunning() throws Exception {
        String startTime = TimeUtil.getTimeWrtSystemTime(-2);
        String endTime = TimeUtil.getTimeWrtSystemTime(20);
        bundles[1].setProcessValidity(startTime, endTime);
        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);

        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);
        List<String> oldNominalTimes = OozieUtil.getActionsNominalTime(cluster3, oldBundleId,
            ENTITY_TYPE.PROCESS);
        logger.info("original process: " + bundles[1].getProcessData());

        String updatedProcess = InstanceUtil
            .setProcessFrequency(bundles[1].getProcessData(),
                new Frequency(7, TimeUnit.minutes));

        logger.info("updated process: " + updatedProcess);

        //now to update

        ServiceResponse response =
            prism.getProcessHelper().update(updatedProcess, updatedProcess);
        AssertUtil.assertSucceeded(response);
        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(), true, false);
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 1, 10);

        String prismString = getResponse(prism, bundles[1], true);
        Assert.assertEquals(Util.getProcessObject(prismString).getFrequency(),
            Util.getProcessObject(updatedProcess).getFrequency());
        dualComparison(bundles[1], cluster3);
        waitingForBundleFinish(cluster3, oldBundleId);

        AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);
    }


    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessNameInEachColoWithOneProcessRunning() throws Exception {
        //bundles[1].generateUniqueBundle();
        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        String originalProcessData = bundles[1].getProcessData();
        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);


        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);
        Thread.sleep(20000);
        List<String> oldNominalTimes =
            OozieUtil.getActionsNominalTime(cluster3, oldBundleId, ENTITY_TYPE.PROCESS);
        bundles[1].setProcessName("myNewProcessName");

        //now to update
        ServiceResponse response =
            prism.getProcessHelper()
                .update((bundles[1].getProcessData()), bundles[1].getProcessData());
        AssertUtil.assertFailed(response);
        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            Util.readEntityName(originalProcessData), false, false);
        AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);
    }

    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessConcurrencyInEachColoWithOneProcessRunning()
        throws Exception {
        String startTime = TimeUtil.getTimeWrtSystemTime(-2);
        String endTime = TimeUtil.getTimeWrtSystemTime(10);
        bundles[1].setProcessValidity(startTime, endTime);

        //bundles[1].generateUniqueBundle();
        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));

        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);


        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);

        //now to update
        DateTime updateTime = new DateTime(DateTimeZone.UTC);
        Thread.sleep(60000);
        List<String> oldNominalTimes =
            OozieUtil.getActionsNominalTime(cluster3, oldBundleId, ENTITY_TYPE.PROCESS);
        logger.info("updating at " + updateTime);
        while (Util
            .parseResponse(updateProcessConcurrency(bundles[1],
                bundles[1].getProcessObject().getParallel() + 3))
            .getStatus() != APIResult.Status.SUCCEEDED) {
            logger.info("WARNING: update did not scceed, retyring ");
            Thread.sleep(20000);
        }

        String prismString = getResponse(prism, bundles[1], true);
        Assert.assertEquals(Util.getProcessObject(prismString).getParallel(),
            bundles[1].getProcessObject().getParallel() + 3);
        dualComparison(bundles[1], cluster3);
        //ensure that the running process has new coordinators created; while the submitted
        // one is updated
        // correctly.
        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(),
            false, true);
        AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);

        // future : should be verified using cord xml
        Job.Status status = OozieUtil.getOozieJobStatus(cluster3.getFeedHelper().getOozieClient(),
            Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);

        boolean doesExist = false;
        while (status != Job.Status.SUCCEEDED && status != Job.Status.FAILED &&
            status != Job.Status.DONEWITHERROR) {
            int statusCount = InstanceUtil
                .getInstanceCountWithStatus(cluster3,
                    Util.readEntityName(bundles[1].getProcessData()),
                    org.apache.oozie.client.CoordinatorAction.Status.RUNNING,
                    ENTITY_TYPE.PROCESS);
            if (statusCount == bundles[1].getProcessObject().getParallel() + 3) {
                doesExist = true;
                break;
            }
            status = OozieUtil.getOozieJobStatus(cluster3.getFeedHelper().getOozieClient(),
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);
            Assert.assertNotNull(status,
                "status must not be null!");
            Thread.sleep(30000);
        }

        Assert.assertTrue(doesExist, "Er! The desired concurrency levels are never reached!!!");
        int expectedNumberOfInstances =
            getExpectedNumberOfWorkflowInstances(TimeUtil.dateToOozieDate(
                    bundles[1].getProcessObject().getClusters().getCluster().get(0)
                        .getValidity().getStart()
                ),
                TimeUtil
                    .dateToOozieDate(
                        bundles[1].getProcessObject().getClusters().getCluster()
                            .get(0).getValidity()
                            .getEnd()
                    ));
        Assert.assertEquals(OozieUtil.getNumberOfWorkflowInstances(cluster3, oldBundleId),
            expectedNumberOfInstances);
    }


    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessIncreaseValidityInEachColoWithOneProcessRunning() throws Exception {
        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);

        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);

        List<String> oldNominalTimes = OozieUtil.getActionsNominalTime(cluster3, oldBundleId,
            ENTITY_TYPE.PROCESS);


        String newEndTime = TimeUtil.addMinsToTime(TimeUtil.dateToOozieDate(
            bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                .getEnd()
        ), 4);
        bundles[1].setProcessValidity(TimeUtil.dateToOozieDate(
                bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                    .getStart()
            ),
            newEndTime);

        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);

        ServiceResponse response = prism.getProcessHelper()
            .update(bundles[1].getProcessData(), bundles[1].getProcessData());
        for (int i = 0; i < 10 &&
            Util.parseResponse(response).getStatus() != APIResult.Status.SUCCEEDED; ++i) {
            response = prism.getProcessHelper()
                .update(bundles[1].getProcessData(), bundles[1].getProcessData());
            java.util.concurrent.TimeUnit.SECONDS.sleep(6);
        }
        Assert.assertEquals(Util.parseResponse(response).getStatus(),
            APIResult.Status.SUCCEEDED, "Process update did not succeed.");

        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(), false, true);

        int i = 0;

        while (OozieUtil.getNumberOfWorkflowInstances(cluster3, oldBundleId)
            != getExpectedNumberOfWorkflowInstances(TimeUtil.dateToOozieDate(
                bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                    .getStart()
            ),
            TimeUtil
                .dateToOozieDate(
                    bundles[1].getProcessObject().getClusters().getCluster().get(0)
                        .getValidity()
                        .getEnd()
                ))
            && i < 10) {
            Thread.sleep(1000);
            i++;
        }


        bundles[1].verifyDependencyListing();

        dualComparison(bundles[1], cluster3);
        waitingForBundleFinish(cluster3, oldBundleId);
        //ensure that the running process has new coordinators created; while the submitted
        // one is updated
        // correctly.
        int finalNumberOfInstances = InstanceUtil
            .getProcessInstanceList(cluster3,
                Util.getProcessName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS)
            .size();
        Assert.assertEquals(finalNumberOfInstances,
            getExpectedNumberOfWorkflowInstances(TimeUtil
                    .dateToOozieDate(
                        bundles[1].getProcessObject().getClusters().getCluster().get(0)
                            .getValidity().getStart()
                    ),
                TimeUtil
                    .dateToOozieDate(
                        bundles[1].getProcessObject().getClusters().getCluster()
                            .get(0).getValidity()
                            .getEnd()
                    )));
        AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);
    }

    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessConcurrencyInEachColoWithOneProcessSuspended()
        throws Exception {
        String startTime = TimeUtil.getTimeWrtSystemTime(-2);
        String endTime = TimeUtil.getTimeWrtSystemTime(7);
        bundles[1].setProcessValidity(startTime, endTime);

        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);

        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);
        List<String> oldNominalTimes = OozieUtil.getActionsNominalTime(cluster3, oldBundleId,
            ENTITY_TYPE.PROCESS);


        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .suspend(URLS.SUSPEND_URL, bundles[1].getProcessData()));
        //now to update
        while (Util
            .parseResponse(updateProcessConcurrency(bundles[1],
                bundles[1].getProcessObject().getParallel() + 3))
            .getStatus() != APIResult.Status.SUCCEEDED) {
            logger.info("WARNING: update did not scceed, retyring ");
            Thread.sleep(20000);
        }

        String prismString = getResponse(prism, bundles[1], true);
        Assert.assertEquals(Util.getProcessObject(prismString).getParallel(),
            bundles[1].getProcessObject().getParallel() + 3);
        dualComparison(bundles[1], cluster3);
        //ensure that the running process has new coordinators created; while the submitted
        // one is updated correctly.
        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(), false, true);
        AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);
        AssertUtil.assertSucceeded(cluster3.getProcessHelper()
            .resume(URLS.RESUME_URL, bundles[1].getProcessData()));
        AssertUtil.checkStatus(cluster3OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);

        Job.Status status = OozieUtil.getOozieJobStatus(cluster3.getFeedHelper().getOozieClient(),
            Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);

        boolean doesExist = false;
        while (status != Job.Status.SUCCEEDED && status != Job.Status.FAILED &&
            status != Job.Status.DONEWITHERROR) {
            if (InstanceUtil
                .getInstanceCountWithStatus(cluster3,
                    Util.readEntityName(bundles[1].getProcessData()),
                    org.apache.oozie.client.CoordinatorAction.Status.RUNNING,
                    ENTITY_TYPE.PROCESS) ==
                bundles[1].getProcessObject().getParallel()) {
                doesExist = true;
                break;
            }
            status = OozieUtil.getOozieJobStatus(cluster3.getFeedHelper().getOozieClient(),
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);
        }

        Assert.assertTrue(doesExist, "Er! The desired concurrency levels are never reached!!!");

        waitingForBundleFinish(cluster3, oldBundleId);

        int finalNumberOfInstances =
            InstanceUtil.getProcessInstanceListFromAllBundles(cluster3,
                Util.getProcessName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS).size();

        int expectedInstances =
            getExpectedNumberOfWorkflowInstances(TimeUtil.dateToOozieDate(
                    bundles[1].getProcessObject().getClusters().getCluster().get(0)
                        .getValidity().getStart()
                ),
                TimeUtil
                    .dateToOozieDate(
                        bundles[1].getProcessObject().getClusters().getCluster()
                            .get(0).getValidity()
                            .getEnd()
                    ));

        Assert.assertEquals(finalNumberOfInstances, expectedInstances,
            "number of instances doesnt match :(");
    }

    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessConcurrencyInEachColoWithOneColoDown() throws Exception {
        try {
            String startTime = TimeUtil.getTimeWrtSystemTime(-1);
            String endTime = TimeUtil.getTimeWrtSystemTime(5);
            bundles[1].setProcessValidity(startTime, endTime);

            bundles[1].submitBundle(prism);
            //now to schedule in 1 colo and let it remain in another

            logger.info("process to be scheduled: " + bundles[1].getProcessData());

            AssertUtil.assertSucceeded(
                cluster3.getProcessHelper()
                    .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
            InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);

            String oldBundleId = InstanceUtil
                .getLatestBundleID(cluster3,
                    Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);

            List<String> oldNominalTimes = OozieUtil.getActionsNominalTime(cluster3, oldBundleId,
                ENTITY_TYPE.PROCESS);


            //now to update
            Util.shutDownService(cluster3.getClusterHelper());

            ServiceResponse response =
                updateProcessConcurrency(bundles[1],
                    bundles[1].getProcessObject().getParallel() + 3);
            AssertUtil.assertPartial(response);

            Util.startService(cluster3.getClusterHelper());

            String prismString = getResponse(prism, bundles[1], true);
            dualComparisonFailure(bundles[1], cluster3);
            Assert.assertEquals(Util.getProcessObject(prismString).getParallel(),
                bundles[1].getProcessObject().getParallel());

            //ensure that the running process has new coordinators created; while the submitted
            // one is updated correctly.
            AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1],
                Job.Status.RUNNING);

            while (Util
                .parseResponse(updateProcessConcurrency(bundles[1],
                    bundles[1].getProcessObject().getParallel() + 3))
                .getStatus() != APIResult.Status.SUCCEEDED) {
                logger.info("WARNING: update did not scceed, retyring ");
                Thread.sleep(20000);
            }
            dualComparison(bundles[1], cluster3);
            dualComparison(bundles[1], cluster2);

            Job.Status status =
                OozieUtil.getOozieJobStatus(cluster3.getFeedHelper().getOozieClient(),
                    Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);

            boolean doesExist = false;
            while (status != Job.Status.SUCCEEDED && status != Job.Status.FAILED &&
                status != Job.Status.DONEWITHERROR) {
                if (InstanceUtil
                    .getInstanceCountWithStatus(cluster3,
                        Util.readEntityName(bundles[1].getProcessData()),
                        org.apache.oozie.client.CoordinatorAction.Status.RUNNING,
                        ENTITY_TYPE.PROCESS) ==
                    bundles[1].getProcessObject().getParallel() + 3) {
                    doesExist = true;
                    break;
                }
                status = OozieUtil.getOozieJobStatus(cluster3.getFeedHelper().getOozieClient(),
                    Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);
                Thread.sleep(30000);
            }
            Assert.assertTrue(doesExist, "Er! The desired concurrency levels are never reached!!!");
            OozieUtil.verifyNewBundleCreation(cluster3, InstanceUtil
                    .getLatestBundleID(cluster3,
                        Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS),
                oldNominalTimes, Util.readEntityName(bundles[1].getProcessData()), false,
                true
            );

            waitingForBundleFinish(cluster3, oldBundleId);

            int finalNumberOfInstances =
                InstanceUtil.getProcessInstanceListFromAllBundles(cluster3,
                    Util.getProcessName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS).size();

            int expectedInstances =
                getExpectedNumberOfWorkflowInstances(TimeUtil.dateToOozieDate(
                        bundles[1].getProcessObject().getClusters().getCluster().get(0)
                            .getValidity().getStart()
                    ),
                    TimeUtil
                        .dateToOozieDate(
                            bundles[1].getProcessObject().getClusters().getCluster()
                                .get(0).getValidity()
                                .getEnd()
                        ));
            Assert.assertEquals(finalNumberOfInstances, expectedInstances,
                "number of instances doesnt match :(");
        } finally {
            Util.restartService(cluster3.getProcessHelper());
        }
    }

    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessConcurrencyExecutionWorkflowInEachColoWithOneProcessRunning()
        throws Exception {
        String startTime = TimeUtil.getTimeWrtSystemTime(-2);
        String endTime = TimeUtil.getTimeWrtSystemTime(6);
        bundles[1].setProcessValidity(startTime, endTime);

        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);

        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);
        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);
        List<String> oldNominalTimes = OozieUtil.getActionsNominalTime(cluster3, oldBundleId,
            ENTITY_TYPE.PROCESS);


        int initialConcurrency = bundles[1].getProcessObject().getParallel();

        bundles[1].setProcessConcurrency(bundles[1].getProcessObject().getParallel() + 3);
        bundles[1].setProcessWorkflow(aggregator1Path);
        bundles[1].getProcessObject().setOrder(getRandomExecutionType(bundles[1]));

        //now to update

        String updateTime = new DateTime(DateTimeZone.UTC).plusMinutes(2).toString();

        logger.info("updating @ " + updateTime);

        while (Util.parseResponse(
            prism.getProcessHelper().update((bundles[1].getProcessData()), bundles[1]
                .getProcessData())).getStatus() != APIResult.Status.SUCCEEDED) {
            Thread.sleep(10000);
        }
        String prismString = getResponse(prism, bundles[1], true);
        Assert.assertEquals(Util.getProcessObject(prismString).getParallel(),
            initialConcurrency + 3);
        Assert.assertEquals(Util.getProcessObject(prismString).getWorkflow().getPath(),
            aggregator1Path);
        Assert.assertEquals(Util.getProcessObject(prismString).getOrder(),
            bundles[1].getProcessObject().getOrder());
        dualComparison(bundles[1], cluster3);
        //ensure that the running process has new coordinators created; while the submitted
        // one is updated correctly.
        waitingForBundleFinish(cluster3, oldBundleId);
        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(), true, true);
        AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);
        int finalNumberOfInstances =
            InstanceUtil.getProcessInstanceListFromAllBundles(cluster3,
                Util.getProcessName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS).size();
        int expectedInstances =
            getExpectedNumberOfWorkflowInstances(TimeUtil.dateToOozieDate(
                    bundles[1].getProcessObject().getClusters().getCluster().get(0)
                        .getValidity().getStart()
                ),
                TimeUtil
                    .dateToOozieDate(
                        bundles[1].getProcessObject().getClusters().getCluster()
                            .get(0).getValidity()
                            .getEnd()
                    ));

        Assert.assertEquals(finalNumberOfInstances, expectedInstances,
            "number of instances doesnt match :(");
    }

    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessConcurrencyExecutionWorkflowInEachColoWithOneProcessSuspended()
        throws Exception {
        String startTime = TimeUtil.getTimeWrtSystemTime(2);
        String endTime = TimeUtil.getTimeWrtSystemTime(6);
        bundles[1].setProcessValidity(startTime, endTime);

        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);

        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);

        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);

        List<String> oldNominalTimes = OozieUtil.getActionsNominalTime(cluster3, oldBundleId,
            ENTITY_TYPE.PROCESS);

        int initialConcurrency = bundles[1].getProcessObject().getParallel();

        bundles[1].setProcessConcurrency(bundles[1].getProcessObject().getParallel() + 3);
        bundles[1].setProcessWorkflow(aggregator1Path);
        bundles[1].getProcessObject().setOrder(getRandomExecutionType(bundles[1]));
        //suspend
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .suspend(URLS.SUSPEND_URL, bundles[1].getProcessData()));

        //now to update
        String updateTime = new DateTime(DateTimeZone.UTC).plusMinutes(2).toString();
        logger.info("updating @ " + updateTime);
        while (Util.parseResponse(
            prism.getProcessHelper()
                .update((bundles[1].getProcessData()), bundles[1].getProcessData()))
            .getStatus() != APIResult.Status.SUCCEEDED) {
            Thread.sleep(10000);
        }

        AssertUtil.assertSucceeded(cluster3.getProcessHelper()
            .resume(URLS.RESUME_URL, bundles[1].getProcessData()));

        String prismString = getResponse(prism, bundles[1], true);
        Assert.assertEquals(Util.getProcessObject(prismString).getParallel(),
            initialConcurrency + 3);
        Assert.assertEquals(Util.getProcessObject(prismString).getWorkflow().getPath(),
            aggregator1Path);
        Assert.assertEquals(Util.getProcessObject(prismString).getOrder(),
            bundles[1].getProcessObject().getOrder());
        dualComparison(bundles[1], cluster3);
        //ensure that the running process has new coordinators created; while the submitted
        // one is updated correctly.
        waitingForBundleFinish(cluster3, oldBundleId);
        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(), true, true);
        AssertUtil.checkNotStatus(cluster3OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);
        int finalNumberOfInstances =
            InstanceUtil.getProcessInstanceListFromAllBundles(cluster3,
                Util.getProcessName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS).size();

        int expectedInstances =
            getExpectedNumberOfWorkflowInstances(TimeUtil.dateToOozieDate(
                    bundles[1].getProcessObject().getClusters().getCluster().get(0)
                        .getValidity().getStart()
                ),
                TimeUtil
                    .dateToOozieDate(
                        bundles[1].getProcessObject().getClusters().getCluster()
                            .get(0).getValidity()
                            .getEnd()
                    ));

        Assert.assertEquals(finalNumberOfInstances, expectedInstances,
            "number of instances doesnt match :(");
    }

    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessAddNewInputInEachColoWithOneProcessRunning() throws Exception {
        String startTime = TimeUtil.getTimeWrtSystemTime(-2);
        String endTime = TimeUtil.getTimeWrtSystemTime(6);
        bundles[1].setProcessValidity(startTime, endTime);

        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);

        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);

        Thread.sleep(20000);
        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);
        List<String> oldNominalTimes = OozieUtil.getActionsNominalTime(cluster3, oldBundleId,
            ENTITY_TYPE.PROCESS);


        String newFeedName = BundleUtil.getInputFeedNameFromBundle(bundles[1]) + "2";
        String inputFeed = BundleUtil.getInputFeedFromBundle(bundles[1]);

        bundles[1].addProcessInput(newFeedName, "inputData2");
        inputFeed = Util.setFeedName(inputFeed, newFeedName);

        logger.info(inputFeed);
        AssertUtil.assertSucceeded(
            prism.getFeedHelper().submitEntity(URLS.SUBMIT_URL, inputFeed));

        while (Util.parseResponse(
            prism.getProcessHelper()
                .update((bundles[1].getProcessData()), bundles[1].getProcessData()))
            .getStatus() != APIResult.Status.SUCCEEDED) {
            Thread.sleep(20000);
        }
        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(), true, false);
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 1, 10);


        bundles[1].verifyDependencyListing();

        dualComparison(bundles[1], cluster3);
        //ensure that the running process has new coordinators created; while the submitted
        // one is updated correctly.
        waitingForBundleFinish(cluster3, oldBundleId);
        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(), true, true);
        AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);
    }

    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessAddNewInputInEachColoWithOneProcessSuspended() throws Exception {
        String startTime = TimeUtil.getTimeWrtSystemTime(1);
        String endTime = TimeUtil.getTimeWrtSystemTime(6);
        bundles[1].setProcessValidity(startTime, endTime);

        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);

        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);

        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);
        List<String> oldNominalTimes = OozieUtil.getActionsNominalTime(cluster3, oldBundleId,
            ENTITY_TYPE.PROCESS);


        String newFeedName = BundleUtil.getInputFeedNameFromBundle(bundles[1]) + "2";
        String inputFeed = BundleUtil.getInputFeedFromBundle(bundles[1]);

        bundles[1].addProcessInput(newFeedName, "inputData2");
        inputFeed = Util.setFeedName(inputFeed, newFeedName);

        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .suspend(URLS.SUSPEND_URL, bundles[1].getProcessData()));
        AssertUtil.assertSucceeded(
            prism.getFeedHelper().submitEntity(URLS.SUBMIT_URL, inputFeed));

        while (Util.parseResponse(
            prism.getProcessHelper()
                .update((bundles[1].getProcessData()), bundles[1].getProcessData()))
            .getStatus() != APIResult.Status.SUCCEEDED) {
            Thread.sleep(10000);
        }

        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(), true, false);
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 1, 10);

        AssertUtil.assertSucceeded(cluster3.getProcessHelper()
            .resume(URLS.RESUME_URL, bundles[1].getProcessData()));

        bundles[1].verifyDependencyListing();

        dualComparison(bundles[1], cluster3);
        //ensure that the running process has new coordinators created; while the submitted
        // one is updated correctly.
        waitingForBundleFinish(cluster3, oldBundleId);
        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(), true, true);
        AssertUtil.checkNotStatus(cluster3OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);

    }

    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessAddNewInputInEachColoWithOneColoDown() throws Exception {
        try {
            String startTime = TimeUtil.getTimeWrtSystemTime(-2);
            String endTime = TimeUtil.getTimeWrtSystemTime(10);
            bundles[1].setProcessValidity(startTime, endTime);

            bundles[1].submitBundle(prism);
            String originalProcess = bundles[1].getProcessData();
            String newFeedName = BundleUtil.getInputFeedNameFromBundle(bundles[1]) + "2";
            String inputFeed = BundleUtil.getInputFeedFromBundle(bundles[1]);
            bundles[1].addProcessInput(newFeedName, "inputData2");
            inputFeed = Util.setFeedName(inputFeed, newFeedName);
            String updatedProcess = bundles[1].getProcessData();


            //now to schedule in 1 colo and let it remain in another
            AssertUtil.assertSucceeded(
                cluster3.getProcessHelper()
                    .schedule(URLS.SCHEDULE_URL, originalProcess));
            InstanceUtil.waitTillInstancesAreCreated(cluster3, originalProcess, 0, 10);

            String oldBundleId = InstanceUtil
                .getLatestBundleID(cluster3,
                    Util.readEntityName(originalProcess), ENTITY_TYPE.PROCESS);

            InstanceUtil.waitTillInstancesAreCreated(cluster3, originalProcess, 0, 10);
            List<String> oldNominalTimes = OozieUtil.getActionsNominalTime(cluster3, oldBundleId,
                ENTITY_TYPE.PROCESS);


            //submit new feed
            AssertUtil.assertSucceeded(
                prism.getFeedHelper().submitEntity(URLS.SUBMIT_URL, inputFeed));


            Util.shutDownService(cluster3.getProcessHelper());

            AssertUtil.assertPartial(
                prism.getProcessHelper()
                    .update(updatedProcess, updatedProcess));

            Util.startService(cluster3.getProcessHelper());
            bundles[1].verifyDependencyListing();

            dualComparison(bundles[1], cluster3);
            Assert.assertFalse(Util.isDefinitionSame(cluster2, prism, originalProcess));


            //ensure that the running process has new coordinators created; while the submitted
            // one is updated correctly.
            OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
                bundles[1].getProcessData(), false, false);
            AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1],
                Job.Status.RUNNING);

            waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);

            while (Util.parseResponse(
                prism.getProcessHelper()
                    .update(updatedProcess, updatedProcess))

                .getStatus() != APIResult.Status.SUCCEEDED) {
                logger.info("update didnt SUCCEED in last attempt");
                Thread.sleep(10000);
            }
            dualComparison(bundles[1], cluster3);
            Assert.assertTrue(Util.isDefinitionSame(cluster2, prism, originalProcess));
            bundles[1].verifyDependencyListing();
            OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
                updatedProcess, true, false);
            waitingForBundleFinish(cluster3, oldBundleId);


            InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 1, 10);

            OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
                bundles[1].getProcessData(), true, true);
            AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1],
                Job.Status.RUNNING);


        } finally {
            Util.restartService(cluster3.getProcessHelper());
        }
    }

    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessDecreaseValidityInEachColoWithOneProcessRunning() throws Exception {
        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);

        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);

        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);

        List<String> oldNominalTimes = OozieUtil.getActionsNominalTime(cluster3, oldBundleId,
            ENTITY_TYPE.PROCESS);

        String newEndTime = TimeUtil.addMinsToTime(TimeUtil.dateToOozieDate(
            bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                .getEnd()
        ), -2);
        bundles[1].setProcessValidity(TimeUtil.dateToOozieDate(
                bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                    .getStart()
            ),
            newEndTime);
        while (Util.parseResponse(
            (prism.getProcessHelper()
                .update(bundles[1].getProcessData(), bundles[1].getProcessData())))
            .getStatus() != APIResult.Status.SUCCEEDED) {
            logger.info("update didnt SUCCEED in last attempt");
            Thread.sleep(10000);
        }
        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(), false, true);


        bundles[1].verifyDependencyListing();

        dualComparison(bundles[1], cluster3);
        waitingForBundleFinish(cluster3, oldBundleId);
        //ensure that the running process has new coordinators created; while the submitted
        // one is updated correctly.
        int finalNumberOfInstances = InstanceUtil
            .getProcessInstanceList(cluster3,
                Util.getProcessName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS)
            .size();
        Assert.assertEquals(finalNumberOfInstances,
            getExpectedNumberOfWorkflowInstances(bundles[1]
                    .getProcessObject().getClusters().getCluster().get(0).getValidity()
                    .getStart(),
                bundles[1].getProcessObject().getClusters().getCluster().get(0)
                    .getValidity().getEnd()));
        AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);
        int expectedNumberOfWorkflows =
            getExpectedNumberOfWorkflowInstances(TimeUtil.dateToOozieDate(
                    bundles[1].getProcessObject().getClusters().getCluster().get(0)
                        .getValidity().getStart()
                ),
                newEndTime);
        Assert.assertEquals(OozieUtil.getNumberOfWorkflowInstances(cluster3, oldBundleId),
            expectedNumberOfWorkflows);
    }

    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessIncreaseValidityInEachColoWithOneProcessSuspended() throws Exception {
        String startTime = TimeUtil.getTimeWrtSystemTime(-1);
        String endTime = TimeUtil.getTimeWrtSystemTime(3);
        bundles[1].setProcessValidity(startTime, endTime);

        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);

        Thread.sleep(30000);
        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);

        String newEndTime = TimeUtil.addMinsToTime(TimeUtil.dateToOozieDate(
            bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                .getEnd()
        ), 4);
        bundles[1].setProcessValidity(TimeUtil.dateToOozieDate(
                bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                    .getStart()
            ),
            newEndTime);

        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .suspend(URLS.SUSPEND_URL, bundles[1].getProcessData()));
        while (Util.parseResponse(
            prism.getProcessHelper()
                .update((bundles[1].getProcessData()), bundles[1].getProcessData()))
            .getStatus() != APIResult.Status.SUCCEEDED) {
            logger.info("update didnt SUCCEED in last attempt");
            Thread.sleep(10000);
        }
        AssertUtil.assertSucceeded(cluster3.getProcessHelper()
            .resume(URLS.RESUME_URL, bundles[1].getProcessData()));

        dualComparison(bundles[1], cluster2);

        dualComparison(bundles[1], cluster3);
        waitingForBundleFinish(cluster3, oldBundleId);
        //ensure that the running process has new coordinators created; while the submitted
        // one is updated correctly.
        int finalNumberOfInstances = InstanceUtil
            .getProcessInstanceList(cluster3,
                Util.getProcessName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS)
            .size();
        Assert.assertEquals(finalNumberOfInstances,
            getExpectedNumberOfWorkflowInstances(bundles[1]
                    .getProcessObject().getClusters().getCluster().get(0).getValidity()
                    .getStart(),
                bundles[1].getProcessObject().getClusters().getCluster().get(0)
                    .getValidity().getEnd()));
        AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);
    }

    private void setBundleWFPath(Bundle... bundles) throws Exception {
        for (Bundle bundle : bundles) {
            bundle.setProcessWorkflow(WORKFLOW_PATH);
        }
    }

    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessFrequencyInEachColoWithOneProcessRunning_Daily() throws Exception {
        //set daily process
        final String START_TIME = TimeUtil.getTimeWrtSystemTime(-20);
        String endTime = TimeUtil.getTimeWrtSystemTime(4000);
        bundles[1].setProcessPeriodicity(1, TimeUnit.days);
        bundles[1].setOutputFeedPeriodicity(1, TimeUnit.days);
        bundles[1].setProcessValidity(START_TIME, endTime);

        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);

        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);

        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);
        List<String> oldNominalTimes =
            OozieUtil.getActionsNominalTime(cluster3, oldBundleId, ENTITY_TYPE.PROCESS);

        logger.info("original process: " + bundles[1].getProcessData());

        String updatedProcess = InstanceUtil
            .setProcessFrequency(bundles[1].getProcessData(),
                new Frequency(5, TimeUnit.minutes));

        logger.info("updated process: " + updatedProcess);

        //now to update
        ServiceResponse response =
            prism.getProcessHelper().update(updatedProcess, updatedProcess);
        AssertUtil.assertSucceeded(response);
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 1, 10);

        String prismString = dualComparison(bundles[1], cluster2);
        Assert.assertEquals(Util.getProcessObject(prismString).getFrequency(),
            new Frequency(5, TimeUnit.minutes));
        dualComparison(bundles[1], cluster3);
        //ensure that the running process has new coordinators created; while the submitted
        // one is updated
        // correctly.
        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(), true, true);
        AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);
    }


    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void
    updateProcessFrequencyInEachColoWithOneProcessRunning_dailyToMonthly_withStartChange()
        throws Exception {
        //set daily process
        final String START_TIME = TimeUtil.getTimeWrtSystemTime(-20);
        String endTime = TimeUtil.getTimeWrtSystemTime(4000 * 60);
        bundles[1].setProcessPeriodicity(1, TimeUnit.days);
        bundles[1].setOutputFeedPeriodicity(1, TimeUnit.days);
        bundles[1].setProcessValidity(START_TIME, endTime);

        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);

        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);

        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);
        List<String> oldNominalTimes = OozieUtil.getActionsNominalTime(cluster3, oldBundleId,
            ENTITY_TYPE.PROCESS);

        logger.info("original process: " + bundles[1].getProcessData());

        String updatedProcess = InstanceUtil
            .setProcessFrequency(bundles[1].getProcessData(),
                new Frequency(1, TimeUnit.months));
        updatedProcess = InstanceUtil
            .setProcessValidity(updatedProcess, TimeUtil.getTimeWrtSystemTime(10),
                endTime);

        logger.info("updated process: " + updatedProcess);

        //now to update
        ServiceResponse response =
            prism.getProcessHelper().update(updatedProcess, updatedProcess);
        AssertUtil.assertSucceeded(response);
        String prismString = dualComparison(bundles[1], cluster2);
        Assert.assertEquals(Util.getProcessObject(prismString).getFrequency(),
            new Frequency(1, TimeUnit.months));
        dualComparison(bundles[1], cluster3);
        //ensure that the running process has new coordinators created; while the submitted
        // one is updated correctly.
        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(), true, true);
        AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);
    }


    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessRollStartTimeBackwardsToPastInEachColoWithOneProcessRunning()
        throws Exception {
        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);

        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);

        List<String> oldNominalTimes = OozieUtil.getActionsNominalTime(cluster3, oldBundleId,
            ENTITY_TYPE.PROCESS);

        String newStartTime = TimeUtil.addMinsToTime(TimeUtil.dateToOozieDate(
            bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                .getStart()
        ), -3);
        bundles[1].setProcessValidity(newStartTime, TimeUtil.dateToOozieDate(
            bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                .getEnd()
        ));

        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);

        AssertUtil.assertSucceeded(
            prism.getProcessHelper()
                .update(bundles[1].getProcessData(), bundles[1].getProcessData()));

        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(), true, true);
        bundles[1].verifyDependencyListing();
        dualComparison(bundles[1], cluster3);
    }

    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessRollStartTimeForwardInEachColoWithOneProcessSuspended()
        throws Exception {
        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData())
        );
        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);
        Thread.sleep(30000);

        OozieUtil.getNumberOfWorkflowInstances(cluster3, oldBundleId);
        String oldStartTime = TimeUtil.dateToOozieDate(
            bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                .getStart()
        );
        String newStartTime = TimeUtil.addMinsToTime(TimeUtil.dateToOozieDate(
            bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                .getStart()
        ), 3);
        bundles[1].setProcessValidity(newStartTime, TimeUtil.dateToOozieDate(
            bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                .getEnd()
        ));

        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);

        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .suspend(URLS.SUSPEND_URL, bundles[1].getProcessData())
        );

        AssertUtil.assertSucceeded(
            prism.getProcessHelper()
                .update(bundles[1].getProcessData(), bundles[1].getProcessData()));

        dualComparison(bundles[1], cluster2);

        bundles[1].verifyDependencyListing();

        dualComparison(bundles[1], cluster3);
        //ensure that the running process has new coordinators created; while the submitted
        // one is updated correctly.
        int finalNumberOfInstances =
            InstanceUtil.getProcessInstanceListFromAllBundles(cluster3,
                Util.getProcessName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS).size();
        Assert.assertEquals(finalNumberOfInstances,
            getExpectedNumberOfWorkflowInstances(oldStartTime,
                bundles[1].getProcessObject().getClusters().getCluster().get(0)
                    .getValidity().getEnd()));
        Assert.assertEquals(InstanceUtil
            .getProcessInstanceList(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS)
            .size(), getExpectedNumberOfWorkflowInstances(newStartTime,
            bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                .getEnd()));

        AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);
    }

    @Test(groups = {"multiCluster"}, timeOut = 1200000)
    public void updateProcessRollStartTimeBackwardsInEachColoWithOneProcessSuspended()
        throws Exception {
        bundles[1].submitBundle(prism);
        //now to schedule in 1 colo and let it remain in another
        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .schedule(URLS.SCHEDULE_URL, bundles[1].getProcessData()));
        String oldBundleId = InstanceUtil
            .getLatestBundleID(cluster3,
                Util.readEntityName(bundles[1].getProcessData()), ENTITY_TYPE.PROCESS);
        Thread.sleep(30000);

        String newStartTime = TimeUtil.addMinsToTime(TimeUtil.dateToOozieDate(
            bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                .getStart()
        ), -3);
        bundles[1].setProcessValidity(newStartTime, TimeUtil.dateToOozieDate(
            bundles[1].getProcessObject().getClusters().getCluster().get(0).getValidity()
                .getEnd()
        ));
        InstanceUtil.waitTillInstancesAreCreated(cluster3, bundles[1].getProcessData(), 0, 10);

        waitForProcessToReachACertainState(cluster3, bundles[1], Job.Status.RUNNING);

        AssertUtil.assertSucceeded(
            cluster3.getProcessHelper()
                .suspend(URLS.SUSPEND_URL, bundles[1].getProcessData()));
        AssertUtil.assertSucceeded(
            prism.getProcessHelper()
                .update(bundles[1].getProcessData(), bundles[1].getProcessData()));
        AssertUtil.assertSucceeded(cluster3.getProcessHelper()
            .resume(URLS.RESUME_URL, bundles[1].getProcessData()));
        List<String> oldNominalTimes =
            OozieUtil.getActionsNominalTime(cluster3, oldBundleId, ENTITY_TYPE.PROCESS);

        OozieUtil.verifyNewBundleCreation(cluster3, oldBundleId, oldNominalTimes,
            bundles[1].getProcessData(), true, false);

        bundles[1].verifyDependencyListing();

        dualComparison(bundles[1], cluster3);
        waitingForBundleFinish(cluster3, oldBundleId);

        AssertUtil.checkNotStatus(cluster2OC, ENTITY_TYPE.PROCESS, bundles[1], Job.Status.RUNNING);
    }


    @Test(timeOut = 1200000)
    public void
    updateProcessWorkflowXml() throws InterruptedException, URISyntaxException, JAXBException,
        IOException, OozieClientException, IllegalAccessException, NoSuchMethodException,
        InvocationTargetException, AuthenticationException {
        Bundle b = BundleUtil.readELBundles()[0][0];
        HadoopFileEditor hadoopFileEditor = null;
        try {

            b = new Bundle(b, cluster1);
            b.submitBundle(prism);

            b.setProcessValidity(TimeUtil.getTimeWrtSystemTime(-10),
                TimeUtil.getTimeWrtSystemTime(15));
            b.submitAndScheduleBundle(prism);

            InstanceUtil.waitTillInstanceReachState(serverOC.get(1),
                Util.readEntityName(b.getProcessData()), 0, CoordinatorAction.Status.RUNNING, 10,
                ENTITY_TYPE.PROCESS);

            //save old data
            String oldBundleID = InstanceUtil
                .getLatestBundleID(cluster1,
                    Util.readEntityName(b.getProcessData()), ENTITY_TYPE.PROCESS);

            List<String> oldNominalTimes = OozieUtil.getActionsNominalTime(cluster1,
                oldBundleID,
                ENTITY_TYPE.PROCESS);

            //update workflow.xml
            hadoopFileEditor = new HadoopFileEditor(cluster1FS);
            hadoopFileEditor.edit(new ProcessMerlin(b
                .getProcessData()).getWorkflow().getPath() + "/workflow.xml", "</workflow-app>",
                "<!-- some comment -->");

            //update
            prism.getProcessHelper().update(b.getProcessData(),
                b.getProcessData());

            Thread.sleep(20000);
            //verify new bundle creation
            OozieUtil.verifyNewBundleCreation(cluster1, oldBundleID, oldNominalTimes,
                b.getProcessData(), true, true);

        } finally {
            b.deleteBundle(prism);
            if (hadoopFileEditor != null) {
                hadoopFileEditor.restore();
            }
        }

    }

    public ServiceResponse updateProcessConcurrency(Bundle bundle, int concurrency)
        throws Exception {
        String oldData = bundle.getProcessData();
        Process updatedProcess = bundle.getProcessObject();
        updatedProcess.setParallel(concurrency);

        return prism.getProcessHelper()
            .update(oldData, prism.getProcessHelper().toString(updatedProcess));
    }

    private String dualComparison(Bundle bundle, ColoHelper coloHelper) throws Exception {
        String prismResponse = getResponse(prism, bundle, true);
        String coloResponse = getResponse(coloHelper, bundle, true);
        Assert.assertTrue(XmlUtil.isIdentical(prismResponse, coloResponse),
            "Process definition should have been identical");
        return getResponse(prism, bundle, true);
    }

    private void dualComparisonFailure(Bundle bundle, ColoHelper coloHelper) throws Exception {
        Assert.assertFalse(XmlUtil.isIdentical(getResponse(prism, bundle, true),
            getResponse(coloHelper, bundle, true)), "Process definition should not have been " +
            "identical");
    }

    private String getResponse(PrismHelper prism, Bundle bundle, boolean bool)
        throws Exception {
        ServiceResponse response = prism.getProcessHelper()
            .getEntityDefinition(Util.URLS.GET_ENTITY_DEFINITION, bundle.getProcessData());
        if (bool)
            AssertUtil.assertSucceeded(response);
        else
            AssertUtil.assertFailed(response);
        String result = response.getMessage();
        Assert.assertNotNull(result);

        return result;

    }


    private void waitForProcessToReachACertainState(ColoHelper coloHelper, Bundle bundle,
                                                    Job.Status state)
        throws Exception {

        while (OozieUtil.getOozieJobStatus(coloHelper.getFeedHelper().getOozieClient(),
            Util.readEntityName(bundle.getProcessData()), ENTITY_TYPE.PROCESS) != state) {
            //keep waiting
            Thread.sleep(10000);
        }

        //now check if the coordinator is in desired state
        CoordinatorJob coord = getDefaultOozieCoord(coloHelper, InstanceUtil
            .getLatestBundleID(coloHelper, Util.readEntityName(bundle.getProcessData()),
                ENTITY_TYPE.PROCESS));

        while (coord.getStatus() != state) {
            Thread.sleep(10000);
            coord = getDefaultOozieCoord(coloHelper, InstanceUtil
                .getLatestBundleID(coloHelper, Util.readEntityName(bundle.getProcessData()),
                    ENTITY_TYPE.PROCESS));
        }
    }

    private Bundle usualGrind(PrismHelper prism, Bundle b) throws Exception {
        b.setInputFeedDataPath(inputFeedPath);
        String prefix = b.getFeedDataPathPrefix();
        HadoopUtil.deleteDirIfExists(prefix.substring(1), cluster1FS);
        Util.lateDataReplenish(prism, 60, 1, prefix, null);
        final String START_TIME = TimeUtil.getTimeWrtSystemTime(-2);
        String endTime = TimeUtil.getTimeWrtSystemTime(6);
        b.setProcessPeriodicity(1, TimeUnit.minutes);
        b.setOutputFeedPeriodicity(1, TimeUnit.minutes);
        b.setProcessValidity(START_TIME, endTime);
        return b;
    }

    private ExecutionType getRandomExecutionType(Bundle bundle) throws Exception {
        ExecutionType current = bundle.getProcessObject().getOrder();
        Random r = new Random();
        ExecutionType[] values = ExecutionType.values();
        int i;
        do {

            i = r.nextInt(values.length);
        } while (current.equals(values[i]));
        return values[i];
    }

    public ServiceResponse updateProcessFrequency(Bundle bundle,
                                                  org.apache.falcon.regression.core.generated
                                                      .dependencies.Frequency frequency)
        throws Exception {
        String oldData = bundle.getProcessData();
        Process updatedProcess = bundle.getProcessObject();
        updatedProcess.setFrequency(frequency);
        return prism.getProcessHelper()
            .update(oldData, prism.getProcessHelper().toString(updatedProcess));
    }

    //need to expand this function more later
    private int getExpectedNumberOfWorkflowInstances(String start, String end) {
        DateTime startDate = new DateTime(start);
        DateTime endDate = new DateTime(end);
        Minutes minutes = Minutes.minutesBetween((startDate), (endDate));
        return minutes.getMinutes();
    }

    private int getExpectedNumberOfWorkflowInstances(Date start, Date end) {
        DateTime startDate = new DateTime(start);
        DateTime endDate = new DateTime(end);
        Minutes minutes = Minutes.minutesBetween((startDate), (endDate));
        return minutes.getMinutes();
    }

    private int getExpectedNumberOfWorkflowInstances(String start, Date end) {
        DateTime startDate = new DateTime(start);
        DateTime endDate = new DateTime(end);
        Minutes minutes = Minutes.minutesBetween((startDate), (endDate));
        return minutes.getMinutes();
    }

    private void waitingForBundleFinish(ColoHelper coloHelper, String bundleId, int minutes)
        throws Exception {
        int wait = 0;
        while (!OozieUtil.isBundleOver(coloHelper, bundleId)) {
            //keep waiting
            logger.info("bundle not over .. waiting");
            Thread.sleep(60000);
            wait++;
            if (wait == minutes) {
                Assert.assertTrue(false);
                break;
            }
        }
    }

    private void waitingForBundleFinish(ColoHelper coloHelper, String bundleId) throws Exception {
        int wait = 0;
        while (!OozieUtil.isBundleOver(coloHelper, bundleId)) {
            //keep waiting
            logger.info("bundle not over .. waiting, bundleId: " + bundleId);
            Thread.sleep(60000);
            wait++;
            if (wait == 15) {
                Assert.assertTrue(false);
                break;
            }
        }
    }

    private CoordinatorJob getDefaultOozieCoord(ColoHelper coloHelper, String bundleId)
        throws Exception {
        OozieClient client = coloHelper.getFeedHelper().getOozieClient();
        BundleJob bundlejob = client.getBundleJobInfo(bundleId);

        for (CoordinatorJob coord : bundlejob.getCoordinators()) {
            if (coord.getAppName().contains("DEFAULT")) {
                return client.getCoordJobInfo(coord.getId());
            }
        }
        return null;
    }

}
