/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
package org.nuxeo.runtime.test.runner;

import org.junit.runners.model.FrameworkMethod;
import org.slf4j.MDC;

import com.google.inject.Binder;

@Features(LoggingFeature.class)
public class MDCFeature implements RunnerFeature {

    protected static final String F_TEST = "fTest";

    protected static final String F_SUITE = "fSuite";

    protected static final String F_STATE = "fState";

    public MDCFeature() {
        super();
    }

    @Override
    public void initialize(FeaturesRunner runner) throws Exception {
        MDC.put(F_STATE, "initialize");
    }

    @Override
    public void configure(FeaturesRunner runner, Binder binder) {
        MDC.put(F_STATE, "configure");
    }

    @Override
    public void beforeRun(FeaturesRunner runner) throws Exception {
        MDC.put(F_STATE, "beforeRun");
    }

    @Override
    public void afterRun(FeaturesRunner runner) throws Exception {
        MDC.put(F_STATE, "afterRun");
    }

    @Override
    public void start(FeaturesRunner runner) throws Exception {
        MDC.put(F_STATE, "start");
    }

    @Override
    public void testCreated(Object test) throws Exception {
        MDC.put(F_STATE, "testCreated");
        MDC.put(F_SUITE, test.getClass().getName());
    }

    @Override
    public void stop(FeaturesRunner runner) throws Exception {
        MDC.clear();
    }

    @Override
    public void beforeSetup(FeaturesRunner runner) throws Exception {
        MDC.put(F_STATE, "beforeSetup");
    }

    @Override
    public void afterTeardown(FeaturesRunner runner) throws Exception {
        MDC.put(F_STATE, "afterTeardown");
    }

    @Override
    public void beforeMethodRun(FeaturesRunner runner, FrameworkMethod method, Object test) throws Exception {
        MDC.put(F_STATE, "beforeMethodRun");
        MDC.put(F_TEST, method.getMethod().getName());
    }

    @Override
    public void afterMethodRun(FeaturesRunner runner, FrameworkMethod method, Object test) throws Exception {
        MDC.put(F_STATE, "afterMethodRun");
        MDC.remove(F_TEST);
    }

    public void put(String key, String value) {
        MDC.put(key, value);
    }

    public void remove(String key) {
        MDC.remove(key);
    }

}
