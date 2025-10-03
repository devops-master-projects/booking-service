package org.example.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.booking.dto.ReservationRequestCreateDto;
import org.example.booking.dto.ReservationRequestResponseDto;
import org.example.booking.dto.ReservationRequestUpdateDto;
import org.example.booking.model.RequestStatus;
import org.example.booking.service.ReservationRequestService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReservationRequestController.class)
class ReservationRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationRequestService service;

    @Autowired
    private ObjectMapper objectMapper;


    // region guest

    @Test
    @WithMockUser(roles = "guest")
    void shouldCreateReservationRequest() throws Exception {
        UUID guestId = UUID.randomUUID();
        ReservationRequestCreateDto dto = new ReservationRequestCreateDto(
                UUID.randomUUID(),
                LocalDate.now(),
                LocalDate.now().plusDays(2),
                2
        );

        ReservationRequestResponseDto response = new ReservationRequestResponseDto();
        Mockito.when(service.create(eq(guestId), any())).thenReturn(response);

        mockMvc.perform(post("/api/reservation-requests")
                        .with(csrf())
                        .with(jwt().jwt(jwt -> jwt.claim("sub", guestId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "guest")
    void shouldUpdateReservationRequest() throws Exception {
        UUID id = UUID.randomUUID();
        ReservationRequestUpdateDto dto = new ReservationRequestUpdateDto(
                LocalDate.now(),
                LocalDate.now().plusDays(3),
                3
        );

        Mockito.when(service.update(eq(id), any())).thenReturn(new ReservationRequestResponseDto());

        mockMvc.perform(put("/api/reservation-requests/{id}", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "guest")
    void shouldGetByGuest() throws Exception {
        UUID guestId = UUID.randomUUID();
        UUID accommodationId = UUID.randomUUID();

        Mockito.when(service.findByGuest(eq(guestId), eq(accommodationId)))
                .thenReturn(List.of(new ReservationRequestResponseDto()));

        mockMvc.perform(get("/api/reservation-requests/guest/{accommodationId}", accommodationId)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", guestId.toString()))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "guest")
    void shouldCancelReservationSuccessfully() throws Exception {
        UUID requestId = UUID.randomUUID();
        Mockito.doNothing().when(service).cancelReservation(requestId);

        mockMvc.perform(post("/api/reservation-requests/{requestId}/cancel", requestId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("Reservation cancelled successfully"));
    }

    @Test
    @WithMockUser(roles = "guest")
    void shouldHandleCancelReservationException() throws Exception {
        UUID requestId = UUID.randomUUID();
        Mockito.doThrow(new RuntimeException("Already cancelled"))
                .when(service).cancelReservation(requestId);

        mockMvc.perform(post("/api/reservation-requests/{requestId}/cancel", requestId)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Already cancelled"));
    }

    // endregion

    // region host

    @Test
    @WithMockUser(roles = "host")
    void shouldGetByAccommodation() throws Exception {
        UUID accommodationId = UUID.randomUUID();
        Mockito.when(service.findByAccommodation(accommodationId))
                .thenReturn(List.of(new ReservationRequestResponseDto()));

        mockMvc.perform(get("/api/reservation-requests/accommodation/{accommodationId}", accommodationId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "host")
    void shouldUpdateStatus() throws Exception {
        UUID id = UUID.randomUUID();
        RequestStatus status = RequestStatus.APPROVED;

        Mockito.when(service.updateStatus(eq(id), eq(status)))
                .thenReturn(new ReservationRequestResponseDto());

        mockMvc.perform(patch("/api/reservation-requests/{id}/status", id)
                        .param("status", status.name())
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "host")
    void shouldDeleteReservationRequest() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/reservation-requests/{id}", id).with(csrf()))
                .andExpect(status().isNoContent());

        Mockito.verify(service).delete(id);
    }

    // endregion

    // region unauthenticated

    @Test
    void shouldRejectUnauthenticatedAccess() throws Exception {
        mockMvc.perform(post("/api/reservation-requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // endregion
}
