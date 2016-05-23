/*
 * (C) Copyright 2006-2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.ecm.core.redis;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisMonitor;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.util.Pool;

public class RedisPoolExecutor implements RedisExecutor {

    private static final Log log = LogFactory.getLog(RedisPoolExecutor.class);
    private Thread monitorThread;
    protected Pool<Jedis> pool;

    protected final boolean supportPipeline;

    public RedisPoolExecutor(Pool<Jedis> pool) {
        this(pool, true);
    }

    public RedisPoolExecutor(Pool<Jedis> pool, boolean supportPipeline) {
        this.pool = pool;
        this.supportPipeline = supportPipeline;
    }

    @Override
    public <T> T execute(RedisCallable<T> callable) throws JedisException {
        if (monitorThread != null) {
            log.debug(String.format("Redis pool state before getting a conn: active: %d, idle: %s",
                    pool.getNumActive(), pool.getNumIdle()));
        }
        Jedis jedis = pool.getResource();
        if (monitorThread != null) {
            log.debug("Using conn: " + jedis.getClient().getSocket().getLocalPort());
        }
        boolean brokenResource = false;
        try {
            return callable.call(jedis);
        } catch (JedisConnectionException cause) {
            brokenResource = true;
            throw cause;
        } finally {
            // a disconnected resournce must be marked as broken
            // this happens when the monitoring is stopped
            if (brokenResource || !jedis.isConnected()) {
                pool.returnBrokenResource(jedis);
            } else {
                pool.returnResource(jedis);
            }
        }

    }

    @Override
    public Pool<Jedis> getPool() {
        return pool;
    }

    @Override
    public boolean supportPipelined() {
        return supportPipeline;
    }

    @Override
    public void startMonitor() {
        monitorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                log.debug("Starting monitor thread");
                execute(jedis -> {
                    jedis.monitor(new JedisMonitor() {
                        public void onCommand(String command) {
                            if (Thread.currentThread().isInterrupted()) {
                                // The only way to get out of this thread
                                jedis.disconnect();
                            } else {
                                log.debug(command);
                            }
                        }
                    });
                    log.debug("Monitor thread stopped");
                    return null;
                });
            }
        });
        monitorThread.setName("Nuxeo-Redis-Monitor");
        monitorThread.start();
    }

    @Override
    public void stopMonitor() {
        if (monitorThread != null) {
            log.debug("Stoping monitor");
            monitorThread.interrupt();
            monitorThread = null;
        }
    }

}
