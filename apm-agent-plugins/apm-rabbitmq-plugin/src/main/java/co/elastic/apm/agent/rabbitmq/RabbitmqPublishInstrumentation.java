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
package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class RabbitmqPublishInstrumentation extends ElasticApmInstrumentation {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void beforeExecute(@Advice.Local("span") Span span,
                                      @Advice.Origin("#m") String methodName) {

        if (tracer == null || tracer.getActive() == null) {
            return;
        }

        final TraceContextHolder<?> parent = tracer.getActive();

        span = parent.createExitSpan();

        if (span != null) {
            span.withType("external")
                .withSubtype("rabbitmq")
                .withName(methodName.toUpperCase())
                .appendToName(" rabbitmq");
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
        return nameStartsWith("com.rabbitmq.client.impl.ChannelN");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return any()
            .and(not(isConstructor()))
            .and(isPublic())
            .and(not(isAnnotatedWith(Deprecated.class)));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("rabbitmq");
    }
}
