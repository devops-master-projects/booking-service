package org.example.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReservationRequestCreateDto {
    UUID accommodationId;
    LocalDate startDate;
    LocalDate endDate;
    int guestCount;

}