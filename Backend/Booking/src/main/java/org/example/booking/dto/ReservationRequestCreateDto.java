package org.example.booking.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class ReservationRequestCreateDto {
    UUID accommodationId;
    LocalDate startDate;
    LocalDate endDate;
    int guestCount;

}