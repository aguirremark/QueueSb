package org.example.service;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.config.ServiceBusConnectionConfig;

public class GenericServiceBusSender implements AutoCloseable {
    private final ServiceBusSenderClient senderClient;
    private final ObjectMapper objectMapper;

    public GenericServiceBusSender(ServiceBusConnectionConfig config) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        
        this.senderClient = new ServiceBusClientBuilder()
                .connectionString(config.getConnectionString())
                .transportType(config.getTransportType())
                .retryOptions(config.getRetryOptions())
                .sender()
                .queueName(config.getQueueName())
                .buildClient();
    }

//    public void sendMessage(String messageBody) {
//        String messageId = UUID.randomUUID().toString();
//        ServiceBusMessage message = new ServiceBusMessage(messageBody)
//                .setPartitionKey(messageId)
//                .setMessageId(messageId + "-" + System.currentTimeMillis());
//        senderClient.sendMessage(message);
//    }

//    public void sendMessage(ServiceBusMessage message) {
//        senderClient.sendMessage(message);
//    }

    public <T> void processEvent(T object, String partitionKey) {
        try {
            String json = objectMapper.writeValueAsString(object);
            ServiceBusMessage message = new ServiceBusMessage(json)
                    .setPartitionKey(partitionKey)
                    .setMessageId(partitionKey + "-" + System.currentTimeMillis());
            senderClient.sendMessage(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send object", e);
        }
    }

    @Override
    public void close() {
        if (senderClient != null) {
            senderClient.close();
        }
    }
}