/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Stephane Lacoin
 */
package org.nuxeo.ecm.directory.sql;

import javax.inject.Inject;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.TransactionalConfig;
import org.nuxeo.ecm.directory.Directory;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.datasource.ConnectionHelper;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.test.runner.LogCaptureFeature;
import org.nuxeo.runtime.test.runner.LogCaptureFeature.NoLogCaptureFilterException;
import org.nuxeo.runtime.transaction.TransactionHelper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

@RunWith(FeaturesRunner.class)
@Features({ LogCaptureFeature.class, CoreFeature.class })
@Deploy({ "org.nuxeo.ecm.core.schema", "org.nuxeo.ecm.core.api", "org.nuxeo.ecm.core", "org.nuxeo.ecm.directory",
        "org.nuxeo.ecm.directory.sql" })
@LocalDeploy({ "org.nuxeo.ecm.directory:test-sql-directories-schema-override.xml",
        "org.nuxeo.ecm.directory.sql:test-sql-directories-bundle.xml" })
@LogCaptureFeature.FilterWith(TestSessionsAreClosedAutomatically.CloseSessionFilter.class)
@TransactionalConfig(autoStart = false)
public class TestSessionsAreClosedAutomatically {

    public static class CloseSessionFilter implements LogCaptureFeature.Filter {

        @Override
        public boolean accept(ILoggingEvent event) {
            if (!SQLDirectory.class.getName().equals(event.getLoggerName())) {
                return false;
            }
            if (!Level.WARN.equals(event.getLevel())) {
                return false;
            }
            String msg = event.getMessage().toString();
            if (!msg.startsWith("Closing a sql directory session")) {
                return false;
            }
            return true;
        }

    }

    protected Directory userDirectory;

    protected @Inject LogCaptureFeature.Result caughtEvents;

    @Before
    public void setSingleDataSourceMode() {
        Framework.getProperties().setProperty(ConnectionHelper.SINGLE_DS, "jdbc/NuxeoTestDS");
    }

    @Before
    public void fetchUserDirectory() throws DirectoryException {
        userDirectory = Framework.getService(DirectoryService.class).getDirectory("userDirectory");
        Assert.assertNotNull(userDirectory);
    }

    @Test
    public void hasNoWarns() throws DirectoryException, NoLogCaptureFilterException {
        boolean started = TransactionHelper.startTransaction();

        try {
            try (Session session = userDirectory.getSession()) {
                // do nothing
            }
        } finally {
            if (started) {
                TransactionHelper.commitOrRollbackTransaction();
            }
        }
        Assert.assertTrue(caughtEvents.getCaughtEvents().isEmpty());
    }

    @Test
    public void hasWarnsOnCommit() throws DirectoryException, NoLogCaptureFilterException {
        boolean started = TransactionHelper.startTransaction();
        try {
            userDirectory.getSession();
        } finally {
            if (started) {
                TransactionHelper.commitOrRollbackTransaction();
            }
        }
        caughtEvents.assertHasEvent();
    }

    @Test
    public void hasWarnsOnRollback() throws DirectoryException, NoLogCaptureFilterException {
        boolean started = TransactionHelper.startTransaction();
        try {
            userDirectory.getSession();
        } finally {
            if (started) {
                TransactionHelper.setTransactionRollbackOnly();
                TransactionHelper.commitOrRollbackTransaction();
            }
        }
        caughtEvents.assertHasEvent();
    }
}
