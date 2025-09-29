package org.example.booking.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity
@Getter
@Setter
@Table(name = "reservation_requests")
public class ReservationRequest {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID guestId;

    @Column()
    private String guestEmail;

    @Column()
    private String guestFirstName;

    @Column()
    private String guestLastName;


    @Column(nullable = false)
    private UUID accommodationId;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private int guestCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status; // PENDING, REJECTED, CANCELLED

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
