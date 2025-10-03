package org.example.booking.controller;
import lombok.RequiredArgsConstructor;
import org.example.booking.dto.AvailabilityRequestDto;
import org.example.booking.dto.CalendarIntervalDto;
import org.example.booking.model.Availability;
import org.example.booking.service.AvailabilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @PreAuthorize("hasRole('host')")
    @PostMapping
    public ResponseEntity<Availability> defineAvailability(@RequestBody AvailabilityRequestDto request) {
        Availability availability = availabilityService.defineAvailability(
                request.getAccommodationId(),
                request.getStartDate(),
                request.getEndDate(),
                request.getPrice(),
                request.getPriceType()
        );
        return ResponseEntity.ok(availability);
    }

    @PreAuthorize("hasRole('host')")
    @PutMapping("/{id}")
    public ResponseEntity<Availability> updateAvailability(@PathVariable UUID id,
                                                           @RequestBody AvailabilityRequestDto request) {
        Availability availability = availabilityService.updateAvailability(
                id,
                request.getStartDate(),
                request.getEndDate(),
                request.getPrice(),
                request.getPriceType()
        );
        return ResponseEntity.ok(availability);
    }

    @PreAuthorize("hasAnyRole('guest','host')")
    @GetMapping("/{accommodationId}/calendar")
    public ResponseEntity<Set<CalendarIntervalDto>> getCalendar(
            @PathVariable UUID accommodationId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Authentication authentication) {

        boolean isHost = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_host"));

        Set<CalendarIntervalDto> calendar = isHost
                ? availabilityService.getCalendarHost(accommodationId, startDate, endDate)
                : availabilityService.getCalendar(accommodationId, startDate, endDate);

        return ResponseEntity.ok(calendar);
    }

    @PreAuthorize("hasRole('host')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAvailability(@PathVariable UUID id) {
        try {
            availabilityService.deleteAvailability(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build(); // Conflict
        }
    }

}
