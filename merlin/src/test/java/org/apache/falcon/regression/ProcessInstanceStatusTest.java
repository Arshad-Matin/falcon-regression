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

package org.apache.falcon.regression;

import org.apache.falcon.regression.core.bundle.Bundle;
import org.apache.falcon.regression.core.generated.dependencies.Frequency.TimeUnit;
import org.apache.falcon.regression.core.helpers.ColoHelper;
import org.apache.falcon.regression.core.response.ProcessInstancesResult;
import org.apache.falcon.regression.core.response.ProcessInstancesResult.WorkflowStatus;
import org.apache.falcon.regression.core.response.ResponseKeys;
import org.apache.falcon.regression.core.util.AssertUtil;
import org.apache.falcon.regression.core.util.BundleUtil;
import org.apache.falcon.regression.core.util.HadoopUtil;
import org.apache.falcon.regression.core.util.InstanceUtil;
import org.apache.falcon.regression.core.util.OSUtil;
import org.apache.falcon.regression.core.util.TimeUtil;
import org.apache.falcon.regression.core.util.Util;
import org.apache.falcon.regression.core.util.Util.URLS;
import org.apache.falcon.regression.testHelper.BaseTestClass;
import org.apache.hadoop.fs.FileSystem;
import org.apache.log4j.Logger;
import org.apache.oozie.client.CoordinatorAction.Status;
import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Process instance status tests.
 */
@Test(groups = "embedded")
public class ProcessInstanceStatusTest extends BaseTestClass {

    ColoHelper cluster = servers.get(0);
    FileSystem clusterFS = serverFS.get(0);
    String baseTestHDFSDir = baseHDFSDir + "/ProcessInstanceStatusTest";
    String aggregateWorkflowDir = baseTestHDFSDir + "/aggregator";
    String feedInputPath = baseTestHDFSDir + "/${YEAR}/${MONTH}/${DAY}/${HOUR}/${MINUTE}";
    String feedOutputPath =
        baseTestHDFSDir + "/output-data/${YEAR}/${MONTH}/${DAY}/${HOUR}/${MINUTE}";
    String feedInputTimedOutPath =
        baseTestHDFSDir + "/timedoutStatus/${YEAR}/${MONTH}/${DAY}/${HOUR}/${MINUTE}";
    String feedOutputTimedOutPath =
        baseTestHDFSDir + "/output-data/timedoutStatus/${YEAR}/${MONTH}/${DAY}/${HOUR}/${MINUTE}";
    private static final Logger logger = Logger.getLogger(ProcessInstanceStatusTest.class);

    @BeforeClass(alwaysRun = true)
    public void createTestData() throws Exception {

        logger.info("in @BeforeClass");

        HadoopUtil.uploadDir(clusterFS, aggregateWorkflowDir, OSUtil.RESOURCES_OOZIE);

        Bundle bundle = BundleUtil.readELBundles()[0][0];
        bundle.generateUniqueBundle();
        bundle = new Bundle(bundle, cluster);

        String startDate = "2010-01-01T20:00Z";
        String endDate = "2010-01-03T01:04Z";

        bundle.setInputFeedDataPath(feedInputPath);
        String prefix = bundle.getFeedDataPathPrefix();

        HadoopUtil.deleteDirIfExists(prefix.substring(1), clusterFS);

        DateTime startDateJoda = new DateTime(TimeUtil.oozieDateToDate(startDate));
        DateTime endDateJoda = new DateTime(TimeUtil.oozieDateToDate(endDate));

        List<String> dataDates =
            TimeUtil.getMinuteDatesOnEitherSide(startDateJoda, endDateJoda, 20);

        for (int i = 0; i < dataDates.size(); i++)
            dataDates.set(i, prefix + dataDates.get(i));

        ArrayList<String> dataFolder = new ArrayList<String>();

        for (String dataDate : dataDates) {
            dataFolder.add(dataDate);
        }

        HadoopUtil.flattenAndPutDataInFolder(clusterFS, OSUtil.NORMAL_INPUT, dataFolder);
    }


