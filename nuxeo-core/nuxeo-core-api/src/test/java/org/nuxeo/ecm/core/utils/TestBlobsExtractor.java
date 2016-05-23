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

package org.nuxeo.ecm.core.utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.runtime.test.NXRuntimeTestCase;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.LocalDeploy;

/**
 * @author Florent Guillaume
 */
@Deploy({"org.nuxeo.ecm.core.schema","org.nuxeo.ecm.core.api"})
@LocalDeploy("org.nuxeo.ecm.core.schema:OSGI-INF/test-blobsextractor-types-contrib.xml")
public class TestBlobsExtractor extends NXRuntimeTestCase {

    @Test
    public void test() throws Exception {
        DocumentModel doc = new DocumentModelImpl("/", "doc", "ComplexDoc");

        List<Map<String, Object>> vignettes = new ArrayList<Map<String, Object>>();
        Map<String, Object> vignette = new HashMap<String, Object>();
        vignette.put("width", Long.valueOf(0));
        vignette.put("height", Long.valueOf(0));
        Blob blob1 = Blobs.createBlob("foo1 bar1");
        blob1.setFilename("file1.txt");
        vignette.put("content", blob1);
        vignettes.add(vignette);

        vignette = new HashMap<String, Object>();
        vignette.put("width", Long.valueOf(0));
        vignette.put("height", Long.valueOf(0));
        Blob blob2 = Blobs.createBlob("foo2 bar2");
        blob2.setFilename("file2.txt");
        vignette.put("content", blob2);
        vignettes.add(vignette);

        Map<String, Object> attachedFile = new HashMap<String, Object>();
        attachedFile.put("name", "some name");
        attachedFile.put("vignettes", vignettes);
        doc.setPropertyValue("cmpf:attachedFile", (Serializable) attachedFile);

        Blob blob3 = Blobs.createBlob("foo3 bar3");
        doc.setProperty("file", "content", blob3);

        BlobsExtractor extractor = new BlobsExtractor();
        List<Blob> blobs = extractor.getBlobs(doc);
        assertEquals(3, blobs.size());
        assertTrue(blobs.contains(blob1));
        assertTrue(blobs.contains(blob2));
        assertTrue(blobs.contains(blob3));
    }

    @Test
    public void testWithRepositoryConfiguration() throws Exception {
        DocumentModel doc = new DocumentModelImpl("/", "doc", "ComplexDoc");

        List<Map<String, Object>> vignettes = new ArrayList<Map<String, Object>>();
        Map<String, Object> vignette = new HashMap<String, Object>();
        vignette.put("width", Long.valueOf(0));
        vignette.put("height", Long.valueOf(0));
        Blob blob1 = Blobs.createBlob("foo1 bar1");
        blob1.setFilename("file1.txt");
        vignette.put("content", blob1);
        vignettes.add(vignette);

        vignette = new HashMap<String, Object>();
        vignette.put("width", Long.valueOf(0));
        vignette.put("height", Long.valueOf(0));
        Blob blob2 = Blobs.createBlob("foo2 bar2");
        blob2.setFilename("file2.txt");
        vignette.put("content", blob2);
        vignettes.add(vignette);

        Map<String, Object> attachedFile = new HashMap<String, Object>();
        attachedFile.put("name", "some name");
        attachedFile.put("vignettes", vignettes);
        doc.setPropertyValue("cmpf:attachedFile", (Serializable) attachedFile);

        Blob blob3 = Blobs.createBlob("foo3 bar3");
        doc.setProperty("file", "content", blob3);

        BlobsExtractor extractor = new BlobsExtractor();
        List<Blob> blobs;

        /*
         * First configuration : only a simple property <index> <field>dc:title</field> </index>
         */
        extractor.setExtractorProperties(null, null, false);
        blobs = extractor.getBlobs(doc);
        assertEquals(0, blobs.size());

        /*
         * Second configuration : only blobs property <index> <fieldType>blob</fieldType> </index>
         */
        extractor.setExtractorProperties(null, null, true);
        blobs = extractor.getBlobs(doc);
        assertEquals(3, blobs.size());
        assertTrue(blobs.contains(blob1));
        assertTrue(blobs.contains(blob2));
        assertTrue(blobs.contains(blob3));

        /*
         * Third configuration : only a blob property whose schema has a prefix <index>
         * <field>cmpf:attachedFile/vignettes//content/data</field> </index>
         */
        Set<String> pathProps = new HashSet<String>();
        pathProps.add("cmpf:attachedFile/vignettes/*/content/data");
        extractor.setExtractorProperties(pathProps, null, false);
        blobs = extractor.getBlobs(doc);
        assertEquals(2, blobs.size());
        assertTrue(blobs.contains(blob1));
        assertTrue(blobs.contains(blob2));

        /*
         * Fourth configuration : only the blob of file whose schema doesn't have a prefix <index>
         * <field>content/data</field> </index>
         */
        pathProps = new HashSet<String>();
        pathProps.add("content/data");
        extractor.setExtractorProperties(pathProps, null, false);
        blobs = extractor.getBlobs(doc);
        assertEquals(1, blobs.size());
        assertTrue(blobs.contains(blob3));

        /*
         * Fifth configuration : all blobs minus some blobs <index> <fieldType>blob</fieldType>
         * <excludeField>content/data</excludeField> </index>
         */
        pathProps = new HashSet<String>();
        pathProps.add("content/data");
        extractor.setExtractorProperties(null, pathProps, true);
        blobs = extractor.getBlobs(doc);
        assertEquals(2, blobs.size());
        assertTrue(blobs.contains(blob2));

    }

}
