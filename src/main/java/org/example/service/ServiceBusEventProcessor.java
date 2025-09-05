package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.example.exception.ServiceBusException;
import org.example.model.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Consumer;

@Service
public class ServiceBusEventProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ServiceBusEventProcessor.class);
    
    private final ServiceBusFactory serviceBusFactory;
    private final ObjectMapper objectMapper;
    private final String senderConnectionString;
    private final String receiverConnectionString;
    
    public ServiceBusEventProcessor(ServiceBusFactory serviceBusFactory,
                                  @Value("${servicebus.login.sender.connection-string}") String senderConnectionString,
                                  @Value("${servicebus.login.receiver.connection-string}") String receiverConnectionString) {
        this.serviceBusFactory = serviceBusFactory;
        this.senderConnectionString = senderConnectionString;
        this.receiverConnectionString = receiverConnectionString;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    public void processEvent(String queueName, String eventType, Consumer<EventType> eventHandler) {
        try (GenericServiceBusSender sender = serviceBusFactory.createSender(senderConnectionString, queueName);
             GenericServiceBusReceiver receiver = serviceBusFactory.createReceiver(receiverConnectionString, queueName,
                     context -> handleMessage(context, eventHandler))) {

            String eventId = UUID.randomUUID().toString();
            EventType event = new EventType(eventType, eventId);
            sender.processEvent(event, event.getEventId());
            
            logger.info("[SENT] Event: {} | EventId: {} | Queue: {}",
                       event.getEventType(), event.getEventId(), queueName);
            
            Thread.sleep(3000);
            
        } catch (Exception e) {
            logger.error("[ERROR] Failed to process event in queue: {}", queueName, e);
            throw new ServiceBusException("Failed to process event in queue: " + queueName, e);
        }
    }
    
    private void handleMessage(com.azure.messaging.servicebus.ServiceBusReceivedMessageContext context, 
                              Consumer<EventType> eventHandler) {
        try {
            String json = context.getMessage().getBody().toString();
            EventType event = objectMapper.readValue(json, EventType.class);
            
            logger.info("[RECEIVED] Event: {} | EventId: {} | MessageId: {}",
                       event.getEventType(), event.getEventId(), context.getMessage().getMessageId());
            
            eventHandler.accept(event);
            
            logger.info("[SUCCESS] Event processed: {}, EventId: {}", event.getEventType(), event.getEventId());
            
        } catch (Exception e) {
            logger.error("[ERROR] Failed to process event | MessageId: {}",
                        context.getMessage().getMessageId(), e);
            throw new ServiceBusException("Failed to process event: " + context.getMessage().getMessageId(), e);
        }
    }
}