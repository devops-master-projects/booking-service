package org.example.booking.repository;

import org.example.booking.model.Reservation;
import org.example.booking.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByRequestGuestId(UUID guestId);

    List<Reservation> findByRequestAccommodationId(UUID accommodationId);

    List<Reservation> findByStatus(ReservationStatus status);
}