/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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

package org.nuxeo.ecm.core.storage.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.runtime.api.Framework;

/**
 * @author Florent Guillaume
 */
public class DatabaseOracle extends DatabaseHelper {

    private static final Log log = LogFactory.getLog(DatabaseOracle.class);

    public static DatabaseHelper INSTANCE = new DatabaseOracle();

    private static final String DEF_SERVER = "localhost";

    private static final String DEF_URL = "jdbc:oracle:thin:@" + DEF_SERVER + ":1521:XE";

    private static final String DEF_USER = "nuxeo";

    private static final String DEF_PASSWORD = "nuxeo";

    private static final String DRIVER = "oracle.jdbc.OracleDriver";

    @Override
    protected void setProperties() {
        setProperty(URL_PROPERTY, DEF_URL);
        setProperty(USER_PROPERTY, DEF_USER);
        setProperty(PASSWORD_PROPERTY, DEF_PASSWORD);
        setProperty(DRIVER_PROPERTY, DRIVER);
        setProperty(ID_TYPE_PROPERTY, DEF_ID_TYPE);
    }

    @Override
    public void initDatabase(Connection connection) throws Exception {
        doOnAllTables(connection, null, Framework.getProperty(USER_PROPERTY).toUpperCase(),
                "DROP TABLE \"%s\" CASCADE CONSTRAINTS PURGE");
        dropSequences(connection);
    }

    public void dropSequences(Connection connection) throws SQLException {
        List<String> sequenceNames = new ArrayList<String>();
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT SEQUENCE_NAME FROM USER_SEQUENCES");
        while (rs.next()) {
            String sequenceName = rs.getString(1);
            if (sequenceName.indexOf('$') != -1) {
                continue;
            }
            sequenceNames.add(sequenceName);
        }
        rs.close();
        for (String sequenceName : sequenceNames) {
            String sql = String.format("DROP SEQUENCE \"%s\"", sequenceName);
            log.trace("SQL: " + sql);
            st.execute(sql);
        }
        st.close();
    }

    @Override
    public RepositoryDescriptor getRepositoryDescriptor() {
        RepositoryDescriptor descriptor = new RepositoryDescriptor();
        descriptor.xaDataSourceName = "oracle.jdbc.xa.client.OracleXADataSource";
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("URL", Framework.getProperty(URL_PROPERTY));
        properties.put("User", Framework.getProperty(USER_PROPERTY));
        properties.put("Password", Framework.getProperty(PASSWORD_PROPERTY));
        descriptor.properties = properties;
        descriptor.idType = Framework.getProperty(ID_TYPE_PROPERTY);
        return descriptor;
    }

    @Override
    public boolean supportsClustering() {
        return true;
    }

    @Override
    public boolean supportsSoftDelete() {
        return true;
    }

    @Override
    public boolean supportsSequenceId() {
        return true;
    }

}
