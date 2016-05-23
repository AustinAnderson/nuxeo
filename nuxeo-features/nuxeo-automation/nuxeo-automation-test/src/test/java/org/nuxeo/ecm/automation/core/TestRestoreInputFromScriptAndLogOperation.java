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
 *     Sun Seng David TAN <stan@nuxeo.com>
 */
package org.nuxeo.ecm.automation.core;

import javax.inject.Inject;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.operations.FetchContextDocument;
import org.nuxeo.ecm.automation.core.operations.LogOperation;
import org.nuxeo.ecm.automation.core.operations.RestoreDocumentInputFromScript;
import org.nuxeo.ecm.automation.core.operations.SetInputAsVar;
import org.nuxeo.ecm.automation.core.operations.document.FetchDocument;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LogCaptureFeature;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Testing RestoreDocumentInputFromScript and LogOperation operations.
 */
@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class, LogCaptureFeature.class })
@LogCaptureFeature.FilterWith(TestRestoreInputFromScriptAndLogOperation.MyLogFilter.class)
public class TestRestoreInputFromScriptAndLogOperation {
    @Inject
    AutomationService service;

    @Inject
    CoreSession session;

    @Inject
    LogCaptureFeature.Result logCaptureResult;

    public static class MyLogFilter implements LogCaptureFeature.Filter {

        @Override
        public boolean accept(ILoggingEvent event) {
            if (!event.getLevel().equals(Level.ERROR)) {
                return false;
            }
            if (!event.getLoggerName().equals("loggerName")) {
                return false;
            }
            return true;
        }

    }

    @Test
    public void testRestoreInput() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "test", "File");
        doc.setPropertyValue("dc:title", "test");
        doc = session.createDocument(doc);

        OperationContext ctx = new OperationContext(session);
        ctx.setInput(doc);

        OperationChain chain = new OperationChain("testSetObjectInput");

        chain.add(FetchContextDocument.ID);
        // put the document in the context
        chain.add(SetInputAsVar.ID).set("name", "test");
        // set the input with fetch /
        chain.add(FetchDocument.ID).set("value", "/");
        // use the new operation to restore the input
        chain.add(RestoreDocumentInputFromScript.ID).set("script", "Context[\"test\"]");
        chain.add(LogOperation.ID).set("category", "loggerName").set("message", "expr:Input title @{This.title}.").set(
                "level", "error");

        // assert that the output is "/test" is the one retrieved from the
        // context variable
        DocumentModel returnedDoc = (DocumentModel) service.run(ctx, chain);
        Assert.assertEquals("/test", returnedDoc.getPathAsString());
        // making sure that
        logCaptureResult.assertHasEvent();
    }
}
