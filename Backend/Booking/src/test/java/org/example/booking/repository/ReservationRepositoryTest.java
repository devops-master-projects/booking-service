package org.example.booking.repository;

import org.example.booking.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ReservationRepositoryTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationRequestRepository reservationRequestRepository;

    private ReservationRequest newRequest(UUID guestId, UUID accommodationId, LocalDate start, LocalDate end) {
        ReservationRequest rr = new ReservationRequest();
        rr.setGuestId(guestId);
        rr.setAccommodationId(accommodationId);
        rr.setStartDate(start);
        rr.setEndDate(end);
        rr.setGuestCount(2);
        rr.setStatus(RequestStatus.PENDING);
        return reservationRequestRepository.save(rr);
    }

    private Reservation newReservation(ReservationRequest req, ReservationStatus status) {
        Reservation r = new Reservation();
        r.setRequest(req);
        r.setConfirmedAt(LocalDateTime.now());
        r.setStatus(status);
        return reservationRepository.save(r);
    }

    @Test
    @DisplayName("findByRequestGuestId returns reservations for specific guest")
    void testFindByRequestGuestId() {
        UUID guest1 = UUID.randomUUID();
        UUID guest2 = UUID.randomUUID();
        UUID acc = UUID.randomUUID();
        ReservationRequest r1 = newRequest(guest1, acc, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
        ReservationRequest r2 = newRequest(guest1, acc, LocalDate.now().plusDays(5), LocalDate.now().plusDays(7));
        ReservationRequest r3 = newRequest(guest2, acc, LocalDate.now().plusDays(9), LocalDate.now().plusDays(11));
        newReservation(r1, ReservationStatus.CONFIRMED);
        newReservation(r2, ReservationStatus.CANCELLED);
        newReservation(r3, ReservationStatus.CONFIRMED);

        List<Reservation> guest1Reservations = reservationRepository.findByRequestGuestId(guest1);
        assertThat(guest1Reservations).hasSize(2)
                .allMatch(res -> res.getRequest().getGuestId().equals(guest1));
    }

    @Test
    @DisplayName("findByRequestAccommodationId returns reservations for accommodation")
    void testFindByRequestAccommodationId() {
        UUID acc1 = UUID.randomUUID();
        UUID acc2 = UUID.randomUUID();
        ReservationRequest a1 = newRequest(UUID.randomUUID(), acc1, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));
        ReservationRequest a2 = newRequest(UUID.randomUUID(), acc1, LocalDate.now().plusDays(3), LocalDate.now().plusDays(5));
        ReservationRequest b1 = newRequest(UUID.randomUUID(), acc2, LocalDate.now().plusDays(2), LocalDate.now().plusDays(4));
        newReservation(a1, ReservationStatus.CONFIRMED);
        newReservation(a2, ReservationStatus.CONFIRMED);
        newReservation(b1, ReservationStatus.CANCELLED);

        List<Reservation> acc1Reservations = reservationRepository.findByRequestAccommodationId(acc1);
        assertThat(acc1Reservations).hasSize(2)
                .allMatch(res -> res.getRequest().getAccommodationId().equals(acc1));
    }

    @Test
    @DisplayName("findByStatus returns reservations with given status")
    void testFindByStatus() {
        UUID acc = UUID.randomUUID();
        ReservationRequest r1 = newRequest(UUID.randomUUID(), acc, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));
        ReservationRequest r2 = newRequest(UUID.randomUUID(), acc, LocalDate.now().plusDays(3), LocalDate.now().plusDays(4));
        newReservation(r1, ReservationStatus.CONFIRMED);
        newReservation(r2, ReservationStatus.CANCELLED);

        List<Reservation> confirmed = reservationRepository.findByStatus(ReservationStatus.CONFIRMED);
        assertThat(confirmed).hasSize(1)
                .first().extracting(r -> r.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("exists overlap query returns true when any reservation overlaps interval")
    void testExistsOverlap() {
        UUID acc = UUID.randomUUID();
        ReservationRequest r = newRequest(UUID.randomUUID(), acc, LocalDate.of(2025,1,10), LocalDate.of(2025,1,15));
        newReservation(r, ReservationStatus.CONFIRMED);

        boolean existsInside = reservationRepository
                .existsByRequest_AccommodationIdAndRequest_StartDateLessThanEqualAndRequest_EndDateGreaterThanEqual(
                        acc,
                        LocalDate.of(2025,1,12), // startDate param
                        LocalDate.of(2025,1,12)  // endDate param
                );
        boolean existsOutside = reservationRepository
                .existsByRequest_AccommodationIdAndRequest_StartDateLessThanEqualAndRequest_EndDateGreaterThanEqual(
                        acc,
                        LocalDate.of(2025,2,1),
                        LocalDate.of(2025,2,2)
                );
        assertThat(existsInside).isTrue();
        assertThat(existsOutside).isFalse();
    }

    @Test
    @DisplayName("overlapping finder with status (note parameter order: endDate then startDate)")
    void testFindOverlappingWithStatus() {
        UUID acc = UUID.randomUUID();
        ReservationRequest r = newRequest(UUID.randomUUID(), acc, LocalDate.of(2025,5,1), LocalDate.of(2025,5,5));
        newReservation(r, ReservationStatus.CONFIRMED);

        Set<Reservation> overlaps = reservationRepository
                .findByRequest_AccommodationIdAndRequest_StartDateLessThanEqualAndRequest_EndDateGreaterThanEqualAndStatus(
                        acc,
                        LocalDate.of(2025,5,5),   // method signature puts endDate first
                        LocalDate.of(2025,5,1),   // then startDate
                        ReservationStatus.CONFIRMED
                );
        assertThat(overlaps).hasSize(1);

        Set<Reservation> nonOverlaps = reservationRepository
                .findByRequest_AccommodationIdAndRequest_StartDateLessThanEqualAndRequest_EndDateGreaterThanEqualAndStatus(
                        acc,
                        LocalDate.of(2025,4,30),
                        LocalDate.of(2025,4,25),
                        ReservationStatus.CONFIRMED
                );
        assertThat(nonOverlaps).isEmpty();
    }

    @Test
    @DisplayName("overlapping finder without status")
    void testFindOverlappingWithoutStatus() {
        UUID acc = UUID.randomUUID();
        ReservationRequest r1 = newRequest(UUID.randomUUID(), acc, LocalDate.of(2025,6,1), LocalDate.of(2025,6,3));
        ReservationRequest r2 = newRequest(UUID.randomUUID(), acc, LocalDate.of(2025,6,4), LocalDate.of(2025,6,6));
        newReservation(r1, ReservationStatus.CONFIRMED);
        newReservation(r2, ReservationStatus.CANCELLED);

        Set<Reservation> overlaps = reservationRepository
                .findByRequest_AccommodationIdAndRequest_StartDateLessThanEqualAndRequest_EndDateGreaterThanEqual(
                        acc,
                        LocalDate.of(2025,6,6),
                        LocalDate.of(2025,6,1)
                );
        assertThat(overlaps).hasSize(2);
    }

    @Test
    @DisplayName("exists overlap with status variant returns expected boolean")
    void testExistsOverlapWithStatus() {
        UUID acc = UUID.randomUUID();
        ReservationRequest r = newRequest(UUID.randomUUID(), acc, LocalDate.of(2025,7,10), LocalDate.of(2025,7,15));
        newReservation(r, ReservationStatus.CONFIRMED);

        boolean exists = reservationRepository
                .existsByRequestAccommodationIdAndRequestStartDateLessThanEqualAndRequestEndDateGreaterThanEqualAndStatus(
                        acc,
                        LocalDate.of(2025,7,10),
                        LocalDate.of(2025,7,15),
                        ReservationStatus.CONFIRMED
                );
        boolean notExists = reservationRepository
                .existsByRequestAccommodationIdAndRequestStartDateLessThanEqualAndRequestEndDateGreaterThanEqualAndStatus(
                        acc,
                        LocalDate.of(2025,8,1),
                        LocalDate.of(2025,8,2),
                        ReservationStatus.CONFIRMED
                );
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("findByRequest_Id returns single reservation")
    void testFindByRequestId() {
        UUID acc = UUID.randomUUID();
        ReservationRequest r = newRequest(UUID.randomUUID(), acc, LocalDate.now().plusDays(8), LocalDate.now().plusDays(10));
        Reservation saved = newReservation(r, ReservationStatus.CONFIRMED);

        assertThat(reservationRepository.findByRequest_Id(r.getId()))
                .isPresent()
                .get().isEqualTo(saved);
    }

    @Test
    @DisplayName("countByRequest_GuestIdAndStatus counts correctly")
    void testCountByGuestAndStatus() {
        UUID guest = UUID.randomUUID();
        UUID acc = UUID.randomUUID();
        ReservationRequest r1 = newRequest(guest, acc, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));
        ReservationRequest r2 = newRequest(guest, acc, LocalDate.now().plusDays(3), LocalDate.now().plusDays(4));
        ReservationRequest r3 = newRequest(UUID.randomUUID(), acc, LocalDate.now().plusDays(5), LocalDate.now().plusDays(6));
        newReservation(r1, ReservationStatus.CANCELLED);
        newReservation(r2, ReservationStatus.CANCELLED);
        newReservation(r3, ReservationStatus.CANCELLED);

        int count = reservationRepository.countByRequest_GuestIdAndStatus(guest, ReservationStatus.CANCELLED);
        assertThat(count).isEqualTo(2);
    }
}
