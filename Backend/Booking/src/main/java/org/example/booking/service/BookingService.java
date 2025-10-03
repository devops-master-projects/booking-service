package org.example.booking.service;

import lombok.RequiredArgsConstructor;
import org.example.booking.model.ReservationStatus;
import org.example.booking.repository.ReservationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final ReservationRepository reservationRepository;

    /**
     * Checks if the guest has actually stayed in the given accommodation at least once in the past.
     * The reservation must have been confirmed and already finished (endDate before today).
     *
     * @param guestId ID of the guest
     * @param accommodationId ID of the accommodation
     * @return true if the guest has completed at least one stay in this accommodation, false otherwise
     */
    public boolean hasGuestCompletedStay(UUID guestId, UUID accommodationId) {
        return reservationRepository.existsByRequest_GuestIdAndRequest_AccommodationIdAndStatusAndRequest_EndDateBefore(
                guestId,
                accommodationId,
                ReservationStatus.CONFIRMED,
                LocalDate.now()
        );
    }
}
