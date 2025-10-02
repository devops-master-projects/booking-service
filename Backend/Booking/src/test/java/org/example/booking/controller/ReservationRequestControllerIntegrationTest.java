package org.example.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.booking.dto.ReservationRequestCreateDto;
import org.example.booking.dto.ReservationRequestResponseDto;
import org.example.booking.dto.ReservationRequestUpdateDto;
import org.example.booking.model.RequestStatus;
import org.example.booking.service.ReservationRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationRequestControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

        @MockitoBean
    ReservationRequestService service;

    private ReservationRequestResponseDto sampleResponse() {
        ReservationRequestResponseDto dto = new ReservationRequestResponseDto();
        dto.setId(UUID.randomUUID());
        dto.setAccommodationId(UUID.randomUUID());
        dto.setGuestId(UUID.randomUUID());
        dto.setStatus(RequestStatus.PENDING);
        return dto;
    }

    // region guest endpoints
    @Test
    @DisplayName("Guest: create reservation request succeeds and passes guestId from JWT 'sub'")
    void guestCreateSuccess() throws Exception {
        UUID guestId = UUID.randomUUID();
        ReservationRequestCreateDto createDto = new ReservationRequestCreateDto(
                UUID.randomUUID(),
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(7),
                2
        );
        ReservationRequestResponseDto resp = sampleResponse();
        when(service.create(eq(guestId), any())).thenReturn(resp);

        mockMvc.perform(post("/api/reservation-requests")
                        .with(csrf())
                        .with(jwt().jwt(j -> j.subject(guestId.toString()).claim("sub", guestId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isOk());

        verify(service).create(eq(guestId), any());
    }

    @Test
    @DisplayName("Guest: get by guest returns OK and invokes service")
    void guestGetByGuest() throws Exception {
        UUID guestId = UUID.randomUUID();
        UUID accommodationId = UUID.randomUUID();
        when(service.findByGuest(eq(guestId), eq(accommodationId))).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/reservation-requests/guest/{accId}", accommodationId)
                        .with(jwt().jwt(j -> j.subject(guestId.toString()).claim("sub", guestId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest"))))
                .andExpect(status().isOk());

        verify(service).findByGuest(guestId, accommodationId);
    }

    @Test
    @DisplayName("Guest: cancel reservation success path")
    void guestCancelSuccess() throws Exception {
        UUID requestId = UUID.randomUUID();
        doNothing().when(service).cancelReservation(requestId);

        mockMvc.perform(post("/api/reservation-requests/{id}/cancel", requestId)
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_guest"))))
                .andExpect(status().isOk())
                .andExpect(content().string("Reservation cancelled successfully"));

        verify(service).cancelReservation(requestId);
    }

    @Test
    @DisplayName("Guest: cancel reservation error returns 400 with message")
    void guestCancelError() throws Exception {
        UUID requestId = UUID.randomUUID();
        doThrow(new RuntimeException("Already cancelled")).when(service).cancelReservation(requestId);

        mockMvc.perform(post("/api/reservation-requests/{id}/cancel", requestId)
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_guest"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Already cancelled"));
    }

    @Test
    @DisplayName("Guest: update reservation request")
    void guestUpdate() throws Exception {
        UUID id = UUID.randomUUID();
        ReservationRequestUpdateDto updateDto = new ReservationRequestUpdateDto(
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(12),
                3
        );
        when(service.update(eq(id), any())).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/reservation-requests/{id}", id)
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_guest")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk());

        verify(service).update(eq(id), any());
    }
    // endregion

    // region host endpoints
    @Test
    @DisplayName("Host: get by accommodation")
    void hostGetByAccommodation() throws Exception {
        UUID accommodationId = UUID.randomUUID();
        when(service.findByAccommodation(accommodationId)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/reservation-requests/accommodation/{id}", accommodationId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_host"))))
                .andExpect(status().isOk());

        verify(service).findByAccommodation(accommodationId);
    }

    @Test
    @DisplayName("Host: update status")
    void hostUpdateStatus() throws Exception {
        UUID id = UUID.randomUUID();
        RequestStatus newStatus = RequestStatus.APPROVED;
        when(service.updateStatus(eq(id), eq(newStatus))).thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/reservation-requests/{id}/status", id)
                        .param("status", newStatus.name())
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_host"))))
                .andExpect(status().isOk());

        verify(service).updateStatus(id, newStatus);
    }

    @Test
    @DisplayName("Host: delete request")
    void hostDelete() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(service).delete(id);

        mockMvc.perform(delete("/api/reservation-requests/{id}", id)
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_host"))))
                .andExpect(status().isNoContent());

        verify(service).delete(id);
    }
    // endregion

    // region security boundaries
    @Test
    @DisplayName("Security: unauthenticated create is 401")
    void unauthenticatedCreate() throws Exception {
        mockMvc.perform(post("/api/reservation-requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security: host cannot call guest create endpoint (403)")
    void hostCannotCreateAsGuest() throws Exception {
        ReservationRequestCreateDto createDto = new ReservationRequestCreateDto(
                UUID.randomUUID(),
                LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(5),
                1
        );
        mockMvc.perform(post("/api/reservation-requests")
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_host")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: guest cannot call host endpoint (403)")
    void guestCannotCallHostEndpoint() throws Exception {
        mockMvc.perform(get("/api/reservation-requests/accommodation/{id}", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_guest"))))
                .andExpect(status().isForbidden());
    }
    // endregion
}
