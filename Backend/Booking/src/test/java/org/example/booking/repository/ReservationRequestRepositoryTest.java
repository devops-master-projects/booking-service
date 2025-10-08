package org.example.booking.repository;

import org.example.booking.model.RequestStatus;
import org.example.booking.model.ReservationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")

class ReservationRequestRepositoryTest {

    @Autowired
    private ReservationRequestRepository repository;

    private ReservationRequest createRequest(UUID guestId, UUID accommodationId, RequestStatus status) {
        ReservationRequest req = new ReservationRequest();
        req.setGuestId(guestId);
        req.setAccommodationId(accommodationId);
        req.setStatus(status);
        req.setStartDate(LocalDate.now());
        req.setEndDate(LocalDate.now().plusDays(2));
        req.setGuestCount(2);
        req.setGuestEmail("test@example.com");
        req.setGuestFirstName("John");
        req.setGuestLastName("Doe");
        return req;
    }

    @Test
    @DisplayName("findByAccommodationId returns correct requests")
    void testFindByAccommodationId() {
        UUID accId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        ReservationRequest req = createRequest(guestId, accId, RequestStatus.PENDING);
        repository.save(req);
        List<ReservationRequest> found = repository.findByAccommodationId(accId);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getAccommodationId()).isEqualTo(accId);
    }

    @Test
    @DisplayName("findByAccommodationIdAndStatus returns correct requests")
    void testFindByAccommodationIdAndStatus() {
        UUID accId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        ReservationRequest req1 = createRequest(guestId, accId, RequestStatus.PENDING);
        ReservationRequest req2 = createRequest(guestId, accId, RequestStatus.REJECTED);
        repository.save(req1);
        repository.save(req2);
        List<ReservationRequest> found = repository.findByAccommodationIdAndStatus(accId, RequestStatus.PENDING);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getStatus()).isEqualTo(RequestStatus.PENDING);
    }

    @Test
    @DisplayName("findByGuestId returns correct requests")
    void testFindByGuestId() {
        UUID accId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        ReservationRequest req = createRequest(guestId, accId, RequestStatus.PENDING);
        repository.save(req);
        List<ReservationRequest> found = repository.findByGuestId(guestId);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getGuestId()).isEqualTo(guestId);
    }

    @Test
    @DisplayName("findByGuestIdAndAccommodationId returns correct requests")
    void testFindByGuestIdAndAccommodationId() {
        UUID accId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        ReservationRequest req = createRequest(guestId, accId, RequestStatus.PENDING);
        repository.save(req);
        List<ReservationRequest> found = repository.findByGuestIdAndAccommodationId(guestId, accId);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getGuestId()).isEqualTo(guestId);
        assertThat(found.get(0).getAccommodationId()).isEqualTo(accId);
    }
}

