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
package org.eclipse.ditto.edge.api.dispatching;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.function.UnaryOperator;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.ActorSystem;

public interface SignalTransformer extends DittoExtensionPoint, UnaryOperator<Signal<?>> {

    static final String CONFIG_PATH = "signal-transformer";

    /**
     * Loads the implementation of {@code SignalTransformer} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code SignalTransformer} should be loaded.
     * @return the {@code SignalTransformer} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static SignalTransformer get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final DefaultScopedConfig dittoScoped = DefaultScopedConfig.dittoScoped(actorSystem.settings().config());
        final var implementation = dittoScoped.getString(CONFIG_PATH);

        return AkkaClassLoader.instantiate(actorSystem, SignalTransformer.class,
                implementation,
                List.of(ActorSystem.class),
                List.of(actorSystem));
    }
}
