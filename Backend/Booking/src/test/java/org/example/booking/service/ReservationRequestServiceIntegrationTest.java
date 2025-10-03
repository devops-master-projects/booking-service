package org.example.booking.service;

import org.example.booking.dto.ReservationCreatedEvent;
import org.example.booking.dto.ReservationRequestCreateDto;
import org.example.booking.dto.ReservationRequestResponseDto;
import org.example.booking.dto.ReservationRequestUpdateDto;
import org.example.booking.model.*;
import org.example.booking.repository.AvailabilityRepository;
import org.example.booking.repository.ReservationRepository;
import org.example.booking.repository.ReservationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "accommodation.service.url=http://accommodation-service"
})
@Transactional
class ReservationRequestServiceIntegrationTest {

    @Autowired
    private ReservationRequestService service;

    @Autowired
    private ReservationRequestRepository requestRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private AvailabilityRepository availabilityRepository;

    @Autowired
    private RestTemplate restTemplate; // real or conditional bean


    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate; // mocked kafka template
    @MockitoBean
    private KafkaTemplate<String, ReservationCreatedEvent> reservationKafkaTemplate;


    @TestConfiguration
    static class RestTemplateConfig {
        @Bean
        @ConditionalOnMissingBean(RestTemplate.class)
        RestTemplate restTemplate() {
            return new RestTemplate();
        }
    }

    private MockRestServiceServer server;

    private UUID accommodationId;
    private UUID guestId;

    @BeforeEach
    void setup() {
        server = MockRestServiceServer.createServer(restTemplate);
        accommodationId = UUID.randomUUID();
        guestId = UUID.randomUUID();
    }

    private String autoConfirmUrl() {
        return "http://accommodation-service/api/accommodations/" + accommodationId + "/auto-confirm";
    }

