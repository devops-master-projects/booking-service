package org.example.booking.controller;

import lombok.RequiredArgsConstructor;
import org.example.booking.dto.ReservationRequestCreateDto;
import org.example.booking.dto.ReservationRequestResponseDto;
import org.example.booking.dto.ReservationRequestUpdateDto;
import org.example.booking.model.RequestStatus;
import org.example.booking.service.ReservationRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/reservation-requests")
@RequiredArgsConstructor
public class ReservationRequestController {

    private final ReservationRequestService service;
    @PreAuthorize("hasRole('guest')")
    @PostMapping
    public ResponseEntity<ReservationRequestResponseDto> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ReservationRequestCreateDto dto
    ) {
        UUID guestId = UUID.fromString(jwt.getClaim("sub"));
        String guestEmail = jwt.getClaim("email");
        String guestFirstName = jwt.getClaim("given_name");
        String guestLastName = jwt.getClaim("family_name");
        return ResponseEntity.ok(
                service.create(guestId, guestEmail, guestLastName, guestFirstName, dto)
        );    }

    @PreAuthorize("hasRole('guest')")
    @PutMapping("/{id}")
    public ResponseEntity<ReservationRequestResponseDto> update(
            @PathVariable UUID id,
            @RequestBody ReservationRequestUpdateDto dto
    ) {
        return ResponseEntity.ok(service.update(id, dto));
    }


    @PreAuthorize("hasRole('guest')")
    @GetMapping("/guest/{accommodationId}")
    public ResponseEntity<List<ReservationRequestResponseDto>> getByGuest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accommodationId
    ) {
        UUID guestId = UUID.fromString(jwt.getClaim("sub"));
        return ResponseEntity.ok(service.findByGuest(guestId, accommodationId));
    }


    @PreAuthorize("hasRole('host')")
    @GetMapping("/accommodation/{accommodationId}")
    public ResponseEntity<List<ReservationRequestResponseDto>> getByAccommodation(
            @PathVariable UUID accommodationId
    ) {
        return ResponseEntity.ok(service.findByAccommodation(accommodationId));
    }

    @PreAuthorize("hasRole('host')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ReservationRequestResponseDto> updateStatus(
            @PathVariable UUID id,
            @RequestParam RequestStatus status,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String firstName = jwt.getClaim("given_name");
        String lastName  = jwt.getClaim("family_name");

        return ResponseEntity.ok(service.updateStatus(id, status, firstName, lastName));
    }


    @PreAuthorize("hasRole('guest')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('guest')")
    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<String> cancelReservation(@PathVariable UUID requestId) {
        try {
            service.cancelReservation(requestId);
            return ResponseEntity.ok("Reservation cancelled successfully");
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

}
