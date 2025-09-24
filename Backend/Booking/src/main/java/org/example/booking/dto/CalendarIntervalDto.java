package org.example.booking.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.booking.model.PriceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CalendarIntervalDto {
    private UUID id;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status; // AVAILABLE ili RESERVED
    private BigDecimal price;
    private PriceType priceType; // dodato umesto liste specialPrices
}
