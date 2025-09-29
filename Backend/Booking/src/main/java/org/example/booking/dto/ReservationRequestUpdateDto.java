package org.example.booking.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ReservationRequestUpdateDto {
    private LocalDate startDate;
    private LocalDate endDate;
    private int guestCount;
}
