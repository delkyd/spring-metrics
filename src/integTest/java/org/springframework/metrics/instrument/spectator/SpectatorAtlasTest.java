/**
 * Copyright 2017 Pivotal Software, Inc.
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
package org.springframework.metrics.instrument.spectator;

import com.netflix.servo.publish.*;
import com.netflix.servo.publish.atlas.AtlasMetricObserver;
import com.netflix.servo.publish.atlas.ServoAtlasConfig;
import com.netflix.servo.tag.BasicTagList;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.LongTaskTimer;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.servo.ServoRegistry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * This is the original support Netflix added for getting Spectator metrics to Atlas (through Servo).
 *
 * Requires a running atlas server on localhost:7101
 */
class SpectatorAtlasTest {

    private static MetricObserver rateTransform(MetricObserver observer) {
        final long pollInterval = 10;
        final long heartbeat = 2 * pollInterval;
        return new CounterToRateMetricTransform(observer, heartbeat, TimeUnit.SECONDS);
    }

    private static MetricObserver createFileObserver(File dir) {
        if (!dir.mkdirs() && !dir.isDirectory())
            throw new IllegalStateException("failed to create metrics directory: " + dir);
        return rateTransform(new FileMetricObserver("servo-example", dir));
    }

    private static MetricObserver createAtlasObserver() {
        return new AtlasMetricObserver(new ServoAtlasConfig() {
            @Override
            public String getAtlasUri() {
                return "http://localhost:7101/api/v1/publish";
            }

            @Override
            public int getPushQueueSize() {
                return 1000;
            }

            @Override
            public boolean shouldSendMetrics() {
                return true;
            }

            @Override
            public int batchSize() {
                return 1000;
            }
        }, BasicTagList.EMPTY);
    }

    @DisplayName("Just trying to understand how different meter primitives get reported to the backend")
    @Test
    @Disabled
    void variousBasicMeters() throws InterruptedException {
        PollRunnable task = new PollRunnable(new MonitorRegistryMetricPoller(),
                BasicMetricFilter.MATCH_ALL,
                true,
                Arrays.asList(
                        createFileObserver(new File("metrics")),
                        createAtlasObserver()
                )
            );

        PollScheduler scheduler = PollScheduler.getInstance();
        scheduler.start();
        scheduler.addPoller(task, 10, TimeUnit.SECONDS);

        ServoRegistry registry = new ServoRegistry();
        Spectator.globalRegistry().add(registry);

        LongTaskTimer ltt = registry.longTaskTimer("myTimer");
        long id = ltt.start();

        Counter counter = registry.counter("myCounter");
        counter.increment();

        Timer timer = registry.timer("myShortTimer");
        timer.record(10, TimeUnit.SECONDS);

        Thread.sleep(20000);
    }
}
