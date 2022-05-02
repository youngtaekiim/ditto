/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.gateway.service.endpoints.routes;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Route;
import akka.stream.javadsl.Flow;

/**
 * Extension to add a custom bind flow for HTTP requests.
 * @since 3.0.0
 */
public abstract class HttpBindFlowProvider extends DittoExtensionPoint {

    /**
     * @param actorSystem the actor system in which to load the extension.
     */
    protected HttpBindFlowProvider(final ActorSystem actorSystem) {
        super(actorSystem);
    }

    /**
     * Create a bind flow for HTTP requests.
     *
     * @return flow which processes HTTP requests.
     */
    public abstract Flow<HttpRequest, HttpResponse, NotUsed> getFlow(final Route innerRoute);

    /**
     * Loads the implementation of {@code HttpBindFlowProvider} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code HttpBindFlowProvider} should be loaded.
     * @return the {@code HttpBindFlowProvider} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    public static HttpBindFlowProvider get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final var implementation = DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(
                actorSystem.settings().config())).getHttpConfig().getBindFlowProvider();

        return new ExtensionId<>(implementation, HttpBindFlowProvider.class).get(actorSystem);
    }
}
