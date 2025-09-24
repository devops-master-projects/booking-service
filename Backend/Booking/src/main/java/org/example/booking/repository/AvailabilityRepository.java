package org.example.booking.repository;

import org.example.booking.model.Availability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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


}
