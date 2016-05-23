/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.work;

import static org.nuxeo.ecm.core.work.api.Work.State.CANCELED;
import static org.nuxeo.ecm.core.work.api.Work.State.COMPLETED;
import static org.nuxeo.ecm.core.work.api.Work.State.RUNNING;
import static org.nuxeo.ecm.core.work.api.Work.State.SCHEDULED;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.NXRuntimeTestCase;
import org.nuxeo.runtime.transaction.TransactionHelper;

public class WorkManagerTXTest extends NXRuntimeTestCase {

    protected static final String CATEGORY = "SleepWork";

    protected static final String QUEUE = "SleepWork";

    protected WorkManager service;

    protected boolean dontClearCompletedWork;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        deployBundle("org.nuxeo.runtime.jtajca");
        deployBundle("org.nuxeo.ecm.core.event");
        deployContrib("org.nuxeo.ecm.core.event.test", "test-workmanager-config.xml");
        fireFrameworkStarted();
        service = Framework.getLocalService(WorkManager.class);
        assertNotNull(service);
        service.clearCompletedWork(0);
        assertEquals(0, service.getQueueSize(QUEUE, COMPLETED));
        assertEquals(0, service.getQueueSize(QUEUE, RUNNING));
        assertEquals(0, service.getQueueSize(QUEUE, SCHEDULED));
        TransactionHelper.startTransaction();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        if (!dontClearCompletedWork) {
            service.clearCompletedWork(0);
        }
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.setTransactionRollbackOnly();
            TransactionHelper.commitOrRollbackTransaction();
        }
        super.tearDown();
    }

    @Test
    public void testWorkManagerPostCommit() throws Exception {
        int duration = 1000; // 1s
        assertEquals(0, service.getQueueSize(QUEUE, SCHEDULED));
        assertEquals(0, service.getQueueSize(QUEUE, COMPLETED));
        SleepWork work = new SleepWork(duration, false);
        service.schedule(work, true);
        assertEquals(0, service.getQueueSize(QUEUE, SCHEDULED));
        assertEquals(0, service.getQueueSize(QUEUE, COMPLETED));

        TransactionHelper.commitOrRollbackTransaction();

        Thread.sleep(duration + 1000);
        assertEquals(0, service.getQueueSize(QUEUE, SCHEDULED));
        assertEquals(1, service.getQueueSize(QUEUE, COMPLETED));
        // tx commit triggered a release of the scheduled work
        assertEquals(COMPLETED, work.getWorkInstanceState());
    }

    @Test
    public void testWorkManagerRollback() throws Exception {
        Assert.assertTrue(TransactionHelper.isTransactionActive());
        int duration = 1000; // 1s
        assertEquals(0, service.getQueueSize(QUEUE, SCHEDULED));
        assertEquals(0, service.getQueueSize(QUEUE, COMPLETED));
        SleepWork work = new SleepWork(duration, false);
        service.schedule(work, true);
        assertEquals(0, service.getQueueSize(QUEUE, SCHEDULED));
        assertEquals(0, service.getQueueSize(QUEUE, COMPLETED));
        TransactionHelper.setTransactionRollbackOnly();
        TransactionHelper.commitOrRollbackTransaction();
        // tx rollback cancels the task and removes it
        assertEquals(0, service.getQueueSize(QUEUE, SCHEDULED));
        assertEquals(0, service.getQueueSize(QUEUE, COMPLETED));
        assertEquals(CANCELED, work.getWorkInstanceState());
    }

}
