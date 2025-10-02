package org.example.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.booking.dto.*;
import org.example.booking.model.*;
import org.example.booking.repository.AvailabilityRepository;
import org.example.booking.repository.ReservationRepository;
import org.example.booking.repository.ReservationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationRequestService Tests")
public class ReservationRequestServiceTest {

    @Mock
    private ReservationRequestRepository repository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ReservationRequestService service;

    private UUID guestId;
    private UUID accommodationId;
    private UUID requestId;
    private ReservationRequest testRequest;
    private ReservationRequestCreateDto createDto;

    @BeforeEach
    void setUp() {
        guestId = UUID.randomUUID();
        accommodationId = UUID.randomUUID();
        requestId = UUID.randomUUID();

        // Set the accommodation service URL via reflection
        ReflectionTestUtils.setField(service, "accommodationServiceUrl", "http://localhost:8085");

        testRequest = createTestRequest();
        createDto = createTestCreateDto();
    }

    @Test
    @DisplayName("Should create reservation request with auto-confirm enabled")
    void testCreate_WithAutoConfirm() {
        // Given
        AutoConfirmResponse autoConfirmResponse = new AutoConfirmResponse();
        autoConfirmResponse.setAutoConfirm(true);

        when(repository.save(any(ReservationRequest.class)))
                .thenReturn(testRequest)
                .thenReturn(testRequest); // For the second save in updateStatus
        when(restTemplate.getForObject(anyString(), eq(AutoConfirmResponse.class)))
                .thenReturn(autoConfirmResponse);
        when(repository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));
        when(repository.findByAccommodationIdAndStatus(accommodationId, RequestStatus.PENDING))
                .thenReturn(Collections.emptyList());
        when(availabilityRepository.findByAccommodationId(accommodationId))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findByRequest_Id(testRequest.getId()))
                .thenReturn(Optional.empty());
        when(reservationRepository.countByRequest_GuestIdAndStatus(guestId, ReservationStatus.CANCELLED))
                .thenReturn(0);

        // When
        ReservationRequestResponseDto result = service.create(guestId, createDto);

        // Then
        assertThat(result).isNotNull();
        verify(repository, times(2)).save(any(ReservationRequest.class));
        verify(restTemplate).getForObject(contains("/auto-confirm"), eq(AutoConfirmResponse.class));
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    @DisplayName("Should create reservation request without auto-confirm")
    void testCreate_WithoutAutoConfirm() {
        // Given
        AutoConfirmResponse autoConfirmResponse = new AutoConfirmResponse();
        autoConfirmResponse.setAutoConfirm(false);

        when(repository.save(any(ReservationRequest.class))).thenReturn(testRequest);
        when(restTemplate.getForObject(anyString(), eq(AutoConfirmResponse.class)))
                .thenReturn(autoConfirmResponse);
        when(reservationRepository.countByRequest_GuestIdAndStatus(guestId, ReservationStatus.CANCELLED))
                .thenReturn(0);
        when(reservationRepository.findByRequest_Id(testRequest.getId()))
                .thenReturn(Optional.empty());

        // When
        ReservationRequestResponseDto result = service.create(guestId, createDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(RequestStatus.PENDING);
        verify(repository).save(any(ReservationRequest.class));
        verify(restTemplate).getForObject(contains("/auto-confirm"), eq(AutoConfirmResponse.class));
    }

    @Test
    @DisplayName("Should delete pending reservation request")
    void testDelete_PendingRequest() {
        // Given
        when(repository.findById(requestId)).thenReturn(Optional.of(testRequest));

        // When
        service.delete(requestId);

        // Then
        verify(repository).findById(requestId);
        verify(repository).deleteById(requestId);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-pending request")
    void testDelete_NonPendingRequest() {
        // Given
        ReservationRequest approvedRequest = createTestRequest();
        approvedRequest.setStatus(RequestStatus.APPROVED);
        when(repository.findById(requestId)).thenReturn(Optional.of(approvedRequest));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.delete(requestId));

        assertThat(exception.getMessage()).isEqualTo("Only PENDING requests can be deleted");
        verify(repository).findById(requestId);
        verify(repository, never()).deleteById(requestId);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent request")
    void testDelete_RequestNotFound() {
        // Given
        when(repository.findById(requestId)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.delete(requestId));

        assertThat(exception.getMessage()).isEqualTo("Request not found");
        verify(repository).findById(requestId);
        verify(repository, never()).deleteById(requestId);
    }

    @Test
    @DisplayName("Should find requests by guest and accommodation")
    void testFindByGuest() {
        // Given
        List<ReservationRequest> requests = Arrays.asList(testRequest);
        when(repository.findByGuestIdAndAccommodationId(guestId, accommodationId))
                .thenReturn(requests);
        when(reservationRepository.findByRequest_Id(testRequest.getId()))
                .thenReturn(Optional.empty());
        when(reservationRepository.countByRequest_GuestIdAndStatus(guestId, ReservationStatus.CANCELLED))
                .thenReturn(1);

        // When
        List<ReservationRequestResponseDto> result = service.findByGuest(guestId, accommodationId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGuestId()).isEqualTo(guestId);
        assertThat(result.get(0).getAccommodationId()).isEqualTo(accommodationId);
        assertThat(result.get(0).isConnectedReservationCancelled()).isFalse();
        assertThat(result.get(0).getCancellationsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should find requests by accommodation")
    void testFindByAccommodation() {
        // Given
        List<ReservationRequest> requests = Arrays.asList(testRequest);
        when(repository.findByAccommodationId(accommodationId)).thenReturn(requests);
        when(reservationRepository.findByRequest_Id(testRequest.getId()))
                .thenReturn(Optional.empty());
        when(reservationRepository.countByRequest_GuestIdAndStatus(guestId, ReservationStatus.CANCELLED))
                .thenReturn(0);

        // When
        List<ReservationRequestResponseDto> result = service.findByAccommodation(accommodationId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccommodationId()).isEqualTo(accommodationId);
    }

    @Test
    @DisplayName("Should update status to APPROVED and create reservation")
    void testUpdateStatus_ToApproved() {
        // Given
        when(repository.findById(requestId)).thenReturn(Optional.of(testRequest));
        when(repository.findByAccommodationIdAndStatus(accommodationId, RequestStatus.PENDING))
                .thenReturn(Collections.emptyList());
        when(availabilityRepository.findByAccommodationId(accommodationId))
                .thenReturn(Collections.emptyList());

        ReservationRequest updatedRequest = createTestRequest();
        updatedRequest.setStatus(RequestStatus.APPROVED);
        when(repository.save(any(ReservationRequest.class))).thenReturn(updatedRequest);
        when(reservationRepository.findByRequest_Id(requestId)).thenReturn(Optional.empty());
        when(reservationRepository.countByRequest_GuestIdAndStatus(guestId, ReservationStatus.CANCELLED))
                .thenReturn(0);

        // When
        ReservationRequestResponseDto result = service.updateStatus(requestId, RequestStatus.APPROVED);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(RequestStatus.APPROVED);
        verify(repository).findById(requestId);
        verify(repository).save(any(ReservationRequest.class));
        verify(reservationRepository).save(any(Reservation.class));
        verify(repository).findByAccommodationIdAndStatus(accommodationId, RequestStatus.PENDING);
        verify(availabilityRepository).findByAccommodationId(accommodationId);
    }

    @Test
    @DisplayName("Should reject overlapping requests when approving")
    void testUpdateStatus_RejectOverlapping() {
        // Given
        ReservationRequest overlappingRequest = createTestRequest();
        overlappingRequest.setId(UUID.randomUUID());
        overlappingRequest.setStartDate(LocalDate.of(2025, 1, 3)); // Overlaps with main request (1-7)
        overlappingRequest.setEndDate(LocalDate.of(2025, 1, 10));

        when(repository.findById(requestId)).thenReturn(Optional.of(testRequest));
        when(repository.findByAccommodationIdAndStatus(accommodationId, RequestStatus.PENDING))
                .thenReturn(Arrays.asList(overlappingRequest));
        when(availabilityRepository.findByAccommodationId(accommodationId))
                .thenReturn(Collections.emptyList());

        ReservationRequest updatedRequest = createTestRequest();
        updatedRequest.setStatus(RequestStatus.APPROVED);
        when(repository.save(any(ReservationRequest.class))).thenReturn(updatedRequest);
        when(reservationRepository.findByRequest_Id(requestId)).thenReturn(Optional.empty());
        when(reservationRepository.countByRequest_GuestIdAndStatus(guestId, ReservationStatus.CANCELLED))
                .thenReturn(0);

        // When
        ReservationRequestResponseDto result = service.updateStatus(requestId, RequestStatus.APPROVED);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(RequestStatus.APPROVED);
        verify(repository, times(2)).save(any(ReservationRequest.class)); // Main request + overlapping
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    @DisplayName("Should update reservation request details")
    void testUpdate() {
        // Given
        ReservationRequestUpdateDto updateDto = new ReservationRequestUpdateDto();
        updateDto.setStartDate(LocalDate.of(2025, 2, 1));
        updateDto.setEndDate(LocalDate.of(2025, 2, 7));
        updateDto.setGuestCount(3);

        when(repository.findById(requestId)).thenReturn(Optional.of(testRequest));

        ReservationRequest updatedRequest = createTestRequest();
        updatedRequest.setStartDate(updateDto.getStartDate());
        updatedRequest.setEndDate(updateDto.getEndDate());
        updatedRequest.setGuestCount(updateDto.getGuestCount());
        when(repository.save(any(ReservationRequest.class))).thenReturn(updatedRequest);
        when(reservationRepository.findByRequest_Id(requestId)).thenReturn(Optional.empty());
        when(reservationRepository.countByRequest_GuestIdAndStatus(guestId, ReservationStatus.CANCELLED))
                .thenReturn(0);

        // When
        ReservationRequestResponseDto result = service.update(requestId, updateDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStartDate()).isEqualTo(updateDto.getStartDate());
        assertThat(result.getEndDate()).isEqualTo(updateDto.getEndDate());
        assertThat(result.getGuestCount()).isEqualTo(updateDto.getGuestCount());
        verify(repository).findById(requestId);
        verify(repository).save(any(ReservationRequest.class));
    }

    @Test
    @DisplayName("Should throw exception when updating non-pending request")
    void testUpdate_NonPendingRequest() {
        // Given
        ReservationRequest approvedRequest = createTestRequest();
        approvedRequest.setStatus(RequestStatus.APPROVED);
        when(repository.findById(requestId)).thenReturn(Optional.of(approvedRequest));

        ReservationRequestUpdateDto updateDto = new ReservationRequestUpdateDto();
        updateDto.setStartDate(LocalDate.of(2025, 2, 1));
        updateDto.setEndDate(LocalDate.of(2025, 2, 7));
        updateDto.setGuestCount(3);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.update(requestId, updateDto));

        assertThat(exception.getMessage()).isEqualTo("Only PENDING requests can be updated");
        verify(repository).findById(requestId);
        verify(repository, never()).save(any(ReservationRequest.class));
    }

    @Test
    @DisplayName("Should cancel reservation and restore availability")
    void testCancelReservation() {
        // Given
        ReservationRequest futureRequest = createTestRequest();
        futureRequest.setStartDate(LocalDate.now().plusDays(5));
        futureRequest.setEndDate(LocalDate.now().plusDays(10));

        Reservation reservation = new Reservation();
        reservation.setRequest(futureRequest);
        reservation.setStatus(ReservationStatus.CONFIRMED);

        when(repository.findById(requestId)).thenReturn(Optional.of(futureRequest));
        when(reservationRepository.findByRequest_Id(requestId)).thenReturn(Optional.of(reservation));
        when(availabilityRepository.findByAccommodationId(accommodationId))
                .thenReturn(Collections.emptyList());
        when(availabilityRepository.findByAccommodationIdAndStatusOrderByStartDateAsc(
                accommodationId, AvailabilityStatus.AVAILABLE))
                .thenReturn(Collections.emptyList());

        // When
        service.cancelReservation(requestId);

        // Then
        verify(repository).findById(requestId);
        verify(reservationRepository).findByRequest_Id(requestId);
        verify(reservationRepository).save(reservation);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should throw exception when canceling too late")
    void testCancelReservation_TooLate() {
        // Given - Request starting yesterday (definitely too late)
        ReservationRequest pastRequest = createTestRequest();
        pastRequest.setStartDate(LocalDate.now().minusDays(1)); // Yesterday
        pastRequest.setEndDate(LocalDate.now().plusDays(3));

        when(repository.findById(requestId)).thenReturn(Optional.of(pastRequest));

        // When & Then
        assertThrows(RuntimeException.class, () ->
                service.cancelReservation(requestId));

        verify(repository).findById(requestId);
    }

    @Test
    @DisplayName("Should merge adjacent availabilities")
    void testMergeAdjacentAvailabilities() {
        // Given
        Availability availability1 = createTestAvailability();
        availability1.setStartDate(LocalDate.of(2025, 1, 1));
        availability1.setEndDate(LocalDate.of(2025, 1, 5));

        Availability availability2 = createTestAvailability();
        availability2.setStartDate(LocalDate.of(2025, 1, 6)); // Adjacent to availability1
        availability2.setEndDate(LocalDate.of(2025, 1, 10));
        availability2.setPrice(availability1.getPrice()); // Same price
        availability2.setPriceType(availability1.getPriceType()); // Same price type

        when(availabilityRepository.findByAccommodationIdAndStatusOrderByStartDateAsc(
                accommodationId, AvailabilityStatus.AVAILABLE))
                .thenReturn(Arrays.asList(availability1, availability2));

        // When
        service.mergeAdjacentAvailabilities(accommodationId);

        // Then
        verify(availabilityRepository).findByAccommodationIdAndStatusOrderByStartDateAsc(
                accommodationId, AvailabilityStatus.AVAILABLE);
        verify(availabilityRepository).delete(availability2); // Second availability should be deleted
        assertThat(availability1.getEndDate()).isEqualTo(LocalDate.of(2025, 1, 10)); // Merged end date
    }

    @Test
    @DisplayName("Should adjust availabilities - scenario 1: reservation completely covers availability")
    void testUpdateStatus_AvailabilityScenario1_CompleteOverlap() throws Exception {
        // Given - reservation (Jan 1-10) completely covers availability (Jan 3-7)
        ReservationRequest request = createTestRequest();
        request.setStartDate(LocalDate.of(2025, 1, 1));
        request.setEndDate(LocalDate.of(2025, 1, 10));

        Availability availability = createTestAvailability();
        availability.setStartDate(LocalDate.of(2025, 1, 3));
        availability.setEndDate(LocalDate.of(2025, 1, 7));

        when(repository.findById(requestId)).thenReturn(Optional.of(request));
        when(repository.findByAccommodationIdAndStatus(accommodationId, RequestStatus.PENDING))
                .thenReturn(Collections.emptyList());
        when(availabilityRepository.findByAccommodationId(accommodationId))
                .thenReturn(Arrays.asList(availability));
        when(repository.save(any(ReservationRequest.class))).thenReturn(request);
        when(reservationRepository.findByRequest_Id(requestId)).thenReturn(Optional.empty());
        when(reservationRepository.countByRequest_GuestIdAndStatus(guestId, ReservationStatus.CANCELLED))
                .thenReturn(0);
        
        // Mock availability save to set ID when saving new entities
        when(availabilityRepository.save(any(Availability.class))).thenAnswer(invocation -> {
            Availability saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"mockEvent\":\"data\"}");

        // When
        service.updateStatus(requestId, RequestStatus.APPROVED);

        // Then - availability should be marked as OCCUPIED
        verify(availabilityRepository).save(availability);
        assertThat(availability.getStatus()).isEqualTo(AvailabilityStatus.OCCUPIED);
        verify(kafkaTemplate, atLeastOnce()).send(eq("availability-events"), anyString(), eq("{\"mockEvent\":\"data\"}"));
    }

    @Test
    @DisplayName("Should adjust availabilities - scenario 2: reservation within availability (split into 3)")
    void testUpdateStatus_AvailabilityScenario2_SplitAvailability() throws Exception {
        // Given - reservation (Jan 5-7) within availability (Jan 1-10)
        ReservationRequest request = createTestRequest();
        request.setStartDate(LocalDate.of(2025, 1, 5));
        request.setEndDate(LocalDate.of(2025, 1, 7));

        Availability availability = createTestAvailability();
        availability.setStartDate(LocalDate.of(2025, 1, 1));
        availability.setEndDate(LocalDate.of(2025, 1, 10));

        when(repository.findById(requestId)).thenReturn(Optional.of(request));
        when(repository.findByAccommodationIdAndStatus(accommodationId, RequestStatus.PENDING))
                .thenReturn(Collections.emptyList());
        when(availabilityRepository.findByAccommodationId(accommodationId))
                .thenReturn(Arrays.asList(availability));
        when(repository.save(any(ReservationRequest.class))).thenReturn(request);
        when(reservationRepository.findByRequest_Id(requestId)).thenReturn(Optional.empty());
        when(reservationRepository.countByRequest_GuestIdAndStatus(guestId, ReservationStatus.CANCELLED))
                .thenReturn(0);
        
        // Mock availability save to set ID when saving new entities
        when(availabilityRepository.save(any(Availability.class))).thenAnswer(invocation -> {
            Availability saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"mockEvent\":\"data\"}");

        // When
        service.updateStatus(requestId, RequestStatus.APPROVED);

        // Then - original availability should be deleted, 3 new ones created
        verify(availabilityRepository).delete(availability);
        verify(availabilityRepository, times(3)).save(any(Availability.class)); // left, occupied, right
        verify(kafkaTemplate, times(4)).send(eq("availability-events"), anyString(), eq("{\"mockEvent\":\"data\"}")); // 1 delete + 3 creates
    }

    @Test
    @DisplayName("Should adjust availabilities - scenario 3: overlap at beginning")
    void testUpdateStatus_AvailabilityScenario3_OverlapBeginning() throws Exception {
        // Given - reservation (Jan 1-5) overlaps beginning of availability (Jan 3-10)
        ReservationRequest request = createTestRequest();
        request.setStartDate(LocalDate.of(2025, 1, 1));
        request.setEndDate(LocalDate.of(2025, 1, 5));

        Availability availability = createTestAvailability();
        availability.setStartDate(LocalDate.of(2025, 1, 3));
        availability.setEndDate(LocalDate.of(2025, 1, 10));

        when(repository.findById(requestId)).thenReturn(Optional.of(request));
        when(repository.findByAccommodationIdAndStatus(accommodationId, RequestStatus.PENDING))
                .thenReturn(Collections.emptyList());
        when(availabilityRepository.findByAccommodationId(accommodationId))
                .thenReturn(Arrays.asList(availability));
        when(repository.save(any(ReservationRequest.class))).thenReturn(request);
        when(reservationRepository.findByRequest_Id(requestId)).thenReturn(Optional.empty());
        when(reservationRepository.countByRequest_GuestIdAndStatus(guestId, ReservationStatus.CANCELLED))
                .thenReturn(0);
        
        // Mock availability save to set ID when saving new entities
        when(availabilityRepository.save(any(Availability.class))).thenAnswer(invocation -> {
            Availability saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"mockEvent\":\"data\"}");

        // When
        service.updateStatus(requestId, RequestStatus.APPROVED);

        // Then - availability start date should be adjusted, occupied period created
        verify(availabilityRepository, times(2)).save(any(Availability.class)); // modified availability + occupied
        assertThat(availability.getStartDate()).isEqualTo(LocalDate.of(2025, 1, 6)); // Adjusted start date
        verify(kafkaTemplate, times(2)).send(eq("availability-events"), anyString(), eq("{\"mockEvent\":\"data\"}"));
    }

    @Test
    @DisplayName("Should adjust availabilities - scenario 4: overlap at end")
    void testUpdateStatus_AvailabilityScenario4_OverlapEnd() throws Exception {
        // Given - reservation (Jan 5-10) overlaps end of availability (Jan 1-7)
        ReservationRequest request = createTestRequest();
        request.setStartDate(LocalDate.of(2025, 1, 5));
        request.setEndDate(LocalDate.of(2025, 1, 10));

        Availability availability = createTestAvailability();
        availability.setStartDate(LocalDate.of(2025, 1, 1));
        availability.setEndDate(LocalDate.of(2025, 1, 7));

        when(repository.findById(requestId)).thenReturn(Optional.of(request));
        when(repository.findByAccommodationIdAndStatus(accommodationId, RequestStatus.PENDING))
                .thenReturn(Collections.emptyList());
        when(availabilityRepository.findByAccommodationId(accommodationId))
                .thenReturn(Arrays.asList(availability));
        when(repository.save(any(ReservationRequest.class))).thenReturn(request);
        when(reservationRepository.findByRequest_Id(requestId)).thenReturn(Optional.empty());
        when(reservationRepository.countByRequest_GuestIdAndStatus(guestId, ReservationStatus.CANCELLED))
                .thenReturn(0);
        
        // Mock availability save to set ID when saving new entities
        when(availabilityRepository.save(any(Availability.class))).thenAnswer(invocation -> {
            Availability saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"mockEvent\":\"data\"}");

        // When
        service.updateStatus(requestId, RequestStatus.APPROVED);

        // Then - availability end date should be adjusted, occupied period created
        verify(availabilityRepository, times(2)).save(any(Availability.class)); // modified availability + occupied
        assertThat(availability.getEndDate()).isEqualTo(LocalDate.of(2025, 1, 4)); // Adjusted end date
        verify(kafkaTemplate, times(2)).send(eq("availability-events"), anyString(), eq("{\"mockEvent\":\"data\"}"));
    }

    @Test
    @DisplayName("Should handle non-overlapping availabilities")
    void testUpdateStatus_AvailabilityScenario_NoOverlap() {
        // Given - reservation (Jan 5-7) doesn't overlap with availability (Jan 10-15)
        ReservationRequest request = createTestRequest();
        request.setStartDate(LocalDate.of(2025, 1, 5));
        request.setEndDate(LocalDate.of(2025, 1, 7));

        Availability availability = createTestAvailability();
        availability.setStartDate(LocalDate.of(2025, 1, 10));
        availability.setEndDate(LocalDate.of(2025, 1, 15));

        when(repository.findById(requestId)).thenReturn(Optional.of(request));
        when(repository.findByAccommodationIdAndStatus(accommodationId, RequestStatus.PENDING))
                .thenReturn(Collections.emptyList());
        when(availabilityRepository.findByAccommodationId(accommodationId))
                .thenReturn(Arrays.asList(availability));
        when(repository.save(any(ReservationRequest.class))).thenReturn(request);
        when(reservationRepository.findByRequest_Id(requestId)).thenReturn(Optional.empty());
        when(reservationRepository.countByRequest_GuestIdAndStatus(guestId, ReservationStatus.CANCELLED))
                .thenReturn(0);

        // When
        service.updateStatus(requestId, RequestStatus.APPROVED);

        // Then - availability should not be modified
        verify(availabilityRepository, never()).save(availability);
        verify(availabilityRepository, never()).delete(availability);
    }

    @Test
    @DisplayName("Should handle update status to REJECTED")
    void testUpdateStatus_ToRejected() {
        // Given
        when(repository.findById(requestId)).thenReturn(Optional.of(testRequest));
        
        ReservationRequest rejectedRequest = createTestRequest();
        rejectedRequest.setStatus(RequestStatus.REJECTED);
        when(repository.save(any(ReservationRequest.class))).thenReturn(rejectedRequest);
        when(reservationRepository.findByRequest_Id(requestId)).thenReturn(Optional.empty());
        when(reservationRepository.countByRequest_GuestIdAndStatus(guestId, ReservationStatus.CANCELLED))
                .thenReturn(0);

        // When
        ReservationRequestResponseDto result = service.updateStatus(requestId, RequestStatus.REJECTED);

        // Then
        assertThat(result.getStatus()).isEqualTo(RequestStatus.REJECTED);
        verify(repository).save(any(ReservationRequest.class));
        // No availability adjustments or reservations should be created for REJECTED
        verifyNoInteractions(availabilityRepository);
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    @DisplayName("Should throw exception when updating status of non-existent request")
    void testUpdateStatus_RequestNotFound() {
        // Given
        when(repository.findById(requestId)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.updateStatus(requestId, RequestStatus.APPROVED));

        assertThat(exception.getMessage()).isEqualTo("Request not found");
        verify(repository).findById(requestId);
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("Should find by guest with cancelled reservation")
    void testFindByGuest_WithCancelledReservation() {
        // Given
        List<ReservationRequest> requests = Arrays.asList(testRequest);
        
        Reservation cancelledReservation = new Reservation();
        cancelledReservation.setStatus(ReservationStatus.CANCELLED);
        
        when(repository.findByGuestIdAndAccommodationId(guestId, accommodationId))
                .thenReturn(requests);
        when(reservationRepository.findByRequest_Id(testRequest.getId()))
                .thenReturn(Optional.of(cancelledReservation));
        when(reservationRepository.countByRequest_GuestIdAndStatus(guestId, ReservationStatus.CANCELLED))
                .thenReturn(2);

        // When
        List<ReservationRequestResponseDto> result = service.findByGuest(guestId, accommodationId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isConnectedReservationCancelled()).isTrue();
        assertThat(result.get(0).getCancellationsCount()).isEqualTo(2);
    }



    // Helper methods for creating test data
    private ReservationRequest createTestRequest() {
        ReservationRequest request = new ReservationRequest();
        request.setId(requestId);
        request.setGuestId(guestId);
        request.setAccommodationId(accommodationId);
        request.setStartDate(LocalDate.of(2025, 1, 1));
        request.setEndDate(LocalDate.of(2025, 1, 7));
        request.setGuestCount(2);
        request.setStatus(RequestStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());
        return request;
    }

    private ReservationRequestCreateDto createTestCreateDto() {
        ReservationRequestCreateDto dto = new ReservationRequestCreateDto();
        dto.setAccommodationId(accommodationId);
        dto.setStartDate(LocalDate.of(2025, 1, 1));
        dto.setEndDate(LocalDate.of(2025, 1, 7));
        dto.setGuestCount(2);
        return dto;
    }

    private Availability createTestAvailability() {
        Availability availability = new Availability();
        availability.setId(UUID.randomUUID());
        availability.setAccommodationId(accommodationId);
        availability.setStartDate(LocalDate.of(2025, 1, 1));
        availability.setEndDate(LocalDate.of(2025, 1, 7));
        availability.setPrice(new BigDecimal("100.00"));
        availability.setPriceType(PriceType.NORMAL);
        availability.setStatus(AvailabilityStatus.AVAILABLE);
        return availability;
    }
}
