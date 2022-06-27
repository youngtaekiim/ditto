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
package org.eclipse.ditto.things.service.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.policies.model.SubjectIssuer.GOOGLE;

import java.util.List;
import java.util.function.Consumer;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.testkit.javadsl.TestKit;
import scala.PartialFunction;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link org.eclipse.ditto.things.service.persistence.actors.ThingSupervisorActor} and its
 * {@link org.eclipse.ditto.things.service.enforcement.ThingEnforcement} for commands requiring smart channel
 * selection where live responses come from a different channel than where the command is published.
 */
public final class SmartChannelEnforcementWithResponseReceiverTest extends AbstractThingEnforcementTest {

    public static final PolicyId POLICY_ID = PolicyId.of("policy:id");

    @Rule
    public final TestName testName = new TestName();

    private JsonObject thing;
    private SudoRetrieveThingResponse sudoRetrieveThingResponse;
    private SudoRetrievePolicyResponse sudoRetrievePolicyResponse;

    @Before
    public void init() {
        super.init();
        final PolicyId policyId = POLICY_ID;
        thing = TestSetup.newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build()
                .toJson(FieldType.all());
        sudoRetrieveThingResponse = SudoRetrieveThingResponse.of(thing, DittoHeaders.empty());
        sudoRetrievePolicyResponse = SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());
    }

    @Test
    public void matchLiveChannelCondition() {
        new TestKit(system) {{
            final var retrieveThing = getRetrieveThing(headers -> headers.liveChannelCondition("exists(thingId)"));
            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(POLICY_ID, sudoRetrievePolicyResponse);

            thingPersistenceActorProbe.expectMsg(addReadSubjectHeader(retrieveThing, TestSetup.GOOGLE_SUBJECT));
            thingPersistenceActorProbe.reply(getRetrieveThingResponse(retrieveThing, b -> {}));

            expectLiveQueryCommandOnPubSub(retrieveThing);
            supervisor.tell(getRetrieveThingResponse(retrieveThing, b -> b.channel("live")), ActorRef.noSender());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(POLICY_ID, sudoRetrievePolicyResponse);

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(POLICY_ID, sudoRetrievePolicyResponse);

            assertLiveChannel(expectMsgClass(RetrieveThingResponse.class));
        }};
    }

    @Test
    public void liveChannelErrorWithTwinFallback() {
        new TestKit(system) {{
            final var retrieveThing = getRetrieveThing(headers -> headers.liveChannelCondition("exists(thingId)")
                    .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(), "use-twin"));
            supervisor.tell(retrieveThing, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(POLICY_ID, sudoRetrievePolicyResponse);

            thingPersistenceActorProbe.expectMsg(addReadSubjectHeader(retrieveThing, TestSetup.GOOGLE_SUBJECT));
            final var twinResponse = getRetrieveThingResponse(retrieveThing, b -> {});
            thingPersistenceActorProbe.reply(twinResponse);

            expectLiveQueryCommandOnPubSub(retrieveThing);

            supervisor.tell(ThingErrorResponse.of(retrieveThing.getEntityId(),
                    ThingIdInvalidException.newBuilder(retrieveThing.getEntityId())
                            .dittoHeaders(retrieveThing.getDittoHeaders().toBuilder().channel("live").build())
                            .build()), ActorRef.noSender());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(POLICY_ID, sudoRetrievePolicyResponse);

            final var receivedError = expectMsgClass(ThingIdInvalidException.class);
            assertLiveChannel(receivedError);
        }};
    }

    @Test
    public void liveCommandErrorWithTwinFallback() {
        new TestKit(system) {{
            final var retrieveThing = getRetrieveThing(headers -> headers.channel("live")
                    .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(), "use-twin"));
            supervisor.tell(retrieveThing, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(POLICY_ID, sudoRetrievePolicyResponse);

            RetrieveThing expectedRetrieveThing = addReadSubjectHeader(retrieveThing, TestSetup.GOOGLE_SUBJECT);
            expectedRetrieveThing = expectedRetrieveThing.setDittoHeaders(expectedRetrieveThing.getDittoHeaders()
                    .toBuilder()
                    .channel(Signal.CHANNEL_TWIN)
                    .readRevokedSubjects(List.of())
                    .build());
            thingPersistenceActorProbe.expectMsg(expectedRetrieveThing);
            final var twinResponse = getRetrieveThingResponse(retrieveThing, b -> {});
            thingPersistenceActorProbe.reply(twinResponse);

            expectLiveQueryCommandOnPubSub(retrieveThing);
            supervisor.tell(ThingErrorResponse.of(retrieveThing.getEntityId(),
                    ThingIdInvalidException.newBuilder(retrieveThing.getEntityId())
                            .dittoHeaders(retrieveThing.getDittoHeaders().toBuilder().channel("live").build())
                            .build()), ActorRef.noSender());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(POLICY_ID, sudoRetrievePolicyResponse);

            final var receivedError = expectMsgClass(ThingIdInvalidException.class);
            assertLiveChannel(receivedError);
        }};
    }

    private DittoHeaders headers() {
        return DittoHeaders.newBuilder()
                .authorizationContext(
                        AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, TestSetup.SUBJECT,
                                AuthorizationSubject.newInstance(String.format("%s:%s", GOOGLE, TestSetup.SUBJECT_ID))))
                .correlationId(testName.getMethodName())
                .schemaVersion(JsonSchemaVersion.V_2)
                .build();
    }

    private RetrieveThing getRetrieveThing(final Consumer<DittoHeadersBuilder<?, ?>> headerModifier) {
        final DittoHeadersBuilder<?, ?> builder =
                headers().toBuilder().putHeader(DittoHeaderDefinition.ORIGINATOR.getKey(), TestSetup.SUBJECT.getId());
        headerModifier.accept(builder);
        return RetrieveThing.of(TestSetup.THING_ID, builder.build());
    }

    private RetrieveThingResponse getRetrieveThingResponse(final RetrieveThing retrieveThing,
            final Consumer<DittoHeadersBuilder<?, ?>> headerModifier) {
        final var builder = retrieveThing.getDittoHeaders()
                .toBuilder()
                .putHeader(DittoHeaderDefinition.ORIGINATOR.getKey(), TestSetup.SUBJECT.getId())
                .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(), "true");
        headerModifier.accept(builder);
        return RetrieveThingResponse.of(retrieveThing.getEntityId(), thing, builder.build());
    }

    private void expectLiveQueryCommandOnPubSub(final RetrieveThing retrieveThing) {
        final DistributedPubSubMediator.Publish publishLiveQueryCommand =
                (DistributedPubSubMediator.Publish) pubSubMediatorProbe.fishForMessage(
                        FiniteDuration.apply(5, "s"),
                        "publish live query command",
                        PartialFunction.fromFunction(msg ->
                                msg instanceof DistributedPubSubMediator.Publish publish &&
                                        publish.topic().equals(
                                                StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic())
                        )
                );

        assertThat(publishLiveQueryCommand.topic()).isEqualTo(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic());
        assertThat(publishLiveQueryCommand.msg()).isInstanceOf(ThingCommand.class);
        assertThat((CharSequence) ((ThingCommand<?>) publishLiveQueryCommand.msg()).getEntityId())
                .isEqualTo(retrieveThing.getEntityId());
        assertLiveChannel((ThingCommand<?>) publishLiveQueryCommand.msg());
    }

    private void assertLiveChannel(final Class<? extends WithDittoHeaders> clazz, final Object message) {
        assertThat(message).isInstanceOf(clazz);
        Assertions.assertThat(Signal.isChannelLive(clazz.cast(message)))
                .describedAs("Expect live channel: " + message)
                .isTrue();
    }

    private void assertLiveChannel(final WithDittoHeaders signal) {
        assertLiveChannel(WithDittoHeaders.class, signal);
    }
}
