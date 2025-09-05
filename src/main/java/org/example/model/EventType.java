package org.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class EventType {

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("timestamp")
    private Instant timestamp;

    public EventType() {
        // Default constructor for Jackson deserialization
    }

    public EventType(String eventType, String eventId) {
        this.eventType = eventType;
        this.eventId = eventId;
        this.timestamp = Instant.now();
    }


}