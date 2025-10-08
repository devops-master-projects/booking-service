package org.example.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.booking.dto.CalendarIntervalDto;
import org.example.booking.model.Availability;
import org.example.booking.model.AvailabilityStatus;
import org.example.booking.model.PriceType;
import org.example.booking.model.ReservationStatus;
import org.example.booking.repository.AvailabilityRepository;
import org.example.booking.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AvailabilityService Tests")
@ActiveProfiles("test")

class AvailabilityServiceTest {

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AvailabilityService availabilityService;

    private UUID accommodationId;
    private Availability testAvailability;

    @BeforeEach
    void setUp() {
        accommodationId = UUID.randomUUID();
        testAvailability = createTestAvailability();
    }

    @Test
    @DisplayName("Should define availability successfully")
    void testDefineAvailability() {
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 7);
        BigDecimal price = new BigDecimal("100.00");
        PriceType priceType = PriceType.NORMAL;

        when(availabilityRepository.save(any(Availability.class))).thenReturn(testAvailability);

        Availability result = availabilityService.defineAvailability(
                accommodationId, startDate, endDate, price, priceType);

        assertThat(result).isNotNull();
        assertThat(result.getAccommodationId()).isEqualTo(accommodationId);
        assertThat(result.getStartDate()).isEqualTo(startDate);
        assertThat(result.getEndDate()).isEqualTo(endDate);
        assertThat(result.getPrice()).isEqualTo(price);
        assertThat(result.getPriceType()).isEqualTo(priceType);
        assertThat(result.getStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);

