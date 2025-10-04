package org.example.booking.repository;

import org.example.booking.model.Availability;
import org.example.booking.model.AvailabilityStatus;
import org.example.booking.model.PriceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("AvailabilityRepository Tests")
public class AvailabilityRepositoryTest {

    @Autowired
    private AvailabilityRepository availabilityRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UUID accommodationId1;
    private UUID accommodationId2;
    private Availability availability1;
    private Availability availability2;
    private Availability availability3;
    private Availability availability4;

    @BeforeEach
    void setUp() {
        accommodationId1 = UUID.randomUUID();
        accommodationId2 = UUID.randomUUID();

        // Create test data
        availability1 = createAvailability(
                accommodationId1,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 7),
                new BigDecimal("100.00"),
                AvailabilityStatus.AVAILABLE,
                PriceType.NORMAL
        );

        availability2 = createAvailability(
                accommodationId1,
                LocalDate.of(2025, 1, 8),
                LocalDate.of(2025, 1, 15),
                new BigDecimal("150.00"),
                AvailabilityStatus.OCCUPIED,
                PriceType.WEEKEND
        );

        availability3 = createAvailability(
                accommodationId1,
                LocalDate.of(2025, 1, 16),
                LocalDate.of(2025, 1, 31),
                new BigDecimal("200.00"),
                AvailabilityStatus.AVAILABLE,
                PriceType.HOLIDAY
        );

