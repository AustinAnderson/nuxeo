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
 *     Benoit Delbosc
 */

package org.nuxeo.ecm.core.storage.sql;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.JDBCUtils;
import org.nuxeo.ecm.core.blob.binary.BinaryManager;
import org.nuxeo.ecm.core.blob.binary.DefaultBinaryManager;
import org.nuxeo.runtime.RuntimeServiceEvent;
import org.nuxeo.runtime.RuntimeServiceListener;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.datasource.ConnectionHelper;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

public abstract class DatabaseHelper {

    private static final Log log = LogFactory.getLog(DatabaseHelper.class);

    public static final String DB_PROPERTY = "nuxeo.test.vcs.db";

    public static final String DB_DEFAULT = "H2";

    public static final String DEF_ID_TYPE = "varchar"; // "varchar", "uuid", "sequence"

    private static final boolean SINGLEDS_DEFAULT = false;

    public static DatabaseHelper DATABASE;

    public static final String DB_CLASS_NAME_BASE = "org.nuxeo.ecm.core.storage.sql.Database";

    protected static final Class<? extends BinaryManager> defaultBinaryManager = DefaultBinaryManager.class;

    static {
        setSystemProperty(DB_PROPERTY, DB_DEFAULT);
        String className = System.getProperty(DB_PROPERTY);
        if (className.indexOf('.') < 0) {
            className = DB_CLASS_NAME_BASE + className;
        }
        setDatabaseForTests(className);
    }

    // available for JDBC tests
    public static final String DRIVER_PROPERTY = "nuxeo.test.vcs.driver";

    // available for JDBC tests
    public static final String XA_DATASOURCE_PROPERTY = "nuxeo.test.vcs.xadatasource";

    // available for JDBC tests
    public static final String URL_PROPERTY = "nuxeo.test.vcs.url";

    public static final String SERVER_PROPERTY = "nuxeo.test.vcs.server";

    public static final String PORT_PROPERTY = "nuxeo.test.vcs.port";

    public static final String DATABASE_PROPERTY = "nuxeo.test.vcs.database";

    public static final String REPOSITORY_PROPERTY = "nuxeo.test.vcs.repository";


    public static final String USER_PROPERTY = "nuxeo.test.vcs.user";

    public static final String PASSWORD_PROPERTY = "nuxeo.test.vcs.password";

    public static final String ID_TYPE_PROPERTY = "nuxeo.test.vcs.idtype";

    // set this to true to activate single datasource for all tests
    public static final String SINGLEDS_PROPERTY = "nuxeo.test.vcs.singleds";

    public static final String FULLTEXT_DISABLED_PROPERTY = "nuxeo.test.vcs.fulltext.disabled";

    public static final String FULLTEXT_SEARCH_DISABLED_PROPERTY = "nuxeo.test.vcs.fulltext.search.disabled";

    public static final String FULLTEXT_ANALYZER_PROPERTY = "nuxeo.test.vcs.fulltext.analyzer";

    protected Error owner;

    public static String setSystemProperty(String name, String def) {
        String value = System.getProperty(name);
        if (value == null || value.equals("") || value.equals("${" + name + "}")) {
            System.setProperty(name, def);
        }
        return value;
    }

    public static String getProperty(String name) {
        return Framework.getProperty(name);
    }

    public static String getProperty(String name, String defvalue) {
        return Framework.getProperty(name, defvalue);
    }

    public static String setProperty(String name, String def) {
        String value = System.getProperty(name);
        if (value == null || value.equals("") || value.equals("${" + name + "}")) {
            value = def;
        }
        Framework.getProperties().setProperty(name, value);
        return value;
    }

    public static String setProperty(String name, String format, String... properties) {
        List<String> values = Stream.of(properties).map(key -> getProperty(key)).collect(Collectors.toList());
        return setProperty(name, String.format(format, values.toArray(new Object[values.size()])));
    }

    public static final String DEFAULT_DATABASE_NAME = "nuxeojunittests";

