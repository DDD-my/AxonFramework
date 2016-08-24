/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.config;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.commandhandling.model.GenericJpaRepository;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.junit.Test;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;
import static org.axonframework.config.AggregateConfigurer.defaultConfiguration;
import static org.junit.Assert.assertNotNull;

public class ConfigurerTest {


    @Test
    public void defaultConfigurationWithEventSourcing() throws Exception {
        Map<String, String> properties = new HashMap<>();
        properties.put("hibernate.connection.url", "jdbc:hsqldb:mem:axontest");
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eventStore", properties);
        EntityManager em = emf.createEntityManager();
        Configuration config = Configurer.defaultConfiguration()
                .withTransactionManager(c -> isolationLevel -> {
                    EntityTransaction tx = em.getTransaction();
                    tx.begin();
                    return new Transaction() {
                        @Override
                        public void commit() {
                            tx.commit();
                        }

                        @Override
                        public void rollback() {
                            tx.rollback();
                        }
                    };
                })
                .withEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                .registerAggregate(
                        defaultConfiguration(StubAggregate.class)
                                .useRepository(c -> new GenericJpaRepository<>(new SimpleEntityManagerProvider(em), StubAggregate.class, c.eventBus())))
                .initialize();

        config.commandBus().dispatch(GenericCommandMessage.asCommandMessage("test"));
        assertNotNull(config.repository(StubAggregate.class));
    }

    @Entity(name = "StubAggregate")
    private static class StubAggregate {

        @Id
        @AggregateIdentifier
        private String id;

        public StubAggregate() {
        }

        @CommandHandler
        public StubAggregate(String command) {
            apply(command);
        }

        @CommandHandler(commandName = "update")
        public void update(String command) {
            apply(1L);
        }

        @EventSourcingHandler
        protected void on(String event) {
            this.id = event;
        }
    }
}
