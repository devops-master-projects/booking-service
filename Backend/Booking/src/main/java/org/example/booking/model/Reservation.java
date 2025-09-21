package org.example.booking.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne
    @JoinColumn(name = "request_id", nullable = false, unique = true)
    private ReservationRequest request;

    @Column(nullable = false)
    private LocalDateTime confirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status; // CONFIRMED, CANCELLED, COMPLETED
}
