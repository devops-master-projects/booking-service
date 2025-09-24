package org.example.booking.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Getter
@Setter
public class ReservationRequestCreateDto {
    UUID accommodationId;
    LocalDate startDate;
    LocalDate endDate;
    int guestCount;

}