package org.example.config;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.core.amqp.AmqpRetryMode;
import com.azure.core.amqp.AmqpTransportType;
import lombok.Getter;

import java.time.Duration;

@Getter
public class ServiceBusConnectionConfig {
    private final String connectionString;
    private final String queueName;
    private final AmqpRetryOptions retryOptions;
    private final AmqpTransportType transportType;
    private final int maxConcurrentCalls;
    private final Duration maxAutoLockRenewDuration;

    public ServiceBusConnectionConfig(Builder builder) {
        this.connectionString = builder.connectionString;
        this.queueName = builder.queueName;
        this.retryOptions = builder.retryOptions;
        this.transportType = builder.transportType;
        this.maxConcurrentCalls = builder.maxConcurrentCalls;
        this.maxAutoLockRenewDuration = builder.maxAutoLockRenewDuration;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String connectionString;
        private String queueName;
        private AmqpRetryOptions retryOptions;
        private AmqpTransportType transportType = AmqpTransportType.AMQP_WEB_SOCKETS;
        private int maxConcurrentCalls = 1;
        private Duration maxAutoLockRenewDuration = Duration.ofSeconds(30);

        public Builder connectionString(String connectionString) {
            this.connectionString = connectionString;
            return this;
        }

        public Builder queueName(String queueName) {
            this.queueName = queueName;
            return this;
        }

        public Builder retryOptions(int maxRetries, int delaySeconds, int maxDelaySeconds, int timeoutMinutes) {
            this.retryOptions = new AmqpRetryOptions()
                    .setMode(AmqpRetryMode.EXPONENTIAL)
                    .setMaxRetries(maxRetries)
                    .setDelay(Duration.ofSeconds(delaySeconds))
                    .setMaxDelay(Duration.ofSeconds(maxDelaySeconds))
                    .setTryTimeout(Duration.ofMinutes(timeoutMinutes));
            return this;
        }

        public Builder transportType(AmqpTransportType transportType) {
            this.transportType = transportType;
            return this;
        }

        public Builder maxConcurrentCalls(int maxConcurrentCalls) {
            this.maxConcurrentCalls = maxConcurrentCalls;
            return this;
        }

        public Builder maxAutoLockRenewDuration(Duration duration) {
            this.maxAutoLockRenewDuration = duration;
            return this;
        }

        public ServiceBusConnectionConfig build() {
            if (connectionString == null || queueName == null) {
                throw new IllegalArgumentException("Connection string and queue name are required");
            }
            return new ServiceBusConnectionConfig(this);
        }
    }
}