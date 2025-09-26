package org.example.booking.controller;
import lombok.RequiredArgsConstructor;
import org.example.booking.dto.AvailabilityRequestDto;
import org.example.booking.dto.CalendarIntervalDto;
import org.example.booking.model.Availability;
import org.example.booking.service.AvailabilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

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

    // TODO: iz uloge ove dve metode mogu da se ekstrahuju
    @GetMapping("/{accommodationId}/calendar")
    public ResponseEntity<Set<CalendarIntervalDto>> getCalendar(
            @PathVariable UUID accommodationId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {

        Set<CalendarIntervalDto> calendar = availabilityService.getCalendar(accommodationId, startDate, endDate);
        System.out.println(calendar.size());
        return ResponseEntity.ok(calendar);
    }

    @GetMapping("/{accommodationId}/calendarHost")
    public ResponseEntity<Set<CalendarIntervalDto>> getCalendarHost(
            @PathVariable UUID accommodationId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {

        Set<CalendarIntervalDto> calendar = availabilityService.getCalendarHost(accommodationId, startDate, endDate);
        System.out.println(calendar.size());
        return ResponseEntity.ok(calendar);
    }

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
