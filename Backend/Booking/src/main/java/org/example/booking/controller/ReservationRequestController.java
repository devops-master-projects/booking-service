package org.example.booking.controller;

import lombok.RequiredArgsConstructor;
import org.example.booking.dto.ReservationRequestCreateDto;
import org.example.booking.dto.ReservationRequestResponseDto;
import org.example.booking.dto.ReservationRequestUpdateDto;
import org.example.booking.model.RequestStatus;
import org.example.booking.service.ReservationRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservation-requests")
@RequiredArgsConstructor
public class ReservationRequestController {

    private final ReservationRequestService service;

    /**
     * TODO!!!
     * Gost kreira novi zahtev za rezervaciju.
     * guestId bi u praksi trebalo da dolazi iz JWT tokena,
     * ovde ga prosleđujem kao header da pojednostavimo.
     */
    @PostMapping
    public ResponseEntity<ReservationRequestResponseDto> create(
            @RequestHeader("X-Guest-Id") UUID guestId,
            @RequestBody ReservationRequestCreateDto dto
    ) {
        return ResponseEntity.ok(service.create(guestId, dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReservationRequestResponseDto> update(
            @PathVariable UUID id,
            @RequestBody ReservationRequestUpdateDto dto
    ) {
        return ResponseEntity.ok(service.update(id, dto));
    }



    /** TODO! izbaciti guestId rezervacije ce se uzeti iz tokena!*/
    @GetMapping("/guest/{guestId}/{accommodationId}")
    public ResponseEntity<List<ReservationRequestResponseDto>> getByGuest(
            @PathVariable UUID guestId,
            @PathVariable UUID accommodationId
    ) {
        return ResponseEntity.ok(service.findByGuest(guestId, accommodationId));
    }


    @GetMapping("/accommodation/{accommodationId}")
    public ResponseEntity<List<ReservationRequestResponseDto>> getByAccommodation(
            @PathVariable UUID accommodationId
    ) {
        return ResponseEntity.ok(service.findByAccommodation(accommodationId));
    }


    @PatchMapping("/{id}/status")
    public ResponseEntity<ReservationRequestResponseDto> updateStatus(
            @PathVariable UUID id,
            @RequestParam RequestStatus status
    ) {
        return ResponseEntity.ok(service.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

}