    @BeforeMethod(alwaysRun = true)
    public void setup(Method method) throws Exception {
        logger.info("test name: " + method.getName());
        bundles[0] = BundleUtil.readELBundles()[0][0];
        bundles[0] = new Bundle(bundles[0], cluster);
        bundles[0].setInputFeedDataPath(feedInputPath);
        bundles[0].setProcessWorkflow(aggregateWorkflowDir);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        removeBundles();
    }

    /**
     * time out is set as 3 minutes .... getStatus is for a large range in past.
     * 6 instance should be materialized and one in running and other in waiting
     *
     * @throws Exception
     */
    @Test(groups = {"singleCluster"})
    public void testProcessInstanceStatus_StartAndEnd_checkNoInstanceAfterEndDate()
        throws Exception {
        bundles[0].setProcessValidity("2010-01-02T01:00Z", "2010-01-03T10:22Z");
        bundles[0].setProcessTimeOut(3, TimeUnit.minutes);
        bundles[0].setProcessPeriodicity(1, TimeUnit.minutes);
        bundles[0].setProcessConcurrency(1);
        bundles[0].submitAndScheduleBundle(prism);
        Thread.sleep(5000);
        ProcessInstancesResult r = prism.getProcessHelper()
            .getProcessInstanceStatus(Util.readEntityName(bundles[0].getProcessData()),
                "?start=2010-01-02T01:00Z&end=2010-01-02T10:20Z");
        InstanceUtil.validateSuccess(r, bundles[0], WorkflowStatus.RUNNING);
        InstanceUtil.validateResponse(r, 6, 1, 0, 5, 0);
    }

    /**
     * Perform -getStatus using only -start parameter within time-range of non-materialized
     * instances. There should be no instances returned in response.
     *
     * @throws Exception
     */
    @Test(groups = {"singleCluster"})
    public void testProcessInstanceStatus_onlyStartAfterMat() throws Exception {
        bundles[0].setProcessValidity("2010-01-02T01:00Z", "2010-01-03T10:22Z");
        bundles[0].setProcessTimeOut(3, TimeUnit.minutes);
        bundles[0].setProcessPeriodicity(1, TimeUnit.minutes);
        bundles[0].setProcessConcurrency(1);
        bundles[0].submitAndScheduleBundle(prism);
        Thread.sleep(5000);
        ProcessInstancesResult r = prism.getProcessHelper()
            .getProcessInstanceStatus(Util.readEntityName(bundles[0].getProcessData()),
                "?start=2010-01-02T05:00Z");
        AssertUtil.assertSucceeded(r);
        Assert.assertEquals(r.getInstances(), null);
    }

    /**
     * Schedule process. Perform -getStatus using -end parameter which is out of process
     * validity range. Attempt should fail.
     *
     * @throws Exception
     */
    @Test(groups = {"singleCluster"})
    public void testProcessInstanceStatus_EndOutOfRange() throws Exception {
        bundles[0].setProcessValidity("2010-01-02T01:00Z", "2010-01-02T01:22Z");
        bundles[0].setProcessPeriodicity(5, TimeUnit.minutes);
        bundles[0].setOutputFeedPeriodicity(5, TimeUnit.minutes);
        bundles[0].submitAndScheduleBundle(prism);
        Thread.sleep(15000);
        ProcessInstancesResult r = prism.getProcessHelper()
            .getProcessInstanceStatus(Util.readEntityName(bundles[0].getProcessData()),
                "?start=2010-01-02T01:00Z&end=2010-01-02T01:30Z");
        InstanceUtil.validateSuccessWithStatusCode(r, 400);
    }

    /**
     * Schedule process and try to -getStatus without date parameters. Attempt should fail with
     * an appropriate message.
     */
    @Test(groups = {"singleCluster"})
    public void testProcessInstanceStatus_dateEmpty() {
        try {
            bundles[0].setProcessValidity("2010-01-02T01:00Z", "2010-01-02T02:30Z");
            bundles[0].setProcessPeriodicity(5, TimeUnit.minutes);
            bundles[0].submitAndScheduleBundle(prism);
            ProcessInstancesResult r = prism.getProcessHelper()
                .getProcessInstanceStatus(Util.readEntityName(bundles[0].getProcessData()), "");
            InstanceUtil.validateSuccessWithStatusCode(r, ResponseKeys.UNPARSEABLE_DATE);
        } catch (Exception e) {
            if (!e.getMessage().contains("Expected BEGIN_OBJECT but was STRING at line 1 column"))
                Assert.assertTrue(false);
        }
    }

