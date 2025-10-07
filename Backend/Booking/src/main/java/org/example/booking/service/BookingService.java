package org.example.booking.service;

import lombok.RequiredArgsConstructor;
import org.example.booking.model.ReservationStatus;
import org.example.booking.repository.ReservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final ReservationRepository reservationRepository;

    private final RestTemplate restTemplate;

    @Value("${accommodation.service.url}")
    private String accommodationServiceUrl;
    /**
     * Checks if the guest has actually stayed in the given accommodation at least once in the past.
     * The reservation must have been completed and already finished (endDate before today).
     *
     * @param guestId ID of the guest
     * @param accommodationId ID of the accommodation
     * @return true if the guest has completed at least one stay in this accommodation, false otherwise
     */
    public boolean hasGuestCompletedStay(UUID guestId, UUID accommodationId) {
        return reservationRepository.existsByRequest_GuestIdAndRequest_AccommodationIdAndStatusAndRequest_EndDateBefore(
                guestId,
                accommodationId,
                ReservationStatus.COMPLETED,
                LocalDate.now()
        );
    }

    /**
     * Checks if the guest can rate the host.
     * Guest can rate only if they have at least one COMPLETED reservation
     * in any accommodation owned by the given host.
     */
    public boolean canGuestRateHost(UUID hostId, UUID guestId) {
        ResponseEntity<UUID[]> response = restTemplate.getForEntity(
                accommodationServiceUrl + "/api/accommodations/host/" + hostId,
                UUID[].class
        );

        UUID[] accommodationIds = response.getBody();
        for (UUID accId : accommodationIds) {
            boolean completed = reservationRepository
                    .existsByRequest_GuestIdAndRequest_AccommodationIdAndStatusAndRequest_EndDateBefore(
                            guestId,
                            accId,
                            ReservationStatus.COMPLETED,
                            LocalDate.now()
                    );
            if (completed) {
                return true;
            }
        }
        return false;
    }

    public boolean canGuestDeleteAccount(UUID guestId) {
        boolean hasActive = reservationRepository.existsByRequest_GuestIdAndStatusIn(
                guestId,
                List.of(ReservationStatus.CONFIRMED)
        );
        return !hasActive;
    }

    public boolean canHostDeleteAccount(UUID hostId, String jwtToken) {
        String url = accommodationServiceUrl + "/api/accommodations/host/" + hostId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<UUID[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                UUID[].class
        );

        UUID[] accommodationIds = response.getBody();
        if (accommodationIds.length == 0) {
            return true;
        }

        boolean hasActive = reservationRepository.existsByRequest_AccommodationIdInAndStatusInAndRequest_EndDateAfter(
                List.of(accommodationIds),
                List.of(ReservationStatus.CONFIRMED),
                LocalDate.now()
        );

        return !hasActive;
    }

}
