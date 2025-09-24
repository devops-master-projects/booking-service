package org.example.booking.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.example.booking.model.RequestStatus;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Getter
@Setter
public class ReservationRequestResponseDto {
    UUID id;
    UUID guestId;
    UUID accommodationId;
    LocalDate startDate;
    LocalDate endDate;
    int guestCount;
    RequestStatus status;
}
