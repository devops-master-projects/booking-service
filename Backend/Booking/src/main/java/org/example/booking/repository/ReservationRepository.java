package org.example.booking.repository;

import org.example.booking.model.Reservation;
import org.example.booking.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByRequestGuestId(UUID guestId);

    List<Reservation> findByRequestAccommodationId(UUID accommodationId);

    List<Reservation> findByStatus(ReservationStatus status);

    boolean existsByRequest_AccommodationIdAndRequest_StartDateLessThanEqualAndRequest_EndDateGreaterThanEqual(
            UUID accommodationId,
            LocalDate startDate,
            LocalDate endDate
    );

    boolean existsByRequest_GuestIdAndRequest_AccommodationIdAndStatusAndRequest_EndDateBefore(
            UUID guestId,
            UUID accommodationId,
            ReservationStatus status,
            LocalDate date
    );


    List<Reservation> findAllByStatusAndRequest_EndDateBefore(
            ReservationStatus status, LocalDate request_endDate
    );

    boolean existsByRequest_GuestIdAndStatusIn(UUID guestId, Collection<ReservationStatus> statuses);


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


    boolean existsByRequest_AccommodationIdInAndStatusInAndRequest_EndDateAfter(
            List<UUID> accommodationIds,
            List<ReservationStatus> statuses,
            LocalDate date
    );

}