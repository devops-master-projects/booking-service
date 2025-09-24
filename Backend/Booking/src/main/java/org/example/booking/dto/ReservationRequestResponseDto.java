package org.example.booking.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.example.booking.model.RequestStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Getter
@Setter
public class ReservationRequestResponseDto {
    UUID id;
    UUID guestId;
    UUID accommodationId;
    String guestEmail;
    String guestFirstName;
    String guestLastName;
    LocalDate startDate;
    LocalDate endDate;
    int guestCount;
    LocalDateTime createdAt;
    RequestStatus status;
}