    /**
     * Schedule process with number of instances. Perform -getStatus request with valid
     * parameters. Attempt should succeed.
     *
     * @throws Exception
     */
    @Test(groups = {"singleCluster"})
    public void testProcessInstanceStatus_StartAndEnd() throws Exception {
        bundles[0].setProcessValidity("2010-01-02T01:00Z", "2010-01-02T01:22Z");
        bundles[0].setProcessPeriodicity(5, TimeUnit.minutes);
        bundles[0].submitAndScheduleBundle(prism);
        Thread.sleep(5000);
        ProcessInstancesResult r = prism.getProcessHelper()
            .getProcessInstanceStatus(Util.readEntityName(bundles[0].getProcessData()),
                "?start=2010-01-02T01:00Z&end=2010-01-02T01:20Z");
        InstanceUtil.validateSuccess(r, bundles[0], WorkflowStatus.RUNNING);
    }

    /**
     * Schedule process. Perform -getStatus using -start parameter which is out of process
     * validity range. Attempt should fail.
     *
     * @throws Exception
     */
    @Test(groups = {"singleCluster"})
    public void testProcessInstanceStatus_StartOutOfRange() throws Exception {
        bundles[0].setProcessValidity("2010-01-02T01:00Z", "2010-01-02T01:22Z");
        bundles[0].setProcessPeriodicity(5, TimeUnit.minutes);
        bundles[0].setOutputFeedPeriodicity(5, TimeUnit.minutes);
        bundles[0].submitAndScheduleBundle(prism);
        Thread.sleep(5000);
        ProcessInstancesResult r = prism.getProcessHelper()
            .getProcessInstanceStatus(Util.readEntityName(bundles[0].getProcessData()),
                "?start=2010-01-02T00:00Z&end=2010-01-02T01:20Z");
        InstanceUtil.validateSuccessWithStatusCode(r, 400);
    }

    /**
     * Schedule and then delete process. Try to get the status of its instances. Attempt should
     * fail with an appropriate code.
     *
     * @throws Exception
     */
    @Test(groups = {"singleCluster"})
    public void testProcessInstanceStatus_killed() throws Exception {
        bundles[0].setProcessValidity("2010-01-02T01:00Z", "2010-01-02T01:22Z");
        bundles[0].setProcessPeriodicity(5, TimeUnit.minutes);
        bundles[0].submitAndScheduleBundle(prism);
        Thread.sleep(15000);
        prism.getProcessHelper().delete(URLS.DELETE_URL, bundles[0].getProcessData());
        Thread.sleep(15000);
        ProcessInstancesResult r = prism.getProcessHelper()
            .getProcessInstanceStatus(Util.readEntityName(bundles[0].getProcessData()),
                "?start=2010-01-02T01:00Z&end=2010-01-02T01:20Z");
        if ((r.getStatusCode() != ResponseKeys.PROCESS_NOT_FOUND))
            Assert.assertTrue(false);
    }

    /**
     * Schedule process and then suspend it. -getStatus of first instance only -start parameter.
     * Instance should be suspended.
     *
     * @throws Exception
     */
    @Test(groups = {"singleCluster"})
    public void testProcessInstanceStatus_onlyStartSuspended() throws Exception {
        bundles[0].setProcessValidity("2010-01-02T01:00Z", "2010-01-02T01:22Z");
        bundles[0].setProcessPeriodicity(5, TimeUnit.minutes);
        bundles[0].submitAndScheduleBundle(prism);
        Thread.sleep(15000);
        prism.getProcessHelper().suspend(URLS.SUSPEND_URL, bundles[0].getProcessData());
        Thread.sleep(15000);
        ProcessInstancesResult r = prism.getProcessHelper()
            .getProcessInstanceStatus(Util.readEntityName(bundles[0].getProcessData()),
                "?start=2010-01-02T01:00Z");
        InstanceUtil.validateSuccessOnlyStart(r, WorkflowStatus.SUSPENDED);
    }

