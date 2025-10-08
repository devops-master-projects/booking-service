package org.example.booking.repository;

import org.example.booking.model.RequestStatus;
import org.example.booking.model.ReservationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationRequestRepository extends JpaRepository<ReservationRequest, UUID> {

    List<ReservationRequest> findByAccommodationId(UUID accommodationId);
    List<ReservationRequest> findByAccommodationIdAndStatus(UUID accommodationId, RequestStatus status);
    List<ReservationRequest> findByGuestId(UUID guestId);
    List<ReservationRequest> findByAccommodationIdOrderByCreatedAtDesc(UUID accommodationId);

    List<ReservationRequest> findByGuestIdAndAccommodationId(UUID guestId, UUID accommodationId);

}