package org.example.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReservationRequestUpdateDto {
    private LocalDate startDate;
    private LocalDate endDate;
    private int guestCount;
}
