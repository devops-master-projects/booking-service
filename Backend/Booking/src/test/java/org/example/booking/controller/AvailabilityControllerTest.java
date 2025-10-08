package org.example.booking.controller;// package org.example.booking.controller;

import org.example.booking.controller.AvailabilityController;
import org.example.booking.dto.AvailabilityRequestDto;
import org.example.booking.dto.CalendarIntervalDto;
import org.example.booking.model.Availability;
import org.example.booking.model.PriceType;
import org.example.booking.service.AvailabilityService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(AvailabilityController.class)
@ActiveProfiles("test")

class AvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AvailabilityService availabilityService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "host")
    void shouldDefineAvailability() throws Exception {
        Availability availability = new Availability();
        Mockito.when(availabilityService.defineAvailability(any(), any(), any(), BigDecimal.valueOf(anyDouble()), any()))
                .thenReturn(availability);

        AvailabilityRequestDto dto = new AvailabilityRequestDto(
                UUID.randomUUID(),
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 10),
                BigDecimal.valueOf(100),
                PriceType.HOLIDAY
        );

        mockMvc.perform(post("/api/availability")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "host")
    void shouldUpdateAvailability() throws Exception {
        Availability availability = new Availability();
        Mockito.when(availabilityService.updateAvailability(any(), any(), any(), BigDecimal.valueOf(anyDouble()), any()))
                .thenReturn(availability);

        AvailabilityRequestDto dto = new AvailabilityRequestDto(
                UUID.randomUUID(),
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 10),
                BigDecimal.valueOf(120.0),
                PriceType.HOLIDAY        );

        mockMvc.perform(put("/api/availability/{id}", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "guest")
    void shouldReturnCalendarForGuest() throws Exception {
        CalendarIntervalDto interval = new CalendarIntervalDto(UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusDays(1), "AVAILABLE", BigDecimal.valueOf(100), PriceType.HOLIDAY);
        Mockito.when(availabilityService.getCalendar(any(), any(), any())).thenReturn(Set.of(interval));

        mockMvc.perform(get("/api/availability/{id}/calendar", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startDate").value(interval.getStartDate().toString()))
                .andExpect(jsonPath("$[0].endDate").value(interval.getEndDate().toString()));
    }

    @Test
    @WithMockUser(roles = "host")
    void shouldReturnCalendarForHost() throws Exception {
        CalendarIntervalDto interval = new CalendarIntervalDto(UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusDays(1), "AVAILABLE", BigDecimal.valueOf(100), PriceType.HOLIDAY);
        Mockito.when(availabilityService.getCalendarHost(any(), any(), any())).thenReturn(Set.of(interval));

        mockMvc.perform(get("/api/availability/{id}/calendar", UUID.randomUUID())
                        .param("startDate", "2025-01-01")
                        .param("endDate", "2025-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startDate").value(interval.getStartDate().toString()))
                .andExpect(jsonPath("$[0].endDate").value(interval.getEndDate().toString()));
    }

    @Test
    @WithMockUser(roles = "host")
    void shouldDeleteAvailabilitySuccessfully() throws Exception {
        Mockito.doNothing().when(availabilityService).deleteAvailability(any());

        mockMvc.perform(delete("/api/availability/{id}", UUID.randomUUID()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "host")
    void shouldReturnConflictWhenDeleteFails() throws Exception {
        Mockito.doThrow(new IllegalStateException("Conflict")).when(availabilityService).deleteAvailability(any());

        mockMvc.perform(delete("/api/availability/{id}", UUID.randomUUID()).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectDefineAvailabilityForUnauthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/availability")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized()); // 401 unauthenticated
    }
}