    public static void setDatabaseName(String name) {
        setProperty(DATABASE_PROPERTY, name);
    }

    public static String getDatabaseName() {
        return getProperty(DATABASE_PROPERTY, DEFAULT_DATABASE_NAME);
    }

    public static void setFulltextMode(boolean disabled, String analyzer, boolean searchDisabled) {
        setProperty(FULLTEXT_DISABLED_PROPERTY, Boolean.toString(disabled));
        setProperty(FULLTEXT_ANALYZER_PROPERTY, analyzer);
        setProperty(FULLTEXT_SEARCH_DISABLED_PROPERTY, Boolean.toString(searchDisabled));
    }

    public static final String DEFAULT_REPOSITORY_NAME = "test";

    public static void setRepositoryName(String name) {
        setProperty(REPOSITORY_PROPERTY, name);
    }

    public static String getRepositoryName() {
        return getProperty(REPOSITORY_PROPERTY, DEFAULT_REPOSITORY_NAME);
    }

    /**
     * Sets the database backend used for VCS unit tests.
     */
    public static void setDatabaseForTests(String className) {
        try {
            DATABASE = (DatabaseHelper) Class.forName(className).newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError("Database class not found: " + className);
        }
    }

    /**
     * Gets a database connection, retrying if the server says it's overloaded.
     *
     * @since 5.9.3
     */
    public static Connection getConnection(String url, String user, String password) throws SQLException {
        return JDBCUtils.getConnection(url, user, password);
    }

