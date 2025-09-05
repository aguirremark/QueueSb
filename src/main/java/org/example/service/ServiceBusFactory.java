package org.example.service;

import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import org.example.config.ServiceBusConnectionConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class ServiceBusFactory {
    
    @Value("${servicebus.retry.max-retries:5}")
    private int maxRetries;
    
    @Value("${servicebus.retry.delay-seconds:2}")
    private int delaySeconds;
    
    @Value("${servicebus.retry.max-delay-seconds:30}")
    private int maxDelaySeconds;
    
    @Value("${servicebus.connection.timeout-minutes:1}")
    private int timeoutMinutes;

    public GenericServiceBusSender createSender(String connectionString, String queueName) {
        ServiceBusConnectionConfig config = ServiceBusConnectionConfig.builder()
                .connectionString(connectionString)
                .queueName(queueName)
                .retryOptions(maxRetries, delaySeconds, maxDelaySeconds, timeoutMinutes)
                .build();
        
        return new GenericServiceBusSender(config);
    }

    public GenericServiceBusReceiver createReceiver(String connectionString, String queueName, 
                                                  Consumer<ServiceBusReceivedMessageContext> messageProcessor) {
        ServiceBusConnectionConfig config = ServiceBusConnectionConfig.builder()
                .connectionString(connectionString)
                .queueName(queueName)
                .retryOptions(maxRetries, delaySeconds, maxDelaySeconds, timeoutMinutes)
                .maxConcurrentCalls(1)
                .build();
        
        return new GenericServiceBusReceiver(config, messageProcessor);
    }

    public GenericServiceBusReceiver createReceiver(String connectionString, String queueName, 
                                                  Consumer<ServiceBusReceivedMessageContext> messageProcessor,
                                                  int maxConcurrentCalls) {
        ServiceBusConnectionConfig config = ServiceBusConnectionConfig.builder()
                .connectionString(connectionString)
                .queueName(queueName)
                .retryOptions(maxRetries, delaySeconds, maxDelaySeconds, timeoutMinutes)
                .maxConcurrentCalls(maxConcurrentCalls)
                .build();
        
        return new GenericServiceBusReceiver(config, messageProcessor);
    }
}