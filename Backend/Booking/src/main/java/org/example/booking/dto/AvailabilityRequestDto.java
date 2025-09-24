package org.example.booking.dto;

import lombok.*;
import org.example.booking.model.PriceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AvailabilityRequestDto {
    private UUID accommodationId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal price;
    private PriceType priceType; // NORMAL, HOLIDAY, SEASONAL, WEEKEND
}