    private void stubAutoConfirm(boolean value) {
        String body = "{\"autoConfirm\":" + value + "}"; // matches AutoConfirmResponse
        server.expect(requestTo(autoConfirmUrl()))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("create(): returns PENDING when autoConfirm=false")
    void testCreatePending() {
        stubAutoConfirm(false);

        ReservationRequestCreateDto dto = new ReservationRequestCreateDto(
                accommodationId,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(7),
                2
        );

        ReservationRequestResponseDto response = service.create(
                guestId,
                "guest@mail.com",
                "Doe",
                "John",
                dto
        );

        assertThat(response.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(requestRepository.findById(response.getId())).isPresent();
        server.verify();
    }


    @Test
    @DisplayName("create(): autoConfirm=true triggers APPROVED status and reservation creation")
    void testCreateAutoConfirmed() {
        stubAutoConfirm(true);
        ReservationRequestCreateDto dto = new ReservationRequestCreateDto(
                accommodationId,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(12),
                2
        );

        ReservationRequestResponseDto response = service.create(
                guestId,
                "guest@mail.com",
                "Doe",
                "John",
                dto
        );
        assertThat(response.getStatus()).isEqualTo(RequestStatus.APPROVED);
        // reservation should exist
        assertThat(reservationRepository.findByRequest_Id(response.getId())).isPresent();
        server.verify();
    }

    @Test
    @DisplayName("updateStatus(APPROVED): rejects overlapping pending requests and creates reservation")
    void testUpdateStatusApproveAndRejectOthers() {
        // Prepare two overlapping pending requests
        LocalDate start = LocalDate.now().plusDays(20);
        LocalDate end = start.plusDays(3);

    ReservationRequest r1 = new ReservationRequest();
        r1.setGuestId(guestId);
        r1.setAccommodationId(accommodationId);
        r1.setStartDate(start);
        r1.setEndDate(end);
        r1.setGuestCount(2);
        r1.setStatus(RequestStatus.PENDING);
    requestRepository.saveAndFlush(r1);

    ReservationRequest r2 = new ReservationRequest();
        r2.setGuestId(UUID.randomUUID());
        r2.setAccommodationId(accommodationId);
        r2.setStartDate(start.plusDays(1)); // overlapping
        r2.setEndDate(end.plusDays(1));
        r2.setGuestCount(3);
        r2.setStatus(RequestStatus.PENDING);
    requestRepository.saveAndFlush(r2);

        // Availabilities for reservation creation side-effects
    Availability availability = new Availability();
        availability.setAccommodationId(accommodationId);
        availability.setStartDate(start.minusDays(1));
        availability.setEndDate(end.plusDays(2));
        availability.setPrice(BigDecimal.valueOf(100));
    availability.setPriceType(PriceType.NORMAL);
        availability.setStatus(AvailabilityStatus.AVAILABLE);
        availabilityRepository.save(availability);

        ReservationRequestResponseDto approved = service.updateStatus(
                r1.getId(),
                RequestStatus.APPROVED,
                "Alice",
                "Smith"
        );
        assertThat(approved.getStatus()).isEqualTo(RequestStatus.APPROVED);
        assertThat(reservationRepository.findByRequest_Id(r1.getId())).isPresent();
        // r2 should be rejected
        ReservationRequest r2Reloaded = requestRepository.findById(r2.getId()).orElseThrow();
        assertThat(r2Reloaded.getStatus()).isEqualTo(RequestStatus.REJECTED);

        // availability should have been adjusted (some OCCUPIED piece exists)
        List<Availability> all = availabilityRepository.findByAccommodationId(accommodationId);
        assertThat(all.stream().anyMatch(a -> a.getStatus() == AvailabilityStatus.OCCUPIED)).isTrue();
    }

    @Test
    @DisplayName("delete(): only PENDING allowed")
    void testDeletePendingOnly() {
        // Pending request (deletable)
    ReservationRequest pending = new ReservationRequest();
        pending.setGuestId(guestId);
        pending.setAccommodationId(accommodationId);
        pending.setStartDate(LocalDate.now().plusDays(30));
        pending.setEndDate(LocalDate.now().plusDays(32));
        pending.setGuestCount(1);
        pending.setStatus(RequestStatus.PENDING);
    requestRepository.saveAndFlush(pending);

    service.delete(pending.getId());
        assertThat(requestRepository.findById(pending.getId())).isEmpty();

        // Non-pending
    ReservationRequest approved = new ReservationRequest();
        approved.setGuestId(guestId);
        approved.setAccommodationId(accommodationId);
        approved.setStartDate(LocalDate.now().plusDays(40));
        approved.setEndDate(LocalDate.now().plusDays(41));
        approved.setGuestCount(2);
        approved.setStatus(RequestStatus.APPROVED);
    requestRepository.saveAndFlush(approved);

        assertThatThrownBy(() -> service.delete(approved.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("cancelReservation(): success path marks reservation cancelled and frees availabilities")
    void testCancelReservationSuccess() {
        LocalDate start = LocalDate.now().plusDays(15);
        LocalDate end = start.plusDays(2);

        // Request
    ReservationRequest req = new ReservationRequest();
        req.setGuestId(guestId);
        req.setAccommodationId(accommodationId);
        req.setStartDate(start);
        req.setEndDate(end);
        req.setGuestCount(2);
        req.setStatus(RequestStatus.APPROVED);
    requestRepository.saveAndFlush(req);

        // Reservation
    Reservation reservation = new Reservation();
        reservation.setRequest(req);
        reservation.setConfirmedAt(LocalDateTime.now());
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation);

        // OCCUPIED availability overlapping
    Availability occ = new Availability();
        occ.setAccommodationId(accommodationId);
        occ.setStartDate(start);
        occ.setEndDate(end);
        occ.setPrice(BigDecimal.valueOf(150));
    occ.setPriceType(PriceType.NORMAL);
        occ.setStatus(AvailabilityStatus.OCCUPIED);
        availabilityRepository.save(occ);

    service.cancelReservation(req.getId());

        Reservation reservationReloaded = reservationRepository.findByRequest_Id(req.getId()).orElseThrow();
        assertThat(reservationReloaded.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        Availability freed = availabilityRepository.findByAccommodationId(accommodationId).stream()
                .filter(a -> a.getId().equals(occ.getId()))
                .findFirst().orElseThrow();
        assertThat(freed.getStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    @DisplayName("cancelReservation(): too late to cancel")
    void testCancelReservationTooLate() {
        // To trigger the 'Too late to cancel reservation' condition the current date must be AFTER (start - 1 day).
        // If start = today, then (start - 1 day) is yesterday, so now().isAfter(yesterday) => true and exception is thrown.
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(2);

    ReservationRequest req = new ReservationRequest();
        req.setGuestId(guestId);
        req.setAccommodationId(accommodationId);
        req.setStartDate(start);
        req.setEndDate(end);
        req.setGuestCount(1);
        req.setStatus(RequestStatus.APPROVED);
    requestRepository.saveAndFlush(req);

    Reservation reservation = new Reservation();
        reservation.setRequest(req);
        reservation.setConfirmedAt(LocalDateTime.now());
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation);

    assertThatThrownBy(() -> service.cancelReservation(req.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Too late");
    }

    @Test
    @DisplayName("mergeAdjacentAvailabilities(): merges intervals with same price and type")
    void testMergeAdjacentAvailabilities() {
        // Two adjacent AVAILABLE intervals
    Availability a1 = new Availability();
        a1.setAccommodationId(accommodationId);
        a1.setStartDate(LocalDate.now().plusDays(50));
        a1.setEndDate(LocalDate.now().plusDays(52));
        a1.setPrice(BigDecimal.valueOf(100));
    a1.setPriceType(PriceType.NORMAL);
        a1.setStatus(AvailabilityStatus.AVAILABLE);
        availabilityRepository.save(a1);

    Availability a2 = new Availability();
        a2.setAccommodationId(accommodationId);
        a2.setStartDate(a1.getEndDate().plusDays(1)); // adjacent
        a2.setEndDate(a1.getEndDate().plusDays(3));
        a2.setPrice(BigDecimal.valueOf(100));
    a2.setPriceType(PriceType.NORMAL);
        a2.setStatus(AvailabilityStatus.AVAILABLE);
        availabilityRepository.save(a2);

    service.mergeAdjacentAvailabilities(accommodationId);

        List<Availability> merged = availabilityRepository.findByAccommodationId(accommodationId);
        // Expect at least one with extended end date covering both
        assertThat(merged.stream().anyMatch(a -> a.getStartDate().equals(a1.getStartDate()) && a.getEndDate().equals(a2.getEndDate()))).isTrue();
    }

    @Test
    @DisplayName("update(): updates only PENDING requests")
    void testUpdatePendingOnly() {
    ReservationRequest req = new ReservationRequest();
        req.setGuestId(guestId);
        req.setAccommodationId(accommodationId);
        req.setStartDate(LocalDate.now().plusDays(60));
        req.setEndDate(LocalDate.now().plusDays(61));
        req.setGuestCount(1);
        req.setStatus(RequestStatus.PENDING);
    requestRepository.saveAndFlush(req);

        ReservationRequestUpdateDto updateDto = new ReservationRequestUpdateDto(
                LocalDate.now().plusDays(62),
                LocalDate.now().plusDays(63),
                4
        );

    ReservationRequestResponseDto updated = service.update(req.getId(), updateDto);
        assertThat(updated.getStartDate()).isEqualTo(updateDto.getStartDate());
        assertThat(updated.getGuestCount()).isEqualTo(4);

        // Non-pending update attempt
        req.setStatus(RequestStatus.APPROVED);
        requestRepository.save(req);
        assertThatThrownBy(() -> service.update(req.getId(), updateDto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PENDING");
    }
}