        availability4 = createAvailability(
                accommodationId2,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 10),
                new BigDecimal("80.00"),
                AvailabilityStatus.AVAILABLE,
                PriceType.NORMAL
        );

        // Persist test data
        entityManager.persistAndFlush(availability1);
        entityManager.persistAndFlush(availability2);
        entityManager.persistAndFlush(availability3);
        entityManager.persistAndFlush(availability4);
    }

    @Test
    @DisplayName("Should find all availabilities by accommodation ID")
    void testFindByAccommodationId() {
        // When
        List<Availability> result = availabilityRepository.findByAccommodationId(accommodationId1);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).extracting(Availability::getAccommodationId)
                .containsOnly(accommodationId1);
        assertThat(result).extracting(Availability::getStatus)
                .containsExactlyInAnyOrder(
                        AvailabilityStatus.AVAILABLE,
                        AvailabilityStatus.OCCUPIED,
                        AvailabilityStatus.AVAILABLE
                );
    }

    @Test
    @DisplayName("Should return empty list when no availabilities found for accommodation ID")
    void testFindByAccommodationId_NotFound() {
        // Given
        UUID nonExistentAccommodationId = UUID.randomUUID();

        // When
        List<Availability> result = availabilityRepository.findByAccommodationId(nonExistentAccommodationId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should find availabilities by accommodation ID and date range overlap")
    void testFindByAccommodationIdAndDateRangeOverlap() {
        // Given - searching for availabilities that overlap with Jan 5-12, 2025
        LocalDate searchStartDate = LocalDate.of(2025, 1, 5);
        LocalDate searchEndDate = LocalDate.of(2025, 1, 12);

        // When
        Set<Availability> result = availabilityRepository
                .findByAccommodationIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        accommodationId1, searchEndDate, searchStartDate);

        // Then
        assertThat(result).hasSize(2); // availability1 (1-7) and availability2 (8-15) should overlap
        assertThat(result).extracting(Availability::getStartDate)
                .containsExactlyInAnyOrder(
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 1, 8)
                );
    }

    @Test
    @DisplayName("Should find availabilities by accommodation ID, status and date range overlap")
    void testFindByAccommodationIdStatusAndDateRangeOverlap() {
        // Given - searching for AVAILABLE status overlapping with Jan 1-20, 2025
        LocalDate searchStartDate = LocalDate.of(2025, 1, 1);
        LocalDate searchEndDate = LocalDate.of(2025, 1, 20);

        // When
        Set<Availability> result = availabilityRepository
                .findByAccommodationIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        accommodationId1, AvailabilityStatus.AVAILABLE, searchEndDate, searchStartDate);

        // Then
        assertThat(result).hasSize(2); // availability1 and availability3 are AVAILABLE
        assertThat(result).extracting(Availability::getStatus)
                .containsOnly(AvailabilityStatus.AVAILABLE);
        assertThat(result).extracting(Availability::getStartDate)
                .containsExactlyInAnyOrder(
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 1, 16)
                );
    }

    @Test
    @DisplayName("Should find availabilities by accommodation ID and status ordered by start date")
    void testFindByAccommodationIdAndStatusOrderByStartDateAsc() {
        // When
        List<Availability> result = availabilityRepository
                .findByAccommodationIdAndStatusOrderByStartDateAsc(
                        accommodationId1, AvailabilityStatus.AVAILABLE);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Availability::getStatus)
                .containsOnly(AvailabilityStatus.AVAILABLE);
        
        // Verify ordering by start date
        assertThat(result.get(0).getStartDate())
                .isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(result.get(1).getStartDate())
                .isEqualTo(LocalDate.of(2025, 1, 16));
    }

    @Test
    @DisplayName("Should return empty set when no date range overlap exists")
    void testFindByDateRangeOverlap_NoOverlap() {
        // Given - searching for dates that don't overlap with any existing availability
        LocalDate searchStartDate = LocalDate.of(2025, 2, 1);
        LocalDate searchEndDate = LocalDate.of(2025, 2, 10);

        // When
        Set<Availability> result = availabilityRepository
                .findByAccommodationIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        accommodationId1, searchEndDate, searchStartDate);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list when no availabilities match status filter")
    void testFindByAccommodationIdAndStatus_NoMatch() {
        // When - searching for EXPIRED status which doesn't exist in test data
        List<Availability> result = availabilityRepository
                .findByAccommodationIdAndStatusOrderByStartDateAsc(
                        accommodationId1, AvailabilityStatus.EXPIRED);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle edge case - exact date boundary match")
    void testFindByDateRangeOverlap_ExactBoundary() {
        // Given - searching for exact end date of availability1
        LocalDate searchStartDate = LocalDate.of(2025, 1, 7);
        LocalDate searchEndDate = LocalDate.of(2025, 1, 7);

        // When
        Set<Availability> result = availabilityRepository
                .findByAccommodationIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        accommodationId1, searchEndDate, searchStartDate);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getStartDate())
                .isEqualTo(LocalDate.of(2025, 1, 1));
    }

    @Test
    @DisplayName("Should verify repository basic CRUD operations work")
    void testBasicCrudOperations() {
        // Given
        Availability newAvailability = createAvailability(
                accommodationId1,
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 2, 7),
                new BigDecimal("120.00"),
                AvailabilityStatus.AVAILABLE,
                PriceType.SEASONAL
        );

        // When - Save
        Availability saved = availabilityRepository.save(newAvailability);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(availabilityRepository.findById(saved.getId())).isPresent();

        // When - Update
        saved.setPrice(new BigDecimal("130.00"));
        saved.setStatus(AvailabilityStatus.OCCUPIED);
        Availability updated = availabilityRepository.save(saved);

        // Then
        assertThat(updated.getPrice()).isEqualTo(new BigDecimal("130.00"));
        assertThat(updated.getStatus()).isEqualTo(AvailabilityStatus.OCCUPIED);

        // When - Delete
        availabilityRepository.delete(updated);

        // Then
        assertThat(availabilityRepository.findById(saved.getId())).isEmpty();
    }

    private Availability createAvailability(UUID accommodationId, LocalDate startDate, LocalDate endDate,
                                          BigDecimal price, AvailabilityStatus status, PriceType priceType) {
        Availability availability = new Availability();
        availability.setAccommodationId(accommodationId);
        availability.setStartDate(startDate);
        availability.setEndDate(endDate);
        availability.setPrice(price);
        availability.setStatus(status);
        availability.setPriceType(priceType);
        return availability;
    }
}