    /**
     * Schedule process. Try to -getStatus using -start/-end parameters with values which were
     * reversed i.e. -start is further then -end. Attempt should fail.
     *
     * @throws Exception
     */
    @Test(groups = {"singleCluster"})
    public void testProcessInstanceStatus_reverseDateRange() throws Exception {
        bundles[0].setProcessValidity("2010-01-02T01:00Z", "2010-01-02T01:22Z");
        bundles[0].setProcessPeriodicity(5, TimeUnit.minutes);
        bundles[0].submitAndScheduleBundle(prism);
        Thread.sleep(15000);
        ProcessInstancesResult r = prism.getProcessHelper()
            .getProcessInstanceStatus(Util.readEntityName(bundles[0].getProcessData()),
                "?start=2010-01-02T01:20Z&end=2010-01-02T01:07Z");
        InstanceUtil.validateSuccessWithStatusCode(r, 400);
    }

    /**
     * Schedule process. Perform -getStatus using -start/-end parameters which are out of process
     * validity range. Attempt should fail.
     *
     * @throws Exception
     */
    @Test(groups = {"singleCluster"})
    public void testProcessInstanceStatus_StartEndOutOfRange() throws Exception {
        bundles[0].setProcessValidity("2010-01-02T01:00Z", "2010-01-02T01:22Z");
        bundles[0].setProcessPeriodicity(5, TimeUnit.minutes);
        bundles[0].setOutputFeedPeriodicity(5, TimeUnit.minutes);
        bundles[0].setOutputFeedLocationData(
            feedOutputPath);
        bundles[0].setProcessConcurrency(2);
        bundles[0].submitAndScheduleBundle(prism);
        Thread.sleep(15000);
        ProcessInstancesResult r = prism.getProcessHelper()
            .getProcessInstanceStatus(Util.readEntityName(bundles[0].getProcessData()),
                "?start=2010-01-02T00:00Z&end=2010-01-02T01:30Z");
        InstanceUtil.validateSuccessWithStatusCode(r, 400);
    }

    /**
     * Schedule process. Suspend and then resume it. -getStatus of its instances. Check that
     * response reflects that instances are running.
     *
     * @throws Exception
     */
    @Test(groups = {"singleCluster"})
    public void testProcessInstanceStatus_resumed() throws Exception {
        bundles[0].setProcessValidity("2010-01-02T01:00Z", "2010-01-02T01:22Z");
        bundles[0].setProcessPeriodicity(5, TimeUnit.minutes);
        bundles[0].setOutputFeedPeriodicity(5, TimeUnit.minutes);
        bundles[0].setOutputFeedLocationData(feedOutputPath);
        bundles[0].setProcessConcurrency(2);
        bundles[0].submitAndScheduleBundle(prism);
        Thread.sleep(15000);
        prism.getProcessHelper().suspend(URLS.SUSPEND_URL, bundles[0].getProcessData());
        Thread.sleep(15000);
        prism.getProcessHelper().resume(URLS.RESUME_URL, bundles[0].getProcessData());
        Thread.sleep(15000);
        ProcessInstancesResult r = prism.getProcessHelper()
            .getProcessInstanceStatus(Util.readEntityName(bundles[0].getProcessData()),
                "?start=2010-01-02T01:00Z&end=2010-01-02T01:22Z");
        InstanceUtil.validateSuccess(r, bundles[0], WorkflowStatus.RUNNING);
    }

    /**
     * Schedule process. -getStatus of it's first instance using only -start parameter which
     * points to start time of process validity. Check that response reflects expected status of
     * instance.
     *
     * @throws Exception
     */
    @Test(groups = {"singleCluster"})
    public void testProcessInstanceStatus_onlyStart() throws Exception {
        bundles[0].setProcessValidity("2010-01-02T01:00Z", "2010-01-02T01:22Z");
        bundles[0].setProcessPeriodicity(5, TimeUnit.minutes);
        bundles[0].submitAndScheduleBundle(prism);
        Thread.sleep(15000);
        ProcessInstancesResult r = prism.getProcessHelper()
            .getProcessInstanceStatus(Util.readEntityName(bundles[0].getProcessData()),
                "?start=2010-01-02T01:00Z");
        InstanceUtil.validateSuccessOnlyStart(r, WorkflowStatus.RUNNING);
    }

