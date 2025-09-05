# Reusable ServiceBus Components Guide

This project now includes reusable ServiceBus components that can be easily configured for different queues and message types.

## Components Overview

### 1. ServiceBusConnectionConfig
A configuration class that encapsulates all ServiceBus connection settings using the builder pattern.

### 2. GenericServiceBusSender
A reusable sender that can send messages to any queue with any configuration.

### 3. GenericServiceBusReceiver  
A reusable receiver that can process messages from any queue with custom message processors.

### 4. ServiceBusFactory
A Spring component that creates configured sender and receiver instances using application properties.

## Basic Usage

### Creating a Sender
```java
@Autowired
private ServiceBusFactory serviceBusFactory;

// Create a sender for any queue
GenericServiceBusSender sender = serviceBusFactory.createSender(
    connectionString, 
    queueName
);

// Send a simple message
sender.sendMessage("Hello World");

// Send an object with partition key
sender.sendObject(myObject, partitionKey);
```

### Creating a Receiver
```java
// Create a receiver with custom message processor
GenericServiceBusReceiver receiver = serviceBusFactory.createReceiver(
    connectionString,
    queueName,
    context -> {
        // Your message processing logic
        String messageBody = context.getMessage().getBody().toString();
        System.out.println("Received: " + messageBody);
    }
);
```

## Advanced Usage

### Multi-Queue Management
See `MultiQueueServiceBusManager` for an example of managing multiple queues:

```java
@Service
public class MyMultiQueueService {
    private final ServiceBusFactory factory;
    private final Map<String, GenericServiceBusSender> senders = new HashMap<>();
    
    public void setupQueues() {
        // Set up different queues with different processors
        setupQueue("orders", this::processOrder);
        setupQueue("notifications", this::processNotification);
        setupQueue("audit", this::processAudit);
    }
    
    private void setupQueue(String queueName, Consumer<ServiceBusReceivedMessageContext> processor) {
        GenericServiceBusSender sender = factory.createSender(connectionString, queueName);
        GenericServiceBusReceiver receiver = factory.createReceiver(connectionString, queueName, processor);
        senders.put(queueName, sender);
    }
}
```

### Custom Configuration
For more control, create your own configuration:

```java
ServiceBusConnectionConfig config = ServiceBusConnectionConfig.builder()
    .connectionString(connectionString)
    .queueName(queueName)
    .retryOptions(maxRetries, delaySeconds, maxDelaySeconds, timeoutMinutes)
    .maxConcurrentCalls(5)
    .maxAutoLockRenewDuration(Duration.ofMinutes(2))
    .build();

GenericServiceBusSender sender = new GenericServiceBusSender(config);
GenericServiceBusReceiver receiver = new GenericServiceBusReceiver(config, messageProcessor);
```

## Migration from Original Services

The original `ServiceBusSenderService` and `ServiceBusReceiverService` have been refactored to use the new reusable components internally, so existing code continues to work without changes.

## Benefits

1. **Reusability**: Same components can be used for different queues
2. **Flexibility**: Easy to configure different settings per queue
3. **Maintainability**: Single implementation to maintain instead of duplicated code
4. **Type Safety**: Builder pattern ensures proper configuration
5. **Resource Management**: Automatic cleanup with try-with-resources support
6. **Error Handling**: Built-in connection error handling and automatic restart logic

## Configuration Properties

All the existing application properties continue to work:

```properties
servicebus.retry.max-retries=5
servicebus.retry.delay-seconds=2
servicebus.retry.max-delay-seconds=30
servicebus.connection.timeout-minutes=1
```

These are used by the `ServiceBusFactory` to create instances with consistent retry and timeout settings.