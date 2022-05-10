/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.util.config.streaming;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Provides configuration settings of the web socket endpoint.
 */
public interface WebsocketConfig {

    /**
     * Config path relative to its parent.
     */
    String CONFIG_PATH = "websocket";

    /**
     * Returns the max queue size of how many inflight commands a single web socket client can have.
     *
     * @return the queue size.
     */
    int getSubscriberBackpressureQueueSize();

    /**
     * Returns the max buffer size of how many outstanding command responses and events a single web socket client
     * can have.
     * Additional command responses and events are dropped if this size is reached.
     *
     * @return the buffer size.
     */
    int getPublisherBackpressureBufferSize();

    /**
     * Returns the factor of maximum throughput at which rejections were sent.
     * This threshold should never be reached unless Akka HTTP or the underlying TCP implementation is broken.
     *
     * @return the factor of maximum throughput at which rejections are sent.
     */
    double getThrottlingRejectionFactor();

    /**
     * Returns the throttling config for websocket.
     *
     * @return the throttling config.
     */
    ThrottlingConfig getThrottlingConfig();

    /**
     * Returns the full qualified classname of the {@code org.eclipse.ditto.gateway.service.endpoints.routes.websocket.WebSocketAuthorizationEnforcer}
     * implementation to use for custom authorizations.
     *
     * @return the full qualified classname of the {@code WebSocketAuthorizationEnforcer} implementation to use.
     * @since 3.0.0
     */
    String getAuthorizationEnforcer();

    /**
     * Returns the full qualified classname of the {@code org.eclipse.ditto.gateway.service.endpoints.routes.sse.SseAuthorizationEnforcer}
     * implementation to use for custom authorizations.
     *
     * @return the full qualified classname of the {@code SseAuthorizationEnforcer} implementation to use.
     * @since 3.0.0
     */
    String getConfigProvider();

    /**
     * Returns the full qualified classname of the {@code org.eclipse.ditto.gateway.service.endpoints.routes.websocket.WebSocketSupervisor}
     * implementation to use for supervising WebSocket connections.
     *
     * @return the full qualified classname of the {@code WebSocketSupervisor} implementation to use.
     * @since 3.0.0
     */
    String getConnectionSupervisor();

    /**
     * Returns the full qualified classname of the {@code org.eclipse.ditto.gateway.service.endpoints.routes.websocket.IncomingWebSocketEventSniffer}
     * implementation to use for listening to incoming WebSocket messages.
     *
     * @return the full qualified classname of the {@code IncomingWebSocketEventSniffer} implementation to use.
     * @since 3.0.0
     */
    String getIncomingEventSniffer();

    /**
     * Returns the full qualified classname of the {@code org.eclipse.ditto.gateway.service.endpoints.routes.websocket.OutgoingWebSocketEventSniffer}
     * implementation to use for listening to outgoing WebSocket messages.
     *
     * @return the full qualified classname of the {@code OutgoingWebSocketEventSniffer} implementation to use.
     * @since 3.0.0
     */
    String getOutgoingEventSniffer();

    /**
     * Render this object into a Config object from which a copy of this object can be constructed.
     *
     * @return a config representation.
     */
    default Config render() {
        final Map<String, Object> map = new HashMap<>();
        map.put(WebsocketConfigValue.SUBSCRIBER_BACKPRESSURE_QUEUE_SIZE.getConfigPath(),
                getSubscriberBackpressureQueueSize());
        map.put(WebsocketConfigValue.PUBLISHER_BACKPRESSURE_BUFFER_SIZE.getConfigPath(),
                getPublisherBackpressureBufferSize());
        map.put(WebsocketConfigValue.THROTTLING_REJECTION_FACTOR.getConfigPath(), getThrottlingRejectionFactor());
        map.put(WebsocketConfigValue.AUTHORIZATION_ENFORCER.getConfigPath(), getAuthorizationEnforcer());
        map.put(WebsocketConfigValue.CONFIG_PROVIDER.getConfigPath(), getConfigProvider());
        map.put(WebsocketConfigValue.CONNECTION_SUPERVISOR.getConfigPath(), getConnectionSupervisor());
        map.put(WebsocketConfigValue.INCOMING_EVENT_SNIFFER.getConfigPath(), getIncomingEventSniffer());
        map.put(WebsocketConfigValue.OUTGOING_EVENT_SNIFFER.getConfigPath(), getOutgoingEventSniffer());
        return ConfigFactory.parseMap(map)
                .withFallback(getThrottlingConfig().render())
                .atKey(CONFIG_PATH);
    }

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code WebSocketConfig}.
     */
    enum WebsocketConfigValue implements KnownConfigValue {

        /**
         * The max queue size of how many inflight commands a single web socket client can have.
         */
        SUBSCRIBER_BACKPRESSURE_QUEUE_SIZE("subscriber.backpressure-queue-size", 100),

        /**
         * The max buffer size of how many outstanding command responses and events a single web socket client can have.
         */
        PUBLISHER_BACKPRESSURE_BUFFER_SIZE("publisher.backpressure-buffer-size", 200),

        /**
         * The factor of maximum throughput at which rejections were sent.
         */
        THROTTLING_REJECTION_FACTOR("throttling-rejection-factor", 1.25),

        /**
         * The full qualified classname of the {@code WebSocketAuthorizationEnforcer} to instantiate.
         * @since 3.0.0
         */
        AUTHORIZATION_ENFORCER("authorization-enforcer",
                "org.eclipse.ditto.gateway.service.streaming.NoOpAuthorizationEnforcer"),

        /**
         * The full qualified classname of the {@code WebSocketConfigProvider} to instantiate.
         * @since 3.0.0
         */
        CONFIG_PROVIDER("config-provider",
                "org.eclipse.ditto.gateway.service.endpoints.routes.websocket.NoOpWebSocketConfigProvider"),

        /**
         * The full qualified classname of the {@code WebSocketSupervisor} to instantiate.
         * @since 3.0.0
         */
        CONNECTION_SUPERVISOR("connection-supervisor",
                "org.eclipse.ditto.gateway.service.endpoints.routes.websocket.NoOpWebSocketSupervisor"),

        /**
         * The full qualified classname of the {@code IncomingWebSocketEventSniffer} to instantiate.
         * @since 3.0.0
         */
        INCOMING_EVENT_SNIFFER("incoming-event-sniffer",
                "org.eclipse.ditto.gateway.service.endpoints.routes.websocket.NoOpIncomingWebSocketEventSniffer"),

        /**
         * The full qualified classname of the {@code OutgoingWebSocketEventSniffer} to instantiate.
         * @since 3.0.0
         */
        OUTGOING_EVENT_SNIFFER("outgoing-event-sniffer",
                "org.eclipse.ditto.gateway.service.endpoints.routes.websocket.NoOpOutgoingWebSocketEventSniffer");

        private final String path;
        private final Object defaultValue;

        WebsocketConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }
}
