/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package co.elastic.apm.agent.redis;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class RedisInstrumentation extends ElasticApmInstrumentation {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void beforeExecute(@Advice.Local("span") Span span,
                                      @Advice.Origin("#m") String methodName) {

        if (tracer == null || tracer.getActive() == null) {
            return;
        }

        final TraceContextHolder<?> parent = tracer.getActive();

        span = parent.createExitSpan();

        if (span != null) {
            span.withType("db")
                .withSubtype("redis")
                .withName(methodName.toUpperCase())
                .appendToName(" redis");
        }

        if (span != null) {
            span.activate();
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    private static void afterExecute(@Advice.Local("span") @Nullable Span span,
                                     @Advice.Thrown @Nullable Throwable t) {
        if (span != null) {
            try {
                span.captureException(t);
            } finally {
                span.deactivate().end();
            }
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("redis.clients.jedis.BinaryJedis")
            .or(nameStartsWith("redis.clients.jedis.Jedis"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isOverriddenFrom(nameEndsWith("Commands")).and(not(named("getDB")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("redis", "jedis");
    }
}
