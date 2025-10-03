package org.example.booking.controller;

import lombok.RequiredArgsConstructor;
import org.example.booking.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/booking")
@RequiredArgsConstructor
public class ReservationsController {
    private final BookingService service;

    @PreAuthorize("hasRole('guest')")
    @GetMapping("/accommodations/{accommodationId}/can-rate")
    public ResponseEntity<Boolean> canGuestRateAccommodation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accommodationId) {
        UUID guestId = UUID.fromString(jwt.getClaim("sub"));
        boolean eligible = service.hasGuestCompletedStay(guestId, accommodationId);
        return ResponseEntity.ok(eligible);
    }

}
