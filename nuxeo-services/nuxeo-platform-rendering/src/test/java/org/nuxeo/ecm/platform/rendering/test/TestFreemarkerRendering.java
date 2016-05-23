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
 *     bstefanescu
 *
 * $Id$
 */

package org.nuxeo.ecm.platform.rendering.test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang.SystemUtils;
import org.junit.Before;
import org.junit.Test;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.impl.DataModelImpl;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.api.impl.blob.URLBlob;
import org.nuxeo.ecm.core.api.model.DocumentPart;
import org.nuxeo.ecm.core.schema.Prefetch;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.platform.rendering.fm.FreemarkerComponent;
import org.nuxeo.ecm.platform.rendering.fm.FreemarkerEngine;
import org.nuxeo.ecm.platform.rendering.wiki.WikiTransformer;
import org.nuxeo.ecm.platform.rendering.wiki.extensions.FreemarkerMacro;
import org.nuxeo.ecm.platform.rendering.wiki.extensions.PatternFilter;
import org.nuxeo.runtime.test.NXRuntimeTestCase;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.LocalDeploy;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.platform.rendering")
@LocalDeploy("org.nuxeo.ecm.platform.rendering:OSGI-INF/test-schema.xml")
public class TestFreemarkerRendering extends NXRuntimeTestCase {

    @Inject
    FreemarkerComponent component;
    FreemarkerEngine engine;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        engine = component.newEngine();
        engine.setResourceLocator(new MyResourceLocator());

        WikiTransformer tr = new WikiTransformer();
        tr.getSerializer().addFilter(new PatternFilter("[A-Z]+[a-z]+[A-Z][A-Za-z]*", "<link>$0</link>"));
        tr.getSerializer().addFilter(
                new PatternFilter("NXP-[0-9]+", "<a href=\"http://jira.nuxeo.org/browse/$0\">$0</a>"));
        tr.getSerializer().registerMacro(new FreemarkerMacro());
        engine.setSharedVariable("wiki", tr);
    }

    public static String getTestFile(String filePath) throws UnsupportedEncodingException {
        return FileUtils.getResourcePathFromContext(filePath);
    }

    @Test
    public void testRendering() throws Exception {
        DocumentModelImpl doc1 = new DocumentModelImpl(null, "File", null, new Path("/root/folder/wiki1"), null, null,
                null, new String[] { "dublincore", "file" }, null, null, "default");
        doc1.addDataModel(new DataModelImpl("dublincore"));
        DocumentPart documentPart = doc1.getPart("dublincore");
        documentPart.get("title").setValue("The dublincore title for doc1");
        documentPart.get("description").setValue("A descripton *with* wiki code and a WikiName");
        Blob blob = new URLBlob(TestFreemarkerRendering.class.getClassLoader().getResource(
                "testdata/blob.wiki"));
        doc1.getPart("dublincore").get("content").setValue(blob);
        // also add something prefetched (dm not loaded)
        Prefetch prefetch = new Prefetch();
        prefetch.put("filename", "file", "filename", "somefile");
        doc1.prefetch = prefetch;

        DocumentModelImpl doc2 = new DocumentModelImpl("/root/folder/wiki2", "Test Doc 2", "File");
        doc2.addDataModel(new DataModelImpl("dublincore"));
        doc2.getPart("dublincore").get("title").setValue("The dublincore title for doc1");
        engine.setSharedVariable("doc", doc2);

        StringWriter writer = new StringWriter();
        Map<String, Object> input = new HashMap<String, Object>();
        input.put("doc", doc1);

        // double s = System.currentTimeMillis();
        engine.render("testdata/c.ftl", input, writer);
        // double e = System.currentTimeMillis();

        InputStream expected = new FileInputStream(getTestFile("expecteddata/c_output.txt"));
        assertTextEquals(FileUtils.read(expected), writer.toString());

    }

    protected void assertTextEquals(String expected, String actual) {
        if (SystemUtils.IS_OS_WINDOWS) {
            // make tests pass under Windows
            expected = expected.trim();
            expected = expected.replace("\n", "");
            expected = expected.replace("\r", "");
            actual = actual.trim();
            actual = actual.replace("\n", "");
            actual = actual.replace("\r", "");
        }
        assertEquals(expected, actual);
    }

    @Test
    public void testUrlEscaping() throws Exception {
        StringWriter writer = new StringWriter();
        Map<String, Object> input = new HashMap<String, Object>();
        input.put("parameter", "\u00e9/");
        engine.render("testdata/url.ftl", input, writer);
        assertEquals("<p>http://google.com?q=%C3%A9%2F</p>", writer.toString());
    }
}
