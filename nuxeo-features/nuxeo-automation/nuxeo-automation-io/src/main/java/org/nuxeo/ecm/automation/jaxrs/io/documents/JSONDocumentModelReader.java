/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     dmetzler
 */
package org.nuxeo.ecm.automation.jaxrs.io.documents;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.nuxeo.ecm.automation.core.util.DocumentHelper;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.impl.DataModelImpl;
import org.nuxeo.ecm.core.api.impl.SimpleDocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.api.model.impl.ComplexProperty;
import org.nuxeo.ecm.core.api.model.impl.ListProperty;
import org.nuxeo.ecm.core.api.model.impl.primitives.BlobProperty;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.webengine.WebException;
import org.nuxeo.ecm.webengine.jaxrs.session.SessionFactory;

/**
 * JAX-RS reader for a DocumentModel. If an id is given, it tries to reattach
 * the document to the session. If not, it creates a ready to create
 * DocumentModel filled with the properties found.
 *
 * @since 5.7.2
 */
@Provider
@Consumes({ "application/json+nxentity", "application/json" })
public class JSONDocumentModelReader implements
        MessageBodyReader<DocumentModel> {

    // private static final String REQUEST_BATCH_ID = "batchId";

    protected static final Log log = LogFactory.getLog(JSONDocumentModelReader.class);

    @Context
    HttpServletRequest request;

    @Context
    JsonFactory factory;

    @Override
    public boolean isReadable(Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType) {
        return DocumentModel.class.isAssignableFrom(type);
    }

    @Override
    public DocumentModel readFrom(Class<DocumentModel> type, Type genericType,
            Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
            throws IOException, WebApplicationException {
        String content = IOUtils.toString(entityStream);
        if (content.isEmpty()) {
            if (content.isEmpty()) {
                throw new WebException("No content in request body",
                        Response.Status.BAD_REQUEST.getStatusCode());
            }

        }

        try {
            return readRequest(content, httpHeaders);
        } catch (Exception e) {
            throw WebException.wrap(e);
        }
    }

    private DocumentModel readRequest(String content,
            MultivaluedMap<String, String> httpHeaders) throws Exception {
        return readRequest(content, httpHeaders, request);
    }

    protected DocumentModel readRequest(String content,
            MultivaluedMap<String, String> httpHeaders,
            HttpServletRequest request) throws Exception {
        JsonParser jp = factory.createJsonParser(content);
        return readJson(jp, httpHeaders, request);
    }

    public static DocumentModel readJson(JsonParser jp,
            MultivaluedMap<String, String> httpHeaders,
            HttpServletRequest request) throws Exception {
        ReadDocumentJson readDocumentJson = new ReadDocumentJson(jp).invoke();
        String uid = readDocumentJson.getUid();
        SimpleDocumentModel simpleDoc = readDocumentJson.getSimpleDoc();

        // If a uid is specified, we try to get the doc from
        // the core session
        if (uid != null) {
            CoreSession session = SessionFactory.getSession(request);
            DocumentModel doc = session.getDocument(new IdRef(uid));
            avoidBlobUpdate(simpleDoc, doc);
            applyDirtyPropertyValues(simpleDoc, doc);
            return doc;
        } else if (type != null) {
            SimpleDocumentModel createdDoc = new SimpleDocumentModel();
            if (name != null) {
                createdDoc.setPathInfo(null, name);
            }
            createdDoc.setType(type);
            applyAllPropertyValues(simpleDoc, createdDoc);
            return createdDoc;
        } else {
            return simpleDoc;
        }
    }

    /**
     * @since 6.0 - HF11.
     */
    public static DocumentModel readJson(JsonParser jp, CoreSession session) throws Exception {

        ReadDocumentJson readDocumentJson = new ReadDocumentJson(jp).invoke();
        String uid = readDocumentJson.getUid();
        SimpleDocumentModel simpleDoc = readDocumentJson.getSimpleDoc();

        // If a uid is specified, we try to get the doc from
        // the core session
        if (uid != null) {
            DocumentModel doc = session.getDocument(new IdRef(uid));
            avoidBlobUpdate(simpleDoc, doc);
            applyDirtyPropertyValues(simpleDoc, doc);
            return doc;
        } else if (type != null) {
            SimpleDocumentModel createdDoc = new SimpleDocumentModel();
            if (name != null) {
                createdDoc.setPathInfo(null, name);
            }
            createdDoc.setType(type);
            applyAllPropertyValues(simpleDoc, createdDoc);
            return createdDoc;
        } else {
            return simpleDoc;
        }
    }

    /**
     * Avoid the blob updates. It's managed by custom ways.
     */
    private static void avoidBlobUpdate(DocumentModel docToClean, DocumentModel docRef) {
        for (String schema : docToClean.getSchemas()) {
            for (String field : docToClean.getDataModel(schema).getDirtyFields()) {
                avoidBlobUpdate(docToClean.getProperty(field), docRef);
            }
        }
    }

    private static void avoidBlobUpdate(Property propToClean, DocumentModel docRef) {
        if (propToClean instanceof BlobProperty) {
            // if the blob used to exist
            if (propToClean.getValue() == null) {
                Serializable value = docRef.getPropertyValue(propToClean.getPath());
                propToClean.setValue(value);
            }
        } else if (propToClean instanceof ComplexProperty) {
            ComplexProperty complexPropToClean = (ComplexProperty) propToClean;
            for (Field field : complexPropToClean.getType().getFields()) {
                Property childPropToClean = complexPropToClean.get(field.getName().getLocalName());
                avoidBlobUpdate(childPropToClean, docRef);
            }
        } else if (propToClean instanceof ListProperty) {
            ListProperty listPropToClean = (ListProperty) propToClean;
            for (int i = 0; i < listPropToClean.size(); i++) {
                Property elPropToClean = listPropToClean.get(i);
                avoidBlobUpdate(elPropToClean, docRef);
            }
        }
    }

    static Properties readProperties(JsonParser jp) throws Exception {
        JsonNode node = jp.readValueAsTree();
        return new Properties(node);

    }

    /**
     * Decodes a Serializable to make it a blob.
     *
     * @since 5.9.1
     */
    private static Serializable decodeBlob(Serializable data) {
        if (data instanceof Blob) {
            return data;
        } else {
            return null;
        }
    }

    /**
     * Check that a serialized data is not null.
     *
     * @since 5.9.1
     */
    private static boolean isNull(Serializable data) {
        return data == null || "null".equals(data);
    }

    public static void applyPropertyValues(DocumentModel src, DocumentModel dst) {
        applyPropertyValues(src, dst, true);
    }

    public static void applyPropertyValues(DocumentModel src, DocumentModel dst, boolean dirtyOnly) {
        // if not "dirty only", it handles all the schemas for the given type
        // so it will trigger the default values initialization
        if (dirtyOnly) {
            applyDirtyPropertyValues(src, dst);
        } else {
            applyAllPropertyValues(src, dst);
        }
    }

    public static void applyDirtyPropertyValues(DocumentModel src, DocumentModel dst) {
        String[] schemas = src.getSchemas();
        for (String schema : schemas) {
            DataModelImpl dataModel = (DataModelImpl) dst.getDataModel(schema);
            DataModelImpl fromDataModel = (DataModelImpl) src.getDataModel(schema);
            for (String field : fromDataModel.getDirtyFields()) {
                Serializable data = (Serializable) fromDataModel.getData(field);
                try {
                    if (!(dataModel.getDocumentPart().get(field) instanceof BlobProperty)) {
                        dataModel.setData(field, data);
                    } else {
                        dataModel.setData(field, decodeBlob(data));
                    }
                } catch (PropertyNotFoundException e) {
                    log.warn(String.format(
                            "Trying to deserialize unexistent field : {%s}",
                            field));
                }
            }
        }
    }

    public static void applyAllPropertyValues(DocumentModel src, DocumentModel dst) {
        SchemaManager service = Framework.getService(SchemaManager.class);
        DocumentType type = service.getDocumentType(src.getType());
        String[] schemas = type.getSchemaNames();
        for (String schemaName : schemas) {
            Schema schema = service.getSchema(schemaName);
            DataModelImpl dataModel = (DataModelImpl) dst.getDataModel(schemaName);
            DataModelImpl fromDataModel = (DataModelImpl) src.getDataModel(schemaName);
            for (Field field : schema.getFields()) {
                String fieldName = field.getName().getLocalName();
                Serializable data = (Serializable) fromDataModel.getData(fieldName);
                try {
                    if (!(dataModel.getDocumentPart().get(fieldName) instanceof BlobProperty)) {
                        dataModel.setData(fieldName, data);
                    } else {
                        dataModel.setData(fieldName, decodeBlob(data));
                    }
                } catch (PropertyNotFoundException e) {
                    log.warn(String.format(
                            "Trying to deserialize unexistent field : {%s}",
                            field));
                }
            }
        }
    }

    /**
     * @since 6.0 - HF11.
     */
    private static class ReadDocumentJson {

        private JsonParser jp;

        private SimpleDocumentModel simpleDoc;

        private String uid;

        public ReadDocumentJson(JsonParser jp) {
            this.jp = jp;
        }

        public SimpleDocumentModel getSimpleDoc() {
            return simpleDoc;
        }

        public String getUid() {
            return uid;
        }

        public ReadDocumentJson invoke() throws Exception {
            JsonToken tok = jp.nextToken();

            // skip {
            if (jp.getCurrentToken() == JsonToken.START_OBJECT) {
                tok = jp.nextToken();
            }
            simpleDoc = new SimpleDocumentModel();
            String type = null;
            String name = null;
            uid = null;
            while (tok != null && tok != JsonToken.END_OBJECT) {
                String key = jp.getCurrentName();
                jp.nextToken();
                if ("properties".equals(key)) {
                    DocumentHelper.setJSONProperties(null, simpleDoc,
                            readProperties(jp));
                } else if ("name".equals(key)) {
                    name = jp.readValueAs(String.class);
                } else if ("type".equals(key)) {
                    type = jp.readValueAs(String.class);
                } else if ("uid".equals(key)) {
                    uid = jp.readValueAs(String.class);
                } else if ("entity-type".equals(key)) {
                    String entityType = jp.readValueAs(String.class);
                    if (!"document".equals(entityType)) {
                        throw new WebApplicationException(
                                Response.Status.BAD_REQUEST);
                    }
                } else {
                    log.debug("Unknown key: " + key);
                    jp.skipChildren();
                }

                tok = jp.nextToken();
            }

            if (tok == null) {
                throw new IllegalArgumentException("Unexpected end of stream.");
            }

            if (StringUtils.isNotBlank(type)) {
                simpleDoc.setType(type);
            }

            if (StringUtils.isNotBlank(name)) {
                simpleDoc.setPathInfo(null, name);
            }
            return this;
        }
    }
}
