/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Thomas Roger
 *     Yannis JULIENNE
 */
package org.nuxeo.functionaltests;

import static org.nuxeo.functionaltests.AbstractTest.NUXEO_URL;
import static org.nuxeo.functionaltests.Constants.ADMINISTRATOR;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.nuxeo.client.api.NuxeoClient;
import org.nuxeo.client.api.objects.Document;
import org.nuxeo.client.api.objects.acl.ACE;
import org.nuxeo.client.api.objects.user.Group;
import org.nuxeo.client.internals.spi.NuxeoClientException;

import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * @since 8.3
 */
public class RestHelper {

    private static final NuxeoClient CLIENT = new NuxeoClient(NUXEO_URL, ADMINISTRATOR, ADMINISTRATOR);

    private static final String USER_WORKSPACE_PATH_FORMAT = "/default-domain/UserWorkspaces/%s";

    private static final List<String> documentIdsToDelete = new ArrayList<>();

    private static final List<String> documentPathsToDelete = new ArrayList<>();

    private static final List<String> usersToDelete = new ArrayList<>();

    private static final List<String> groupsToDelete = new ArrayList<>();

    protected static final Log log = LogFactory.getLog(RestHelper.class);

    //@yannis : temporary fix for setting user password before JAVACLIENT-91
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RestHelper() {
        // helper class
    }

    public static void cleanup() {
        cleanupDocuments();
        cleanupUsers();
        cleanupGroups();
    }

    public static void cleanupDocuments() {
        documentIdsToDelete.forEach(RestHelper::deleteDocument);
        documentIdsToDelete.clear();
        documentPathsToDelete.clear();
    }

    public static void cleanupUsers() {
        for (String user : usersToDelete) {
            try{
                RestHelper.deleteDocument(String.format(USER_WORKSPACE_PATH_FORMAT, user));
            }catch(NuxeoClientException e){
                log.warn("User workspace not deleted for "+user+" (propably not found)");
            }
        }
        usersToDelete.forEach(RestHelper::deleteUser);
        usersToDelete.clear();
    }

    public static void cleanupGroups() {
        groupsToDelete.forEach(RestHelper::deleteGroup);
        groupsToDelete.clear();
    }

    public static String createUser(String username, String password) {
        return createUser(username, password, null, null, null, null, null);
    }

    public static String createUser(String username, String password, String firstName, String lastName,
            String company, String email, String group) {
        //@yannis : temporary fix for setting user password before JAVACLIENT-91
        String json = buildUserJSON(username, password, firstName, lastName, company, email, group);

        Response response = CLIENT.post(AbstractTest.NUXEO_URL+"/api/v1/user", json);
        if (!response.isSuccessful()) {
            throw new RuntimeException(String.format("Unable to create user '%s'", username));
        }

        try (ResponseBody responseBody = response.body()) {
            JsonNode jsonNode = MAPPER.readTree(responseBody.charStream());
            String id = jsonNode.get("id").getTextValue();
            usersToDelete.add(id);
            return id;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String buildUserJSON(String username, String password, String firstName, String lastName,
            String company, String email, String group) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"entity-type\": \"user\"").append(",\n");
        sb.append("\"id\": \"").append(username).append("\",\n");
        sb.append("\"properties\": {").append("\n");
        if (firstName != null) {
            sb.append("\"firstName\": \"").append(firstName).append("\",\n");
        }
        if (lastName != null) {
            sb.append("\"lastName\": \"").append(lastName).append("\",\n");
        }
        if (email != null) {
            sb.append("\"email\": \"").append(email).append("\",\n");
        }
        if (company != null) {
            sb.append("\"company\": \"").append(company).append("\",\n");
        }
        if (group != null) {
            sb.append("\"groups\": [\"").append(group).append("\"]").append(",\n");
        }
        sb.append("\"username\": \"").append(username).append("\",\n");
        sb.append("\"password\": \"").append(password).append("\"\n");
        sb.append("}").append("\n");
        sb.append("}");
        return sb.toString();
    }

    public static void deleteUser(String username) {
        CLIENT.getUserManager().deleteUser(username);
    }

    public static void createGroup(String name, String label) {
        createGroup(name, label, null, null);
    }

    public static void createGroup(String name, String label, String[] members, String[] subGroups) {
        Group group = new Group();
        group.setGroupName(name);
        group.setGroupLabel(label);
        if (members != null) {
            group.setMemberUsers(Arrays.asList(members));
        }
        if (subGroups != null) {
            group.setMemberGroups(Arrays.asList(subGroups));
        }

        CLIENT.getUserManager().createGroup(group);
        groupsToDelete.add(name);
    }

    public static void deleteGroup(String name) {
        CLIENT.getUserManager().deleteGroup(name);
    }

    public static String createDocument(String idOrPath, String type, String title, String description) {
        Document document = new Document(title, type);
        Map<String, Object> properties = new HashMap<>();
        properties.put("dc:title", title);
        if (description != null) {
            properties.put("dc:description", description);
        }
        document.setProperties(properties);

        if (idOrPath.startsWith("/")) {
            document = CLIENT.repository().createDocumentByPath(idOrPath, document);
        } else {
            document = CLIENT.repository().createDocumentById(idOrPath, document);
        }

        String docId = document.getId();
        String docPath = document.getPath();
        // do we already have to delete one parent?
        if (documentPathsToDelete.stream().noneMatch(docPath::startsWith)) {
            documentIdsToDelete.add(docId);
            documentPathsToDelete.add(docPath);
        }
        return docId;
    }

    public static void deleteDocument(String idOrPath) {
        // TODO change that by proper deleteDocument(String)
        if (idOrPath.startsWith("/")) {
            CLIENT.repository().deleteDocument(CLIENT.repository().fetchDocumentByPath(idOrPath));
        } else {
            CLIENT.repository().deleteDocument(CLIENT.repository().fetchDocumentById(idOrPath));
        }
    }

    public static void addPermission(String idOrPath, String username, String permission) {
        Document document;
        if (idOrPath.startsWith("/")) {
            document = CLIENT.repository().fetchDocumentByPath(idOrPath);
        } else {
            document = CLIENT.repository().fetchDocumentById(idOrPath);
        }

        ACE ace = new ACE();
        ace.setUsername(username);
        ace.setPermission(permission);

        //@yannis : temporary fix for setting permission before JAVACLIENT-90 is done
        Calendar beginDate = Calendar.getInstance();
        ace.setBegin(beginDate);
        Calendar endDate = Calendar.getInstance();
        endDate.add(Calendar.YEAR, 1);
        ace.setEnd(endDate);

        document.addPermission(ace);
    }

    public static void removePermissions(String idOrPath, String username) {
        Document document;
        if (idOrPath.startsWith("/")) {
            document = CLIENT.repository().fetchDocumentByPath(idOrPath);
        } else {
            document = CLIENT.repository().fetchDocumentById(idOrPath);
        }

        document.removePermission(username);
    }

}