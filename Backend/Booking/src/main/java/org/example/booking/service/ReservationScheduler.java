package org.example.booking.service;

import lombok.RequiredArgsConstructor;
import org.example.booking.model.Reservation;
import org.example.booking.model.ReservationStatus;
import org.example.booking.repository.ReservationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationScheduler {

    private final ReservationRepository reservationRepository;

    /**
     * Marks all confirmed reservations as COMPLETED if their end date has passed.
     * <p>
     * This method is scheduled to run once per day (at midnight) and checks all reservations
     * with status {@link ReservationStatus#CONFIRMED}. If the reservation's endDate is before today,
     * the status is updated to {@link ReservationStatus#COMPLETED}.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void markCompletedReservations() {
        LocalDate today = LocalDate.now();

        List<Reservation> toComplete = reservationRepository
                .findAllByStatusAndRequest_EndDateBefore(ReservationStatus.CONFIRMED, today);

        toComplete.forEach(r -> r.setStatus(ReservationStatus.COMPLETED));
        reservationRepository.saveAll(toComplete);
    }


}