    /**
     * Schedule process. Try to perform -getStatus using valid -start parameter but invalid
     * process name. Attempt should fail with an appropriate status code.
     *
     * @throws Exception
     */
    @Test(groups = {"singleCluster"})
    public void testProcessInstanceStatus_invalidName() throws Exception {
        bundles[0].setProcessValidity("2010-01-02T01:00Z", "2010-01-02T02:30Z");
        bundles[0].setProcessPeriodicity(5, TimeUnit.minutes);
        bundles[0].submitAndScheduleBundle(prism);
        ProcessInstancesResult r = prism.getProcessHelper()
            .getProcessInstanceStatus("invalidProcess", "?start=2010-01-01T01:00Z");
        if (!(r.getStatusCode() == ResponseKeys.PROCESS_NOT_FOUND))
            Assert.assertTrue(false);
    }

    /**
     * Schedule process. Suspend it. -getStatus of it's instances. Check if response reflects
     * their status as suspended.
     *
     * @throws Exception
     */
    @Test(groups = {"singleCluster"})
    public void testProcessInstanceStatus_suspended() throws Exception {
        bundles[0].setProcessValidity("2010-01-02T01:00Z", "2010-01-02T01:22Z");
        bundles[0].setProcessPeriodicity(5, TimeUnit.minutes);
        for (int i = 0; i < bundles[0].getClusters().size(); i++)
            logger.info("cluster to be submitted: " + i + "  " + bundles[0].getClusters().get(i));
        bundles[0].submitAndScheduleBundle(prism);
        Thread.sleep(5000);
        prism.getProcessHelper().suspend(URLS.SUSPEND_URL, bundles[0].getProcessData());
        Thread.sleep(5000);
        ProcessInstancesResult r = prism.getProcessHelper()
            .getProcessInstanceStatus(Util.readEntityName(bundles[0].getProcessData()),
                "?start=2010-01-02T01:00Z&end=2010-01-02T01:20Z");
        InstanceUtil.validateSuccess(r, bundles[0], WorkflowStatus.SUSPENDED);
    }

    /**
     * Schedule process. Try to -getStatus without time range parameters. Attempt should fails
     * with an appropriate status code.
     *
     * @throws Exception
     */
    @Test(groups = {"singleCluster"})
    public void testProcessInstanceStatus_woParams() throws Exception {
        bundles[0].setProcessValidity("2010-01-02T01:00Z", "2010-01-02T02:30Z");
        bundles[0].setProcessPeriodicity(5, TimeUnit.minutes);
        bundles[0].submitAndScheduleBundle(prism);
        ProcessInstancesResult r = prism.getProcessHelper()
            .getProcessInstanceStatus(Util.readEntityName(bundles[0].getProcessData()), null);
        InstanceUtil.validateSuccessWithStatusCode(r, ResponseKeys.UNPARSEABLE_DATE);
    }

    /**
     * Schedule process with timeout set to 2 minutes. Wait till it become timed-out. -getStatus
     * of that process. Check that all materialized instances are failed.
     *
     * @throws Exception
     */
    @Test(groups = {"singleCluster"})
    public void testProcessInstanceStatus_timedOut() throws Exception {
        bundles[0].setInputFeedDataPath(feedInputTimedOutPath);
        bundles[0].setProcessValidity("2010-01-02T01:00Z", "2010-01-02T01:11Z");
        bundles[0].setProcessPeriodicity(5, TimeUnit.minutes);
        bundles[0].setProcessTimeOut(2, TimeUnit.minutes);
        bundles[0].setOutputFeedPeriodicity(5, TimeUnit.minutes);
        bundles[0].setOutputFeedLocationData(feedOutputTimedOutPath);
        bundles[0].setProcessConcurrency(3);
        bundles[0].submitAndScheduleBundle(prism);
        Status status = null;
        while (status != Status.TIMEDOUT) {
            status = InstanceUtil
                .getInstanceStatus(cluster, Util.readEntityName(bundles[0].getProcessData()), 0,
                    0);
            Thread.sleep(15000);
        }
        ProcessInstancesResult r = prism.getProcessHelper()
            .getProcessInstanceStatus(Util.readEntityName(bundles[0].getProcessData()),
                "?start=2010-01-02T01:00Z&end=2010-01-02T01:11Z");
        InstanceUtil.validateFailedInstances(r, 3);
    }
}
