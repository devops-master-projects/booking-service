package org.example.booking.dto;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AvailabilityEvent {
    private String eventType; // CREATED, UPDATED, DELETED, STATUS_CHANGED
    private String id;
    private String accommodationId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal price;
    private String priceType;
    private String status; // AVAILABLE, OCCUPIED
}