    /**
     * Executes one statement on all the tables in a database.
     */
    public static void doOnAllTables(Connection connection, String catalog, String schemaPattern, String statement)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        List<String> tableNames = new LinkedList<String>();
        Set<String> truncateFirst = new HashSet<String>();
        try (ResultSet rs = metadata.getTables(catalog, schemaPattern, "%", new String[] { "TABLE" })) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (tableName.indexOf('$') != -1) {
                    // skip Oracle 10g flashback/fulltext-index tables
                    continue;
                }
                if (tableName.toLowerCase().startsWith("trace_xe_")) {
                    // Skip mssql 2012 system table
                    continue;
                }
                if ("ACLR_USER_USERS".equals(tableName)) {
                    // skip nested table that is dropped by the main table
                    continue;
                }
                if ("ANCESTORS_ANCESTORS".equals(tableName)) {
                    // skip nested table that is dropped by the main table
                    continue;
                }
                if ("ACLR_MODIFIED".equals(tableName) && DATABASE instanceof DatabaseOracle) {
                    // global temporary table on Oracle, must TRUNCATE before DROP
                    truncateFirst.add(tableName);
                }
                tableNames.add(tableName);
            }
            // not all databases can cascade on drop
            // remove hierarchy last because of foreign keys
            if (tableNames.remove("HIERARCHY")) {
                tableNames.add("HIERARCHY");
            }
            // needed for Azure
            if (tableNames.remove("NXP_LOGS")) {
                tableNames.add("NXP_LOGS");
            }
            if (tableNames.remove("NXP_LOGS_EXTINFO")) {
                tableNames.add("NXP_LOGS_EXTINFO");
            }
            // PostgreSQL is lowercase
            if (tableNames.remove("hierarchy")) {
                tableNames.add("hierarchy");
            }
            try (Statement st = connection.createStatement()) {
                for (String tableName : tableNames) {
                    if (truncateFirst.contains(tableName)) {
                        String sql = String.format("TRUNCATE TABLE \"%s\"", tableName);
                        executeSql(st, sql);
                    }
                    String sql = String.format(statement, tableName);
                    executeSql(st, sql);
                }
            }
        }
    }

    protected static void executeSql(Statement st, String sql) throws SQLException {
        log.trace("SQL: " + sql);
        st.execute(sql);
    }

    public void setUp(FeaturesRunner runner) throws Exception {
        setOwner(runner);
        setDatabaseName(DEFAULT_DATABASE_NAME);
        setRepositoryName(DEFAULT_REPOSITORY_NAME);
        setBinaryManager(defaultBinaryManager, "");
        setFulltextMode(false, fulltextAnalyzer(), false);
        setSingleDataSourceMode();
        setProperties();
        Class.forName(getProperty(DRIVER_PROPERTY));
        Framework.addListener(new RuntimeServiceListener() {

            @Override
            public void handleEvent(RuntimeServiceEvent event) {
                if (RuntimeServiceEvent.RUNTIME_STOPPED == event.id) {
                    try {
                        tearDown();
                    } catch (SQLException cause) {
                        throw new AssertionError("Cannot teardown database", cause);
                    }
                }
            }
        });
    }

    protected void setProperties() {

    }

    public void initDatabase(Connection connection) throws Exception {

    }

    protected String fulltextAnalyzer() {
        return "none";
    }

    protected void setOwner(FeaturesRunner runner) {
        if (owner != null) {
            throw new Error("Second call to setUp() without tearDown() on " + runner.getDescription(), owner);
        }
        owner = new Error("Database not released on " + runner.getDescription());
    }

    /**
     * @throws SQLException
     */
    public void tearDown() throws SQLException {
        owner = null;
    }

    public static void setBinaryManager(Class<? extends BinaryManager> binaryManagerClass, String key) {
        setProperty("nuxeo.test.vcs.binary-manager", binaryManagerClass.getName());
        setProperty("nuxeo.test.vcs.binary-manager-key", key);
    }

    public static void setFulltext(boolean disabled, String analyzerOptions, boolean searchDisabled) {
        setProperty("nuxeo.test.vcs.fulltext.disabled", Boolean.toString(disabled));
        setProperty("nuxeo.test.vcs.fulltext.analyzer", analyzerOptions);
        setProperty("nuxeo.text.vcs.fulltext.search.disabled", Boolean.toString(searchDisabled));
    }

    public static void setDatasourceURL(String value) {
        setProperty(URL_PROPERTY, value);
    }

    public String getDeploymentContrib() {
        return "OSGI-INF/test-repository-contrib.xml";
    }

    public abstract RepositoryDescriptor getRepositoryDescriptor();

    public static void setSingleDataSourceMode() {
        if (Boolean.parseBoolean(Framework.getProperty(SINGLEDS_PROPERTY)) || SINGLEDS_DEFAULT) {
            // the name doesn't actually matter, as code in
            // ConnectionHelper.getDataSource ignores it and uses
            // nuxeo.test.vcs.url etc. for connections in test mode
            String dataSourceName = "jdbc/NuxeoTestDS";
            Framework.getProperties().setProperty(ConnectionHelper.SINGLE_DS, dataSourceName);
        }
    }

    /**
     * For databases that do asynchronous fulltext indexing, sleep a bit.
     */
    public void sleepForFulltext() {
    }

    /**
     * For databases that don't have subsecond resolution, sleep a bit to get to the next second.
     */
    public void maybeSleepToNextSecond() {
        if (!hasSubSecondResolution()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
    }

    /**
     * For databases that don't have subsecond resolution, like MySQL.
     */
    public boolean hasSubSecondResolution() {
        return true;
    }

    /**
     * For databases that fail to cascade deletes beyond a certain depth.
     */
    public int getRecursiveRemovalDepthLimit() {
        return 0;
    }

    /**
     * For databases that don't support clustering.
     */
    public boolean supportsClustering() {
        return false;
    }

    public boolean supportsMultipleFulltextIndexes() {
        return true;
    }

    public boolean supportsXA() {
        return true;
    }

    public boolean supportsSoftDelete() {
        return false;
    }

    /**
     * Whether this database supports "sequence" as an id type.
     *
     * @since 5.9.3
     */
    public boolean supportsSequenceId() {
        return false;
    }

    public boolean supportsArrayColumns() {
        return false;
    }

    protected void setProperties2() {
        throw new UnsupportedOperationException();
    }

}
