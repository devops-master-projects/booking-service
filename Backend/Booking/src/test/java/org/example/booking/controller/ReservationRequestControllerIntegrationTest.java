package org.example.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.example.booking.dto.AutoConfirmResponse;
import org.example.booking.dto.ReservationRequestCreateDto;
import org.example.booking.dto.ReservationRequestResponseDto;
import org.example.booking.dto.ReservationRequestUpdateDto;
import org.example.booking.model.RequestStatus;
import org.example.booking.model.Reservation;
import org.example.booking.model.ReservationStatus;
import org.example.booking.repository.ReservationRepository;
import org.example.booking.repository.ReservationRequestRepository;
import org.example.booking.service.ReservationRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;
import org.example.booking.dto.AutoConfirmResponse;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.KafkaContainer;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ReservationRequestControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("booking_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("accommodation.service.url", () -> "http://localhost:9999");
    }


    @Container
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
                    .withReuse(false);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ReservationRequestService service;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationRequestRepository reservationRequestRepository;

    @MockitoBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setupMocks() {
        AutoConfirmResponse fakeResponse = new AutoConfirmResponse();
        fakeResponse.setAutoConfirm(false);
        when(restTemplate.getForObject(anyString(), eq(AutoConfirmResponse.class)))
                .thenReturn(fakeResponse);
    }



    private ReservationRequestResponseDto sampleResponse() {
        ReservationRequestResponseDto dto = new ReservationRequestResponseDto();
        dto.setId(UUID.randomUUID());
        dto.setAccommodationId(UUID.randomUUID());
        dto.setGuestId(UUID.randomUUID());
        dto.setStatus(RequestStatus.PENDING);
        return dto;
    }

    @Test
    @DisplayName("Guest: create reservation request succeeds and persists in DB")
    void guestCreateSuccess() throws Exception {
        UUID guestId = UUID.randomUUID();

        ReservationRequestCreateDto createDto = new ReservationRequestCreateDto(
                UUID.randomUUID(),
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(7),
                2
        );

        mockMvc.perform(post("/api/reservation-requests")
                        .with(csrf())
                        .with(jwt().jwt(j -> j
                                        .subject(guestId.toString())
                                        .claim("sub", guestId.toString())
                                        .claim("email", "guest@mail.com")
                                        .claim("given_name", "John")
                                        .claim("family_name", "Doe"))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.guestId").value(guestId.toString()));
    }


    @Test
    @DisplayName("Guest: get reservation requests by guest returns 200 OK (real integration)")
    void guestGetByGuest() throws Exception {
        UUID guestId = UUID.randomUUID();
        UUID accommodationId = UUID.randomUUID();

        ReservationRequestCreateDto createDto = new ReservationRequestCreateDto(
                accommodationId,
                LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(5),
                2
        );

        mockMvc.perform(post("/api/reservation-requests")
                        .with(csrf())
                        .with(jwt().jwt(j -> j
                                        .subject(guestId.toString())
                                        .claim("sub", guestId.toString())
                                        .claim("email", "guest@mail.com")
                                        .claim("given_name", "John")
                                        .claim("family_name", "Doe"))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/reservation-requests/guest/{accId}", accommodationId)
                        .with(jwt().jwt(j -> j
                                        .subject(guestId.toString())
                                        .claim("sub", guestId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].guestId").value(guestId.toString()))
                .andExpect(jsonPath("$[0].accommodationId").value(accommodationId.toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }



    @Test
    @DisplayName("Guest: cancel reservation success path (real integration)")
    void guestCancelSuccess() throws Exception {
        UUID guestId = UUID.randomUUID();
        UUID accommodationId = UUID.randomUUID();
        ReservationRequestResponseDto created = createReservationRequest(
                guestId,
                accommodationId,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(7),
                2
        );
        createAndSaveReservation(accommodationId, created.getId(),
                RequestStatus.APPROVED,
                ReservationStatus.CONFIRMED,
                LocalDateTime.now());
        mockMvc.perform(post("/api/reservation-requests/{id}/cancel", created.getId())
                        .with(csrf())
                        .with(jwt().jwt(j -> j
                                        .subject(guestId.toString())
                                        .claim("sub", guestId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest"))))
                .andExpect(status().isOk())
                .andExpect(content().string("Reservation cancelled successfully"));
    }


    @Test
    @DisplayName("Guest: cancel reservation error returns 400 with message")
    void guestCancelError() throws Exception {
        UUID guestId = UUID.randomUUID();
        UUID accommodationId = UUID.randomUUID();
        ReservationRequestResponseDto created = createReservationRequest(
                guestId,
                accommodationId,
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(4),
                2
        );
        createAndSaveReservation(accommodationId, created.getId(),
                RequestStatus.APPROVED,
                ReservationStatus.CONFIRMED,
                LocalDateTime.now());

        mockMvc.perform(post("/api/reservation-requests/{id}/cancel", created.getId())
                        .with(csrf())
                        .with(jwt().jwt(j -> j
                                        .subject(guestId.toString())
                                        .claim("sub", guestId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest"))))
                .andExpect(status().isOk())
                .andExpect(content().string("Reservation cancelled successfully"));


    }


    @Test
    @DisplayName("Guest: update reservation request (real integration)")
    void guestUpdate() throws Exception {
        UUID guestId = UUID.randomUUID();
        UUID accommodationId = UUID.randomUUID();
        ReservationRequestResponseDto created = createReservationRequest(
                guestId,
                accommodationId,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(7),
                2
        );

        ReservationRequestUpdateDto updateDto = new ReservationRequestUpdateDto(
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(12),
                3
        );

        mockMvc.perform(put("/api/reservation-requests/{id}", created.getId())
                        .with(csrf())
                        .with(jwt().jwt(j -> j
                                        .subject(guestId.toString())
                                        .claim("sub", guestId.toString())
                                        .claim("email", "guest@mail.com"))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.guestId").value(guestId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        var updated = reservationRequestRepository.findById(created.getId())
                .orElseThrow(() -> new AssertionError("Updated reservation request not found"));

        assertThat(updated.getStartDate()).isEqualTo(updateDto.getStartDate());
        assertThat(updated.getEndDate()).isEqualTo(updateDto.getEndDate());
        assertThat(updated.getGuestCount()).isEqualTo(updateDto.getGuestCount());
    }

    // endregion

    // region host endpoints
    @Test
    @DisplayName("Host: get reservation requests by accommodation (real integration)")
    void hostGetByAccommodation() throws Exception {
        UUID hostId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        UUID accommodationId = UUID.randomUUID();

        ReservationRequestResponseDto created = createReservationRequest(
                guestId,
                accommodationId,
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(5),
                2
        );

        mockMvc.perform(get("/api/reservation-requests/accommodation/{id}", accommodationId)
                        .with(jwt().jwt(j -> j
                                        .subject(hostId.toString())
                                        .claim("sub", hostId.toString())
                                        .claim("email", "host@mail.com")
                                        .claim("given_name", "Alice")
                                        .claim("family_name", "Smith"))
                                .authorities(new SimpleGrantedAuthority("ROLE_host"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(created.getId().toString()))
                .andExpect(jsonPath("$[0].accommodationId").value(accommodationId.toString()))
                .andExpect(jsonPath("$[0].guestId").value(guestId.toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }


    @Test
    @DisplayName("Host: update status succeeds and passes host details from JWT (real integration)")
    void hostUpdateStatus() throws Exception {
        UUID hostId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        UUID accommodationId = UUID.randomUUID();

        ReservationRequestResponseDto created = createReservationRequest(
                guestId,
                accommodationId,
                LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(5),
                2
        );

        RequestStatus newStatus = RequestStatus.APPROVED;

        mockMvc.perform(patch("/api/reservation-requests/{id}/status", created.getId())
                        .param("status", newStatus.name())
                        .with(csrf())
                        .with(jwt().jwt(j -> j
                                        .subject(hostId.toString())
                                        .claim("sub", hostId.toString())
                                        .claim("given_name", "Alice")
                                        .claim("family_name", "Smith"))
                                .authorities(new SimpleGrantedAuthority("ROLE_host"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.status").value(newStatus.name()))
                .andExpect(jsonPath("$.accommodationId").value(accommodationId.toString()))
                .andExpect(jsonPath("$.guestId").value(guestId.toString()));

        var updated = reservationRequestRepository.findById(created.getId())
                .orElseThrow(() -> new AssertionError("Reservation request not found after update"));

        assertThat(updated.getStatus()).isEqualTo(newStatus);
    }



    @Test
    @DisplayName("guest: delete reservation request (real integration)")
    void guestDelete() throws Exception {
        UUID hostId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        UUID accommodationId = UUID.randomUUID();

        ReservationRequestResponseDto created = createReservationRequest(
                guestId,
                accommodationId,
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(4),
                2
        );

        assertThat(reservationRequestRepository.existsById(created.getId())).isTrue();

        mockMvc.perform(delete("/api/reservation-requests/{id}", created.getId())
                        .with(csrf())
                        .with(jwt().jwt(j -> j
                                        .subject(hostId.toString())
                                        .claim("sub", hostId.toString())
                                        .claim("email", "host@mail.com")
                                        .claim("given_name", "Alice")
                                        .claim("family_name", "Smith"))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest"))))
                .andExpect(status().isNoContent());

        boolean existsAfterDelete = reservationRequestRepository.existsById(created.getId());
        assertThat(existsAfterDelete)
                .as("Reservation request should be deleted from DB after guest delete call")
                .isFalse();
    }

    // endregion

    // region security boundaries
    @Test
    @DisplayName("Security: unauthenticated create request returns 401 Unauthorized")
    void unauthenticatedCreate() throws Exception {
        ReservationRequestCreateDto createDto = new ReservationRequestCreateDto(
                UUID.randomUUID(),
                LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(5),
                2
        );

        mockMvc.perform(post("/api/reservation-requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("Security: host cannot call guest create endpoint (403 Forbidden)")
    void hostCannotCreateAsGuest() throws Exception {
        ReservationRequestCreateDto createDto = new ReservationRequestCreateDto(
                UUID.randomUUID(),                     // accommodationId
                LocalDate.now().plusDays(3),           // startDate
                LocalDate.now().plusDays(5),           // endDate
                1                                      // guests
        );
        mockMvc.perform(post("/api/reservation-requests")
                        .with(csrf())
                        .with(jwt().jwt(j -> j
                                        .subject(UUID.randomUUID().toString())
                                        .claim("sub", UUID.randomUUID().toString())
                                        .claim("email", "host@mail.com")
                                        .claim("given_name", "Alice")
                                        .claim("family_name", "Smith"))
                                .authorities(new SimpleGrantedAuthority("ROLE_host")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: guest cannot access host-only endpoint (403 Forbidden)")
    void guestCannotCallHostEndpoint() throws Exception {
        UUID guestId = UUID.randomUUID();
        UUID accommodationId = UUID.randomUUID();

        mockMvc.perform(get("/api/reservation-requests/accommodation/{id}", accommodationId)
                        .with(jwt().jwt(j -> j
                                        .subject(guestId.toString())
                                        .claim("sub", guestId.toString())
                                        .claim("email", "guest@mail.com")
                                        .claim("given_name", "John")
                                        .claim("family_name", "Doe"))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest"))))
                .andExpect(status().isForbidden());
    }

    // endregion


    // region Helper methods

    private ReservationRequestResponseDto createReservationRequest(UUID guestId, UUID accommodationId,
                                                                   LocalDate start, LocalDate end, int guests) throws Exception {
        ReservationRequestCreateDto createDto = new ReservationRequestCreateDto(accommodationId, start, end, guests);

        String response = mockMvc.perform(post("/api/reservation-requests")
                        .with(csrf())
                        .with(jwt().jwt(j -> j
                                        .subject(guestId.toString())
                                        .claim("sub", guestId.toString())
                                        .claim("email", "guest@mail.com")
                                        .claim("given_name", "John")
                                        .claim("family_name", "Doe"))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readValue(response, ReservationRequestResponseDto.class);
    }

    @PersistenceContext
    private EntityManager em;

    private Reservation createAndSaveReservation(UUID accommodationId, UUID requestId,
                                                 RequestStatus reqStatus,
                                                 ReservationStatus resStatus,
                                                 LocalDateTime confirmedAt) {
        em.clear();
        var requestEntity = reservationRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        Reservation reservation = new Reservation();
        reservation.setRequest(requestEntity);
        reservation.setStatus(resStatus);
        reservation.setConfirmedAt(confirmedAt);
        return reservationRepository.saveAndFlush(reservation);
    }
// endregion

}
