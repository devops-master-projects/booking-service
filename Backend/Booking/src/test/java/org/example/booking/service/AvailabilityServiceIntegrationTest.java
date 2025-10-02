package org.example.booking.service;

import org.example.booking.dto.CalendarIntervalDto;
import org.example.booking.model.Availability;
import org.example.booking.model.AvailabilityStatus;
import org.example.booking.model.PriceType;
import org.example.booking.repository.AvailabilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {org.example.booking.BookingApplication.class, org.example.booking.config.TestKafkaConfig.class})
@ActiveProfiles("test")
@Transactional // Rollback after each test
@DisplayName("AvailabilityService Integration Tests")
class AvailabilityServiceIntegrationTest {

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private AvailabilityRepository availabilityRepository;

    private UUID accommodationId;

    @BeforeEach
    void setUp() {
        accommodationId = UUID.randomUUID();
        // Clean up any existing data
        availabilityRepository.deleteAll();
    }

    @Test
    @DisplayName("Should define and retrieve availability through full stack")
    void testDefineAvailabilityIntegration() {
        // Given
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 7);
        BigDecimal price = new BigDecimal("100.00");

        // When - This will actually call repository.save()
        Availability saved = availabilityService.defineAvailability(
                accommodationId, startDate, endDate, price, PriceType.NORMAL);

        // Then - This will actually call repository.findById()
        Availability retrieved = availabilityRepository.findById(saved.getId()).orElse(null);
        
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getAccommodationId()).isEqualTo(accommodationId);
        assertThat(retrieved.getStartDate()).isEqualTo(startDate);
        assertThat(retrieved.getEndDate()).isEqualTo(endDate);
        assertThat(retrieved.getPrice()).isEqualTo(price);
        assertThat(retrieved.getStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should get calendar with real repository queries")
    void testGetCalendarIntegration() {
        // Given - Create test data in database
        availabilityService.defineAvailability(
                accommodationId, 
                LocalDate.of(2025, 1, 1), 
                LocalDate.of(2025, 1, 7),
                new BigDecimal("100.00"), 
                PriceType.NORMAL
        );

        availabilityService.defineAvailability(
                accommodationId,
                LocalDate.of(2025, 1, 15),
                LocalDate.of(2025, 1, 21),
                new BigDecimal("150.00"),
                PriceType.WEEKEND
        );

        // When - This uses real repository queries
        Set<CalendarIntervalDto> calendar = availabilityService.getCalendar(
                accommodationId, 
                LocalDate.of(2025, 1, 1), 
                LocalDate.of(2025, 1, 31)
        );

        // Then
        assertThat(calendar).hasSize(2);
        assertThat(calendar).extracting(CalendarIntervalDto::getStatus)
                .containsOnly("AVAILABLE");
    }

    @Test
    @DisplayName("Should update availability through full stack")
    void testUpdateAvailabilityIntegration() {
        // Given
        Availability original = availabilityService.defineAvailability(
                accommodationId,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 7),
                new BigDecimal("100.00"),
                PriceType.NORMAL
        );

        // When
        availabilityService.updateAvailability(
                original.getId(),
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2025, 1, 8),
                new BigDecimal("120.00"),
                PriceType.WEEKEND
        );

        // Then - Verify in database
        Availability fromDb = availabilityRepository.findById(original.getId()).orElse(null);
        assertThat(fromDb).isNotNull();
        assertThat(fromDb.getStartDate()).isEqualTo(LocalDate.of(2025, 1, 2));
        assertThat(fromDb.getEndDate()).isEqualTo(LocalDate.of(2025, 1, 8));
        assertThat(fromDb.getPrice()).isEqualTo(new BigDecimal("120.00"));
        assertThat(fromDb.getPriceType()).isEqualTo(PriceType.WEEKEND);
    }
}