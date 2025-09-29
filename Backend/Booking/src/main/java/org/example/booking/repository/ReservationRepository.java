package org.example.booking.repository;

import org.example.booking.model.Reservation;
import org.example.booking.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByRequestGuestId(UUID guestId);

    List<Reservation> findByRequestAccommodationId(UUID accommodationId);

    List<Reservation> findByStatus(ReservationStatus status);

    boolean existsByRequest_AccommodationIdAndRequest_StartDateLessThanEqualAndRequest_EndDateGreaterThanEqual(
            UUID accommodationId,
            LocalDate startDate,
            LocalDate endDate
    );

    Set<Reservation> findByRequest_AccommodationIdAndRequest_StartDateLessThanEqualAndRequest_EndDateGreaterThanEqualAndStatus(
            UUID accommodationId,
            LocalDate endDate,
            LocalDate startDate,
            ReservationStatus status
    );

    Set<Reservation> findByRequest_AccommodationIdAndRequest_StartDateLessThanEqualAndRequest_EndDateGreaterThanEqual(
            UUID accommodationId,
            LocalDate endDate,
            LocalDate startDate
    );
    boolean existsByRequestAccommodationIdAndRequestStartDateLessThanEqualAndRequestEndDateGreaterThanEqualAndStatus(
            UUID accommodationId,
            LocalDate startDate,
            LocalDate endDate,
            ReservationStatus status
    );

    Optional<Reservation> findByRequest_Id(UUID requestId);
    int countByRequest_GuestIdAndStatus(UUID guestId, ReservationStatus status);



}