        verify(availabilityRepository).save(any(Availability.class));
    }

    @Test
    @DisplayName("Should update availability successfully")
    void testUpdateAvailability() {
        UUID availabilityId = UUID.randomUUID();
        LocalDate newStartDate = LocalDate.of(2025, 1, 10);
        LocalDate newEndDate = LocalDate.of(2025, 1, 17);
        BigDecimal newPrice = new BigDecimal("150.00");
        PriceType newPriceType = PriceType.WEEKEND;

        when(availabilityRepository.findById(availabilityId)).thenReturn(Optional.of(testAvailability));
        when(availabilityRepository.save(any(Availability.class))).thenReturn(testAvailability);

        Availability result = availabilityService.updateAvailability(
                availabilityId, newStartDate, newEndDate, newPrice, newPriceType);

        assertThat(result).isNotNull();
        verify(availabilityRepository).findById(availabilityId);
        verify(availabilityRepository).save(testAvailability);
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent availability")
    void testUpdateAvailability_NotFound() {
        UUID availabilityId = UUID.randomUUID();
        when(availabilityRepository.findById(availabilityId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () ->
                availabilityService.updateAvailability(
                        availabilityId, LocalDate.now(), LocalDate.now().plusDays(1), 
                        new BigDecimal("100.00"), PriceType.NORMAL));

        verify(availabilityRepository).findById(availabilityId);
        verifyNoMoreInteractions(availabilityRepository);
    }

    @Test
    @DisplayName("Should get calendar for accommodation")
    void testGetCalendar() {
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 31);

        Set<Availability> availabilities = Set.of(testAvailability);
        when(availabilityRepository.findByAccommodationIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                accommodationId, AvailabilityStatus.AVAILABLE, endDate, startDate))
                .thenReturn(availabilities);

        Set<CalendarIntervalDto> result = availabilityService.getCalendar(accommodationId, startDate, endDate);

        assertThat(result).isNotEmpty();
        verify(availabilityRepository).findByAccommodationIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                accommodationId, AvailabilityStatus.AVAILABLE, endDate, startDate);
    }

    @Test
    @DisplayName("Should get calendar for host view")
    void testGetCalendarHost() {
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 31);

        Set<Availability> availabilities = Set.of(testAvailability);
        when(availabilityRepository.findByAccommodationIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                accommodationId, AvailabilityStatus.AVAILABLE, endDate, startDate))
                .thenReturn(availabilities);
        
        when(reservationRepository.findByRequest_AccommodationIdAndRequest_StartDateLessThanEqualAndRequest_EndDateGreaterThanEqualAndStatus(
                accommodationId, endDate, startDate, ReservationStatus.CONFIRMED))
                .thenReturn(Collections.emptySet());

        Set<CalendarIntervalDto> result = availabilityService.getCalendarHost(accommodationId, startDate, endDate);

        assertThat(result).isNotEmpty();
        verify(availabilityRepository).findByAccommodationIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                accommodationId, AvailabilityStatus.AVAILABLE, endDate, startDate);
        verify(reservationRepository).findByRequest_AccommodationIdAndRequest_StartDateLessThanEqualAndRequest_EndDateGreaterThanEqualAndStatus(
                accommodationId, endDate, startDate, ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Should delete availability successfully")
    void testDeleteAvailability() {
        UUID availabilityId = UUID.randomUUID();
        when(availabilityRepository.findById(availabilityId)).thenReturn(Optional.of(testAvailability));
        when(reservationRepository.existsByRequestAccommodationIdAndRequestStartDateLessThanEqualAndRequestEndDateGreaterThanEqualAndStatus(
                testAvailability.getAccommodationId(), testAvailability.getEndDate(), testAvailability.getStartDate(), ReservationStatus.CONFIRMED))
                .thenReturn(false);

        availabilityService.deleteAvailability(availabilityId);

        verify(availabilityRepository).findById(availabilityId);
        verify(reservationRepository).existsByRequestAccommodationIdAndRequestStartDateLessThanEqualAndRequestEndDateGreaterThanEqualAndStatus(
                testAvailability.getAccommodationId(), testAvailability.getEndDate(), testAvailability.getStartDate(), ReservationStatus.CONFIRMED);
        verify(availabilityRepository).deleteById(availabilityId);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent availability")
    void testDeleteAvailability_NotFound() {
        UUID availabilityId = UUID.randomUUID();
        when(availabilityRepository.findById(availabilityId)).thenReturn(Optional.empty());

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                availabilityService.deleteAvailability(availabilityId));
        
        assertThat(exception.getMessage()).isEqualTo("Availability not found");
        verify(availabilityRepository).findById(availabilityId);
        verifyNoMoreInteractions(availabilityRepository);
        verifyNoInteractions(reservationRepository);
    }

    @Test
    @DisplayName("Should throw exception when deleting availability with active reservations")
    void testDeleteAvailability_WithActiveReservations() {
        UUID availabilityId = UUID.randomUUID();
        when(availabilityRepository.findById(availabilityId)).thenReturn(Optional.of(testAvailability));
        when(reservationRepository.existsByRequestAccommodationIdAndRequestStartDateLessThanEqualAndRequestEndDateGreaterThanEqualAndStatus(
                testAvailability.getAccommodationId(), testAvailability.getEndDate(), testAvailability.getStartDate(), ReservationStatus.CONFIRMED))
                .thenReturn(true);

        Exception exception = assertThrows(IllegalStateException.class, () ->
                availabilityService.deleteAvailability(availabilityId));
        
        assertThat(exception.getMessage()).isEqualTo("Cannot delete availability with active reservations");
        verify(availabilityRepository).findById(availabilityId);
        verify(reservationRepository).existsByRequestAccommodationIdAndRequestStartDateLessThanEqualAndRequestEndDateGreaterThanEqualAndStatus(
                testAvailability.getAccommodationId(), testAvailability.getEndDate(), testAvailability.getStartDate(), ReservationStatus.CONFIRMED);
        verifyNoMoreInteractions(availabilityRepository);
    }

    private Availability createTestAvailability() {
        return createAvailability(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 7));
    }

    private Availability createAvailability(LocalDate startDate, LocalDate endDate) {
        Availability availability = new Availability();
        availability.setId(UUID.randomUUID());
        availability.setAccommodationId(accommodationId);
        availability.setStartDate(startDate);
        availability.setEndDate(endDate);
        availability.setPrice(new BigDecimal("100.00"));
        availability.setStatus(AvailabilityStatus.AVAILABLE);
        availability.setPriceType(PriceType.NORMAL);
        return availability;
    }
}