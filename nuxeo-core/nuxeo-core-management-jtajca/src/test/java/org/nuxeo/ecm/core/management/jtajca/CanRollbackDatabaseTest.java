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
package org.nuxeo.ecm.core.management.jtajca;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.inject.Inject;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.datasource.DataSourceHelper;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeHarness;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.nuxeo.runtime.transaction.TransactionRuntimeException;

import junit.framework.AssertionFailedError;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
public class CanRollbackDatabaseTest {

    @Inject
    protected RuntimeHarness harness;

    // don't use LocalDeploy, it fails on SQL Server (deploy is done on a connection with tx)
    @Test(expected = TransactionRuntimeException.class)
    public void testFatalRollback() throws Exception {
        try (AutoCloseable closeable = harness.deployTestContrib("org.nuxeo.ecm.core.management.jtajca",
                "ds-contrib-with-fatal.xml")) {
            insertWrongReference();
        }
    }

    // don't use LocalDeploy, it fails on SQL Server (deploy is done on a connection with tx)
    @Test(expected = SQLException.class)
    public void testNoFatalRollback() throws Exception {
        try (AutoCloseable closeable = harness.deployTestContrib("org.nuxeo.ecm.core.management.jtajca",
                "ds-contrib.xml")) {
            insertWrongReference();
        }
    }

    private void insertWrongReference() throws NamingException, SQLException, AssertionFailedError {
        DataSource ds = DataSourceHelper.getDataSource("rollback");
        {
            try (Connection db = ds.getConnection()) {
                try (Statement st = db.createStatement()) {
                    st.execute("INSERT into hierarchy(id) values('pfouh')");
                    st.addBatch("INSERT into hierarchy (id, parentid ) values ('1','2')");
                    st.executeBatch();
                }
            } finally {
                try {
                    TransactionHelper.setTransactionRollbackOnly();
                    TransactionHelper.commitOrRollbackTransaction();
                } finally {
                    TransactionHelper.startTransaction();
                    try (Connection db = ds.getConnection()) {
                        try (Statement st = db.createStatement()) {
                            try (ResultSet rs = st.executeQuery("SELECT id from hierarchy where id = 'pfouh'")) {
                                if (rs.next()) {
                                    throw new AssertionFailedError("connection was not rollbacked");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
