package org.example.booking.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "availability")
public class Availability {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID accommodationId;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private BigDecimal price;
}
