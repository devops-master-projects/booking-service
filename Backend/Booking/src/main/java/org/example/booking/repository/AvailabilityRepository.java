package org.example.booking.repository;

import org.example.booking.model.Availability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AvailabilityRepository extends JpaRepository<Availability, UUID> {

    List<Availability> findByAccommodationId(UUID accommodationId);
    List<Availability> findByAccommodationIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID accommodationId,
            LocalDate startDate,
            LocalDate endDate
    );
}
