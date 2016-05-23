/*
 * (C) Copyright 2006-2012 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Benjamin JALON <bjalon@nuxeo.com>
 */
package org.nuxeo.ecm.automation.core.test;

import static junit.framework.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.features.PlatformFunctions;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @author <a href="mailto:bjalon@nuxeo.com">Benjamin JALON</a>
 * @since 5.7
 */
@RunWith(FeaturesRunner.class)
public class PlatformFunctionTest {

    List<String> listOfString = Arrays.asList(new String[] { "value list 1", "value list 2" });

    List<Integer> listOfInteger = Arrays.asList(new Integer[] { new Integer(-1), new Integer(-2) });

    String[] arrayOfString = new String[] { "value list 1", "value list 2" };

    Integer[] arrayOfInteger = new Integer[] { new Integer(10), new Integer(11), };

    List<Object> listOfObjects = new ArrayList<Object>();

    Integer intValue1 = new Integer(1);

    Integer intValue2 = new Integer(2);

    String stringValue1 = "value 1";

    String stringValue2 = "value 2";

    private PlatformFunctions pf;

    @Before
    public void setup() {
        pf = new PlatformFunctions();

    }

    @Test
    public void shouldConcatenateWithStringScalar() {
        List<String> result = pf.concatenateValuesAsNewList(listOfString, stringValue1);
        assertEquals(3, result.size());
        assertEquals("value list 1", result.get(0));
        assertEquals("value list 2", result.get(1));
        assertEquals("value 1", result.get(2));
    }

    @Test
    public void shouldConcatenateWithIntegerScalar() {
        List<Integer> result = pf.concatenateValuesAsNewList(listOfInteger, intValue1);
        assertEquals(3, result.size());
        assertEquals(new Integer(-1), result.get(0));
        assertEquals(new Integer(-2), result.get(1));
        assertEquals(new Integer(1), result.get(2));
    }

    @Test
    public void shouldConcatenateListString() {
        List<String> result = pf.concatenateValuesAsNewList(listOfString, arrayOfString, stringValue1);
        assertEquals(5, result.size());
        assertEquals("value list 1", result.get(0));
        assertEquals("value list 2", result.get(1));
        assertEquals("value list 1", result.get(2));
        assertEquals("value list 2", result.get(3));
        assertEquals("value 1", result.get(4));
    }

    @Test
    public void shouldConcatenateListInteger() {
        List<Integer> result = pf.concatenateValuesAsNewList(listOfInteger, arrayOfInteger, intValue1);
        assertEquals(5, result.size());
        assertEquals(new Integer(-1), result.get(0));
        assertEquals(new Integer(-2), result.get(1));
        assertEquals(new Integer(10), result.get(2));
        assertEquals(new Integer(11), result.get(3));
        assertEquals(new Integer(1), result.get(4));
    }
}
