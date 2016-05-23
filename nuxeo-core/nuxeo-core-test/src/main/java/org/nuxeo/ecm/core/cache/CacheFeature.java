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
 *     mhilaire
 *
 */

package org.nuxeo.ecm.core.cache;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Assume;
import org.nuxeo.ecm.core.cache.Cache;
import org.nuxeo.ecm.core.cache.CacheAttributesChecker;
import org.nuxeo.ecm.core.cache.CacheService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.SimpleFeature;

import com.google.inject.Binder;
import com.google.inject.Provider;
import com.google.inject.name.Names;

@Features(CoreFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class)
@Deploy({ "org.nuxeo.ecm.core.cache" })
public class CacheFeature extends SimpleFeature {

    public static final String DEFAULT_TEST_CACHE_NAME = "default-test-cache";

    public static final String KEY = "key1";

    public static final String VAL = "val1";

    boolean enabled = false;

    public void enable() {
        enabled = true;
    }

    @Override
    public void start(FeaturesRunner runner) throws Exception {
        Assume.assumeTrue(enabled);
    }

    @Override
    public void configure(final FeaturesRunner runner, Binder binder) {
        bindCache(binder, DEFAULT_TEST_CACHE_NAME);
    }

    protected void bindCache(Binder binder, final String name) {
        binder.bind(Cache.class).annotatedWith(Names.named(name)).toProvider(new Provider<Cache>() {

            @Override
            public Cache get() {
                return Framework.getService(CacheService.class).getCache(name);
            }

        });
    }

    @Override
    public void beforeSetup(FeaturesRunner runner) throws Exception {
        Framework.getService(CacheService.class).getCache(DEFAULT_TEST_CACHE_NAME).put(KEY, VAL);
    }

    @Override
    public void afterTeardown(FeaturesRunner runner) throws IOException {
        IOException errors = new IOException("Check suppressed errors for cache cleanup");
        clearCache(errors, DEFAULT_TEST_CACHE_NAME);
        if (errors.getSuppressed().length > 0) {
            throw errors;
        }
    }

    protected void clearCache(IOException errors, String name) {
        Framework.getService(CacheService.class).getCache(name).invalidateAll();
    }

    public static <T extends Cache> T unwrapImpl(Class<T> type, Cache cache) {
        if (!(cache instanceof CacheAttributesChecker)) {
            Assert.fail("Not an attribute checker " + cache.getClass());
        }
        cache = ((CacheAttributesChecker) cache).getCache();
        if (!type.isAssignableFrom(cache.getClass())) {
            Assert.fail("Not of requested type  " + type);
        }
        return type.cast(cache);
    }
}
