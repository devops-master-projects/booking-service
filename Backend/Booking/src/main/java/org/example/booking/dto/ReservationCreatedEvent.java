package org.example.booking.dto;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReservationCreatedEvent {
    private UUID reservationId;
    private UUID accommodationId;
    private UUID guestId;
    private String guestName;
    private String guestLastName;
    private String guestEmail;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;
}
