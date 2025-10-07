package org.example.booking.controller;

import com.nimbusds.oauth2.sdk.Response;
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

    @PreAuthorize("hasRole('guest')")
    @GetMapping("/host/{hostId}/can-rate")
    public ResponseEntity<Boolean> canGuestRateHost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID hostId) {
        UUID guestId = UUID.fromString(jwt.getClaim("sub"));
        boolean eligible = service.canGuestRateHost(hostId, guestId);
        return ResponseEntity.ok(eligible);
    }

    @PreAuthorize("hasRole('guest')")
    @GetMapping("/guest/can-delete-account")
    public ResponseEntity<Boolean> canGuestDeleteAccount(@AuthenticationPrincipal Jwt jwt) {
        UUID guestId = UUID.fromString(jwt.getClaim("sub"));
        boolean canDelete = service.canGuestDeleteAccount(guestId);
        return ResponseEntity.ok(canDelete);
    }

    @PreAuthorize("hasRole('host')")
    @GetMapping("/host/can-delete-account")
    public ResponseEntity<Boolean> canHostDeleteAccount(@AuthenticationPrincipal Jwt jwt) {
        UUID hostId = UUID.fromString(jwt.getClaim("sub"));
        boolean eligible = service.canHostDeleteAccount(hostId, jwt.getTokenValue());
        return ResponseEntity.ok(eligible);
    }




}
