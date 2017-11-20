/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.thingsearch.persistence.ProcessableThingEvent;
import org.eclipse.ditto.services.thingsearch.persistence.TestConstants;
import org.eclipse.ditto.signals.events.things.FeaturesModified;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

public final class MongoFeaturesModifiedStrategyTest extends AbstractMongoEventToPersistenceStrategyTest {

    private final MongoFeaturesModifiedStrategy strategy = new MongoFeaturesModifiedStrategy();

    @Test
    public void thingUpdates() throws Exception {
        final List<Bson> updates = strategy.thingUpdates(thingEvent(), indexLengthRestrictionEnforcer);
        assertThat(updates).hasSize(2);
        verify(indexLengthRestrictionEnforcer).enforceRestrictions(thingEvent().getThingEvent().getFeatures());
    }

    @Test
    public void policyUpdates() {
        final List<PolicyUpdate> updates = strategy.policyUpdates(thingEvent(), policyEnforcer);
        verifyPolicyUpdatesForSchemaVersion(updates, 1);
        verifyPermissionCallForSchemaVersion(() -> any(ResourceKey.class), ArgumentMatchers::anyString,
                expectedPolicyUpdates());
    }

    private int expectedPolicyUpdates() {
        return TestConstants.ThingEvent.FEATURES_MODIFIED.getFeatures().getSize()
                + TestConstants.ThingEvent.FEATURES_MODIFIED.getFeatures().stream().map(ft ->
                ft.getProperties().get().getSize()).reduce(0, (a, b) -> a + b);
    }

    private ProcessableThingEvent<FeaturesModified> thingEvent() {
        return ProcessableThingEvent.newInstance(TestConstants.ThingEvent.FEATURES_MODIFIED, version);
    }

}