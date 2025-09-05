package org.example.service;

import com.azure.messaging.servicebus.*;
import org.example.config.ServiceBusConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class GenericServiceBusReceiver implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(GenericServiceBusReceiver.class);
    
    private final ServiceBusConnectionConfig config;
    private final Consumer<ServiceBusReceivedMessageContext> messageProcessor;
    private final Consumer<ServiceBusErrorContext> errorHandler;
    private ServiceBusProcessorClient processorClient;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong processedMessages = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong connectionErrors = new AtomicLong(0);
    private final ScheduledExecutorService healthCheckExecutor = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean isHealthy = true;
    private final AtomicBoolean isRestarting = new AtomicBoolean(false);
    private volatile long lastRestartTime = 0;

    public GenericServiceBusReceiver(ServiceBusConnectionConfig config, 
                                   Consumer<ServiceBusReceivedMessageContext> messageProcessor,
                                   Consumer<ServiceBusErrorContext> errorHandler) {
        this.config = config;
        this.messageProcessor = messageProcessor;
        this.errorHandler = errorHandler != null ? errorHandler : this::defaultErrorHandler;
        initialize();
    }

    public GenericServiceBusReceiver(ServiceBusConnectionConfig config, 
                                   Consumer<ServiceBusReceivedMessageContext> messageProcessor) {
        this(config, messageProcessor, null);
    }

    private void initialize() {
        try {
            logger.info("Initializing ServiceBus receiver for queue: {}", config.getQueueName());
            createProcessorClient();
            start();
            startHealthCheck();
            logger.info("ServiceBus receiver initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize ServiceBus receiver", e);
            throw new RuntimeException("ServiceBus receiver initialization failed", e);
        }
    }

    public void start() {
        if (processorClient != null && !isRunning.get()) {
            try {
                processorClient.start();
                isRunning.set(true);
                logger.info("ServiceBus receiver started successfully");
            } catch (Exception e) {
                logger.error("Failed to start ServiceBus receiver", e);
                throw new RuntimeException("Failed to start ServiceBus receiver", e);
            }
        }
    }

    public void stop() {
        if (processorClient != null && isRunning.get()) {
            try {
                processorClient.stop();
                isRunning.set(false);
            } catch (Exception e) {
                logger.error("Error stopping ServiceBus receiver", e);
            }
        }
    }

    private void processMessageWithMetrics(ServiceBusReceivedMessageContext context) {
        String messageId = null;
        try {
            messageId = context.getMessage().getMessageId();
            logger.debug("Processing message: {}", messageId);
            
            if (!isHealthy && connectionErrors.get() > 0) {
                logger.warn("Connection unhealthy, abandoning message: {}", messageId);
                context.abandon();
                return;
            }
            
            messageProcessor.accept(context);
            context.complete();
            processedMessages.incrementAndGet();
            logger.debug("Message processed successfully: {}", messageId);
            
        } catch (Exception e) {
            errorCount.incrementAndGet();
            logger.error("Failed to process message: {}", messageId, e);
            
            if (isConnectionError(e)) {
                connectionErrors.incrementAndGet();
                isHealthy = false;
                logger.error("[CONNECTION_ERROR] Connection error during message processing: {}", messageId, e);
                scheduleRestart();
                return;
            }
            
            try {
                if (context.getMessage().getDeliveryCount() < 3) {
                    context.abandon();
                    logger.warn("Message abandoned for retry: {}", messageId);
                } else {
                    context.deadLetter();
                    logger.error("Message sent to dead letter queue: {}", messageId);
                }
            } catch (Exception completionError) {
                logger.error("Failed to complete message processing for: {}", messageId, completionError);
                if (isConnectionError(completionError)) {
                    connectionErrors.incrementAndGet();
                    isHealthy = false;
                    scheduleRestart();
                }
            }
        }
    }

    private void defaultErrorHandler(ServiceBusErrorContext context) {
        errorCount.incrementAndGet();
        Throwable exception = context.getException();
        String errorMessage = exception.getMessage();
        
        if (isConnectionError(exception)) {
            connectionErrors.incrementAndGet();
            isHealthy = false;
            logger.error("[CONNECTION_ERROR] ServiceBus connection error detected | Namespace: {} | Entity: {} | Error: {} | ConnectionErrors: {}", 
                        context.getFullyQualifiedNamespace(),
                        context.getEntityPath(),
                        errorMessage,
                        connectionErrors.get());
            scheduleRestart();
        } else {
            logger.error("ServiceBus error in namespace: {}, entity: {}, error: {}", 
                        context.getFullyQualifiedNamespace(),
                        context.getEntityPath(),
                        errorMessage,
                        exception);
        }
    }

    private void createProcessorClient() {
        this.processorClient = new ServiceBusClientBuilder()
                .connectionString(config.getConnectionString())
                .transportType(config.getTransportType())
                .retryOptions(config.getRetryOptions())
                .processor()
                .queueName(config.getQueueName())
                .maxConcurrentCalls(config.getMaxConcurrentCalls())
                .maxAutoLockRenewDuration(config.getMaxAutoLockRenewDuration())
                .disableAutoComplete()
                .processMessage(this::processMessageWithMetrics)
                .processError(this::defaultErrorHandler)
                .buildProcessorClient();
    }

    private boolean isConnectionError(Throwable exception) {
        if (exception == null) return false;
        
        String message = exception.getMessage();
        String exceptionType = exception.getClass().getSimpleName();
        
        if (exception instanceof NullPointerException && message != null && 
            message.contains("Cannot invoke \"java.util.List.add(Object)\" because \"this._sessions\" is null")) {
            return true;
        }
        
        return message != null && (
            message.contains("this._sessions") ||
            message.contains("Connection was closed") ||
            message.contains("AMQP connection") ||
            message.contains("Cannot invoke") ||
            message.contains("Connection reset") ||
            message.contains("Connection refused") ||
            exceptionType.contains("Connection") ||
            exception instanceof java.lang.NullPointerException
        );
    }

    private void scheduleRestart() {
        if (!isRestarting.compareAndSet(false, true)) {
            logger.debug("Restart already in progress, skipping duplicate restart request");
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRestart = currentTime - lastRestartTime;
        long delaySeconds = Math.min(30, Math.max(10, (connectionErrors.get() - 1) * 5));
        
        if (timeSinceLastRestart < 30000) {
            delaySeconds = Math.max(delaySeconds, 30);
        }
        
        logger.info("[RESTART_SCHEDULED] Scheduling restart in {} seconds due to connection errors", delaySeconds);

        healthCheckExecutor.schedule(() -> {
            try {
                logger.info("[RESTART_ATTEMPT] Attempting to restart ServiceBus processor | ConnectionErrors: {}", connectionErrors.get());
                
                if (processorClient != null) {
                    try {
                        processorClient.close();
                    } catch (Exception closeEx) {
                        logger.warn("Error closing existing processor during restart", closeEx);
                    }
                }
                
                Thread.sleep(3000);
                createProcessorClient();
                processorClient.start();
                isRunning.set(true);
                isHealthy = true;
                lastRestartTime = System.currentTimeMillis();
                
                logger.info("[RESTART_SUCCESS] ServiceBus processor restarted successfully");
            } catch (Exception e) {
                logger.error("[RESTART_FAILED] Failed to restart ServiceBus processor", e);
                isHealthy = false;
            } finally {
                isRestarting.set(false);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private void startHealthCheck() {
        healthCheckExecutor.scheduleAtFixedRate(() -> {
            if (!isHealthy && connectionErrors.get() > 0) {
                logger.warn("[HEALTH_CHECK] ServiceBus connection unhealthy | ConnectionErrors: {} | ProcessedMessages: {} | TotalErrors: {}", 
                           connectionErrors.get(), processedMessages.get(), errorCount.get());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }



    @Override
    public void close() {
//        logger.info("Shutting down ServiceBus receiver...");
        stop();
        
        healthCheckExecutor.shutdown();
        try {
            if (!healthCheckExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                logger.warn("Health check executor did not terminate gracefully, forcing shutdown");
                healthCheckExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            healthCheckExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        if (processorClient != null) {
            try {
                processorClient.close();
                logger.info("ServiceBus receiver closed successfully. Processed: {}, Errors: {}, ConnectionErrors: {}", 
                           processedMessages.get(), errorCount.get(), connectionErrors.get());
            } catch (Exception e) {
                logger.error("Error closing ServiceBus receiver", e);
            } finally {
                processorClient = null;
            }
        }
    }
}