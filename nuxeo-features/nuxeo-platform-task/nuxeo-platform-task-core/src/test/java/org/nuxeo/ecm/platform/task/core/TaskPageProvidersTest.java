/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.platform.task.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.task.TaskService;
import org.nuxeo.ecm.platform.task.dashboard.DashBoardItem;
import org.nuxeo.ecm.platform.task.providers.UserTaskPageProvider;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.test.runner.LocalDeploy;

/**
 * @since 5.4.2
 */
@LocalDeploy({ "org.nuxeo.ecm.platform.task.core:OSGI-INF/pageproviders-contrib.xml" })
public class TaskPageProvidersTest extends TaskTestCase {

    @Inject
    protected CoreSession session;

    @Inject
    protected TaskService taskService;

    @Inject
    protected PageProviderService ppService;

    @Inject
    protected UserManager userManager;

    protected NuxeoPrincipal administrator;

    protected DocumentModel document;

    @Before
    public void setUp() throws Exception {
        administrator = userManager.getPrincipal(SecurityConstants.ADMINISTRATOR);
        document = getDocument();

        // create isolated task
        List<String> actors = new ArrayList<String>();
        actors.add(NuxeoPrincipal.PREFIX + administrator.getName());
        actors.add(NuxeoGroup.PREFIX + SecurityConstants.MEMBERS);
        Calendar calendar = Calendar.getInstance();
        calendar.set(2006, 6, 6);
        // create one task
        taskService.createTask(session, administrator, document, "Test Task Name", actors, false, "test directive",
                "test comment", calendar.getTime(), null, null);
        // create another task to check pagination
        taskService.createTask(session, administrator, document, "Test Task Name 2", actors, false, "test directive",
                "test comment", calendar.getTime(), null, null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testTaskPageProvider() throws Exception {
        Map<String, Serializable> properties = new HashMap<String, Serializable>();
        properties.put(UserTaskPageProvider.CORE_SESSION_PROPERTY, (Serializable) session);
        PageProvider<DashBoardItem> taskProvider = (PageProvider<DashBoardItem>) ppService
                .getPageProvider("current_user_tasks", null, null, null, properties, (Object[]) null);
        List<DashBoardItem> tasks = taskProvider.getCurrentPage();
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        // check first single task
        DashBoardItem task = tasks.get(0);
        assertNotNull(task.getStartDate());
        // There is no sort order, we can not assert which one is the first
        assertTrue(task.getName(), task.getName().startsWith("Test Task Name"));
        assertEquals("test comment", task.getComment());
        assertNull(task.getDescription());
        assertEquals("test directive", task.getDirective());
        assertEquals(document.getRef(), task.getDocRef());
        assertEquals(document, task.getDocument());
        assertNotNull(task.getDueDate());
        assertNotNull(task.getStartDate());
        assertFalse(taskProvider.isPreviousPageAvailable());
        assertTrue(taskProvider.isNextPageAvailable());
        taskProvider.nextPage();
        tasks = taskProvider.getCurrentPage();
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        // check second single task
        task = tasks.get(0);

        assertEquals("test comment", task.getComment());
        assertNull(task.getDescription());
        assertEquals("test directive", task.getDirective());
        assertEquals(document.getRef(), task.getDocRef());
        assertEquals(document, task.getDocument());
        assertNotNull(task.getDueDate());
        assertNotNull(task.getStartDate());
        assertTrue(task.getName(), task.getName().startsWith("Test Task Name"));
        assertTrue(taskProvider.isPreviousPageAvailable());
        assertFalse(taskProvider.isNextPageAvailable());
    }

    @Test
    public void testTaskPageProviderSorting() {
        Map<String, Serializable> properties = new HashMap<>();
        properties.put(UserTaskPageProvider.CORE_SESSION_PROPERTY, (Serializable) session);
        PageProvider<DashBoardItem> taskProvider = (PageProvider<DashBoardItem>) ppService
                .getPageProvider("current_user_tasks_sort_asc", null, null, null, properties, (Object[]) null);
        List<DashBoardItem> tasks = taskProvider.getCurrentPage();
        assertNotNull(tasks);
        assertEquals("Test Task Name", tasks.get(0).getName());
        // Check task order
        taskProvider = (PageProvider<DashBoardItem>) ppService.getPageProvider("current_user_tasks_sort_desc", null,
                null, null, properties, (Object[]) null);
        tasks = taskProvider.getCurrentPage();
        assertNotNull(tasks);
        // Check task order update
        assertEquals("Test Task Name 2", tasks.get(0).getName());
    }

    protected DocumentModel getDocument() throws Exception {
        DocumentModel model = session.createDocumentModel(session.getRootDocument().getPathAsString(), "1", "File");
        DocumentModel doc = session.createDocument(model);
        assertNotNull(doc);

        session.saveDocument(doc);
        session.save();
        return doc;
    }

}
