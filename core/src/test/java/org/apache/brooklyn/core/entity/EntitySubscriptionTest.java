/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.core.entity;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.SubscriptionHandle;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.sensor.BasicSensorEvent;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.policy.TestEnricher;
import org.apache.brooklyn.core.test.policy.TestPolicy;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.test.Asserts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class EntitySubscriptionTest extends BrooklynAppUnitTestSupport {

    // TODO Duplication between this and PolicySubscriptionTest and LocalSubscriptionManagerTest

    private static final Logger log = LoggerFactory.getLogger(EntitySubscriptionTest.class);
    
    private static final long SHORT_WAIT_MS = 100;

    private SimulatedLocation loc;
    private TestEntity entity;
    private TestEntity observedEntity;
    private BasicGroup observedGroup;
    private TestEntity observedChildEntity;
    private TestEntity observedMemberEntity;
    private TestEntity otherEntity;
    private RecordingSensorEventListener<Object> listener;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = app.newSimulatedLocation();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        observedEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        observedChildEntity = observedEntity.createAndManageChild(EntitySpec.create(TestEntity.class));

        observedGroup = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
        observedMemberEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        observedGroup.addMember(observedMemberEntity);
        
        otherEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        listener = new RecordingSensorEventListener<>();
        
        app.start(ImmutableList.of(loc));
    }
    
    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        try {
            super.tearDown();
        } finally {
            loc = null;
            entity = null;
            observedEntity = null;
            observedChildEntity = null;
            observedGroup = null;
            observedMemberEntity = null;
            otherEntity = null;
            listener = null;
        }
    }
    
    @Test
    public void testSubscriptionReceivesEvents() {
        entity.subscriptions().subscribe(observedEntity, TestEntity.SEQUENCE, listener);
        entity.subscriptions().subscribe(observedEntity, TestEntity.NAME, listener);
        entity.subscriptions().subscribe(observedEntity, TestEntity.MY_NOTIF, listener);
        
        otherEntity.sensors().set(TestEntity.SEQUENCE, 123);
        observedEntity.sensors().set(TestEntity.SEQUENCE, 123);
        observedEntity.sensors().set(TestEntity.NAME, "myname");
        observedEntity.sensors().emit(TestEntity.MY_NOTIF, 456);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, observedEntity, 123),
                        new BasicSensorEvent<String>(TestEntity.NAME, observedEntity, "myname"),
                        new BasicSensorEvent<Integer>(TestEntity.MY_NOTIF, observedEntity, 456)));
            }});
    }
    
    @Test
    public void testSubscriptionToAllReceivesEvents() {
        entity.subscriptions().subscribe(null, TestEntity.SEQUENCE, listener);
        
        observedEntity.sensors().set(TestEntity.SEQUENCE, 123);
        otherEntity.sensors().set(TestEntity.SEQUENCE, 456);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, observedEntity, 123),
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, otherEntity, 456)));
            }});
    }
    
    @Test
    public void testSubscribeToChildrenReceivesEvents() {
        entity.subscriptions().subscribeToChildren(observedEntity, TestEntity.SEQUENCE, listener);
        
        observedChildEntity.sensors().set(TestEntity.SEQUENCE, 123);
        observedEntity.sensors().set(TestEntity.SEQUENCE, 456);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, observedChildEntity, 123)));
            }});
    }
    
    @Test
    public void testSubscribeToChildrenReceivesEventsForDynamicallyAddedChildren() {
        entity.subscriptions().subscribeToChildren(observedEntity, TestEntity.SEQUENCE, listener);
        
        final TestEntity observedChildEntity2 = observedEntity.createAndManageChild(EntitySpec.create(TestEntity.class));
        observedChildEntity2.sensors().set(TestEntity.SEQUENCE, 123);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, observedChildEntity2, 123)));
            }});
    }
    
    @Test
    public void testSubscribeToMembersReceivesEvents() {
        entity.subscriptions().subscribeToMembers(observedGroup, TestEntity.SEQUENCE, listener);
        
        observedMemberEntity.sensors().set(TestEntity.SEQUENCE, 123);
        observedGroup.sensors().set(TestEntity.SEQUENCE, 456);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, observedMemberEntity, 123)));
            }});
    }
    
    @Test
    public void testSubscribeToMembersReceivesEventsForDynamicallyAddedMembers() {
        entity.subscriptions().subscribeToMembers(observedGroup, TestEntity.SEQUENCE, listener);
        
        final TestEntity observedMemberEntity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        observedGroup.addMember(observedMemberEntity2);
        observedMemberEntity2.sensors().set(TestEntity.SEQUENCE, 123);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, observedMemberEntity2, 123)));
            }});
    }
    
    @Test(groups="Integration")
    public void testSubscribeToMembersIgnoresEventsForDynamicallyRemovedMembers() {
        entity.subscriptions().subscribeToMembers(observedGroup, TestEntity.SEQUENCE, listener);
        
        observedGroup.removeMember(observedMemberEntity);
        
        observedMemberEntity.sensors().set(TestEntity.SEQUENCE, 123);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of());
            }});
    }
    
    @Test
    public void testUnsubscribeRemovesAllSubscriptionsForThatEntity() {
        entity.subscriptions().subscribe(observedEntity, TestEntity.SEQUENCE, listener);
        entity.subscriptions().subscribe(observedEntity, TestEntity.NAME, listener);
        entity.subscriptions().subscribe(observedEntity, TestEntity.MY_NOTIF, listener);
        entity.subscriptions().subscribe(otherEntity, TestEntity.SEQUENCE, listener);
        entity.subscriptions().unsubscribe(observedEntity);
        
        observedEntity.sensors().set(TestEntity.SEQUENCE, 123);
        observedEntity.sensors().set(TestEntity.NAME, "myname");
        observedEntity.sensors().emit(TestEntity.MY_NOTIF, 123);
        otherEntity.sensors().set(TestEntity.SEQUENCE, 456);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, otherEntity, 456)));
            }});
    }
    
    @Test
    @SuppressWarnings("unused")
    public void testUnsubscribeUsingHandleStopsEvents() {
        SubscriptionHandle handle1 = entity.subscriptions().subscribe(observedEntity, TestEntity.SEQUENCE, listener);
        SubscriptionHandle handle2 = entity.subscriptions().subscribe(observedEntity, TestEntity.NAME, listener);
        SubscriptionHandle handle3 = entity.subscriptions().subscribe(otherEntity, TestEntity.SEQUENCE, listener);
        
        entity.subscriptions().unsubscribe(observedEntity, handle2);
        
        observedEntity.sensors().set(TestEntity.SEQUENCE, 123);
        observedEntity.sensors().set(TestEntity.NAME, "myname");
        otherEntity.sensors().set(TestEntity.SEQUENCE, 456);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, observedEntity, 123),
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, otherEntity, 456)));
            }});
    }
    
    @Test
    public void testSubscriptionReceivesEventsInOrder() {
        final int NUM_EVENTS = 100;
        entity.subscriptions().subscribe(observedEntity, TestEntity.MY_NOTIF, listener);

        for (int i = 0; i < NUM_EVENTS; i++) {
            observedEntity.sensors().emit(TestEntity.MY_NOTIF, i);
        }
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(Iterables.size(listener.getEvents()), NUM_EVENTS);
                for (int i = 0; i < NUM_EVENTS; i++) {
                    assertEquals(Iterables.get(listener.getEvents(), i).getValue(), i);
                }
            }});
    }

    @Test
    public void testSubscriptionReceivesInitialValueEvents() {
        observedEntity.sensors().set(TestEntity.SEQUENCE, 123);
        observedEntity.sensors().set(TestEntity.NAME, "myname");
        
        entity.subscriptions().subscribe(ImmutableMap.of("notifyOfInitialValue", true), observedEntity, TestEntity.SEQUENCE, listener);
        entity.subscriptions().subscribe(ImmutableMap.of("notifyOfInitialValue", true), observedEntity, TestEntity.NAME, listener);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, observedEntity, 123),
                        new BasicSensorEvent<String>(TestEntity.NAME, observedEntity, "myname")));
            }});
    }

    
    @Test
    public void testSubscriptionNotReceivesInitialValueEventsByDefault() {
        observedEntity.sensors().set(TestEntity.SEQUENCE, 123);
        observedEntity.sensors().set(TestEntity.NAME, "myname");
        
        entity.subscriptions().subscribe(observedEntity, TestEntity.SEQUENCE, listener);
        entity.subscriptions().subscribe(observedEntity, TestEntity.NAME, listener);
        
        Asserts.succeedsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of());
            }});
    }

    // TODO A visual inspection test that we get a log.warn telling us we can't get the initial-value
    @Test
    public void testSubscriptionForInitialValueWhenNotValid() {
        entity.subscriptions().subscribe(ImmutableMap.of("notifyOfInitialValue", true), observedEntity, TestEntity.MY_NOTIF, listener);
        entity.subscriptions().subscribe(ImmutableMap.of("notifyOfInitialValue", true), observedEntity, null, listener);
        entity.subscriptions().subscribe(ImmutableMap.of("notifyOfInitialValue", true), null, TestEntity.NAME, listener);
        entity.subscriptions().subscribe(ImmutableMap.of("notifyOfInitialValue", true), null, null, listener);
    }
    
    @Test
    public void testContextEntityOnSubscriptionCallbackTask() {
        log.info("Observing "+observedEntity+" from "+entity);
        
        observedEntity.sensors().set(TestEntity.NAME, "myval");
        entity.subscriptions().subscribe(ImmutableMap.of("notifyOfInitialValue", true), observedEntity, TestEntity.NAME, listener);
        
        // notify-of-initial-value should give us our entity
        assertListenerCalledOnceWithContextEntityEventually(listener, entity);
        listener.clearEvents();
        
        // as should subsequent events
        observedEntity.sensors().set(TestEntity.NAME, "myval2");
        assertListenerCalledOnceWithContextEntityEventually(listener, entity);
        listener.clearEvents();

        // same for subscribing to children: context should be the subscriber
        entity.subscriptions().subscribeToChildren(observedEntity, TestEntity.SEQUENCE, listener);
        observedChildEntity.sensors().set(TestEntity.SEQUENCE, 123);
        assertListenerCalledOnceWithContextEntityEventually(listener, entity);
    }
    
    @Test
    public void testContextEntityOnPolicySubscriptionCallbackTask() {
        TestPolicy policy = entity.policies().add(PolicySpec.create(TestPolicy.class));
        policy.subscriptions().subscribe(observedEntity, TestEntity.NAME, listener);

        observedEntity.sensors().set(TestEntity.NAME, "myval");
        assertListenerCalledOnceWithContextEntityEventually(listener, entity);
    }
    
    @Test
    public void testContextEntityOnEnricherSubscriptionCallbackTask() {
        TestEnricher enricher = entity.enrichers().add(EnricherSpec.create(TestEnricher.class));
        enricher.subscriptions().subscribe(observedEntity, TestEntity.NAME, listener);

        observedEntity.sensors().set(TestEntity.NAME, "myval");
        assertListenerCalledOnceWithContextEntityEventually(listener, entity);
    }
    
    protected void assertListenerCalledEventually(final RecordingSensorEventListener<?> listener, final int expectedEventCount) {
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents().size(), expectedEventCount);
            }});
    }
    
    protected void assertListenerCalledOnceWithContextEntityEventually(final RecordingSensorEventListener<?> listener, final Entity expectedContext) {
        assertListenerCalledEventually(listener, 1);
        assertEquals(BrooklynTaskTags.getContextEntity(Iterables.getOnlyElement(listener.getTasks())), entity);
    }
}
