package org.example.booking.repository;

import org.example.booking.model.Availability;
import org.example.booking.model.AvailabilityStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface AvailabilityRepository extends JpaRepository<Availability, UUID> {

    List<Availability> findByAccommodationId(UUID accommodationId);
    Set<Availability> findByAccommodationIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID accommodationId,
            LocalDate endDate,
            LocalDate startDate
    );

    Set<Availability> findByAccommodationIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID accommodationId, AvailabilityStatus status, LocalDate endDate, LocalDate startDate);

    List<Availability> findByAccommodationIdAndStatusOrderByStartDateAsc(
            UUID accommodationId,
            AvailabilityStatus status
    );



}
