/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core.repository.eventsourcing;

import org.axonframework.core.DomainEvent;
import org.axonframework.core.DomainEventStream;
import org.axonframework.core.SimpleDomainEventStream;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An EventStore implementation that uses JPA to store DomainEvents in a database. The actual DomainEvent is stored as a
 * serialized blob of bytes. Other columns are used to store meta-data that allow quick finding of DomainEvents for a
 * specific aggregate in the correct order.
 * <p/>
 * The serializer used to serialize the events is configurable. By default, the {@link XStreamEventSerializer} is user.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class JpaEventStore implements EventStore {

    private EntityManager entityManager;

    private final EventSerializer eventSerializer;

    /**
     * Initialize a JpaEventStore using an {@link XStreamEventSerializer}, which serializes events as XML.
     */
    public JpaEventStore() {
        this(new XStreamEventSerializer());
    }

    /**
     * Initialize a JpaEventStore which serializes events using the given {@link EventSerializer}.
     *
     * @param eventSerializer The serializer to (de)serialize domain events with.
     */
    public JpaEventStore(EventSerializer eventSerializer) {
        this.eventSerializer = eventSerializer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void appendEvents(String type, DomainEventStream events) {
        while (events.hasNext()) {
            DomainEvent event = events.next();
            DomainEventEntry entry = new DomainEventEntry(type, event, eventSerializer);
            entityManager.persist(entry);
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public DomainEventStream readEvents(String type, UUID identifier) {
        List<DomainEventEntry> entries = (List<DomainEventEntry>) entityManager.createQuery(
                "SELECT e FROM DomainEventEntry e "
                        + "WHERE e.aggregateIdentifier = :id AND e.type = :type "
                        + "ORDER BY e.sequenceIdentifier ASC")
                .setParameter("id", identifier.toString())
                .setParameter("type", type)
                .getResultList();
        List<DomainEvent> events = new ArrayList<DomainEvent>(entries.size());
        for (DomainEventEntry entry : entries) {
            events.add(entry.getDomainEvent(eventSerializer));
        }
        return new SimpleDomainEventStream(events);
    }

    /**
     * Sets the EntityManager for this EventStore to use. This EntityManager must be assigned to a persistence context
     * that contains the {@link DomainEventEntry} as one of the managed entity types.
     *
     * @param entityManager the EntityManager to use.
     */
    @PersistenceContext
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
}
