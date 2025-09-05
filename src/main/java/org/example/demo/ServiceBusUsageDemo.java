package org.example.demo;

import org.example.model.EventType;
import org.example.service.ServiceBusEventProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ServiceBusUsageDemo implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(ServiceBusUsageDemo.class);

    private final ServiceBusEventProcessor eventProcessor;

    @Value("${servicebus.queue.name}")
    private String queueName;

    public ServiceBusUsageDemo(ServiceBusEventProcessor eventProcessor) {
        this.eventProcessor = eventProcessor;
    }

    @Override
    public void run(String... args) throws Exception {

        eventProcessor.processEvent(queueName, "LOGIN_EVENT", this::handleLoginEvent);

    }

    private void handleLoginEvent(EventType event) {
        logger.info("Processing login event: {}", event.getEventId());
    }
}