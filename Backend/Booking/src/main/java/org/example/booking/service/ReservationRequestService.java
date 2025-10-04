    package org.example.booking.service;

    import com.fasterxml.jackson.core.JsonProcessingException;
    import com.fasterxml.jackson.databind.ObjectMapper;
    import jakarta.transaction.Transactional;
    import lombok.RequiredArgsConstructor;
    import org.example.booking.dto.*;
    import org.example.booking.model.*;
    import org.example.booking.repository.AvailabilityRepository;
    import org.example.booking.repository.ReservationRepository;
    import org.example.booking.repository.ReservationRequestRepository;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.kafka.core.KafkaTemplate;
    import org.springframework.stereotype.Service;
    import org.springframework.web.client.RestTemplate;

    import java.time.LocalDate;
    import java.time.LocalDateTime;
    import java.util.ArrayList;
    import java.util.List;
    import java.util.Optional;
    import java.util.UUID;
    import java.util.stream.Collectors;

    @Service
    @RequiredArgsConstructor
    public class ReservationRequestService {

        private final ReservationRequestRepository repository;
        private final ReservationRepository reservationRepository;
        private final AvailabilityRepository availabilityRepository;
        private final RestTemplate restTemplate;
        private final KafkaTemplate<String, String> stringKafkaTemplate;
        private final KafkaTemplate<String, ReservationCreatedEvent> reservationKafkaTemplate;
        private final KafkaTemplate<String, RequestRespondedEvent> requestRespondedEventKafkaTemplate;
        private final ObjectMapper objectMapper;
        @Value("${accommodation.service.url}")
        private String accommodationServiceUrl;


        @Transactional
        public ReservationRequestResponseDto create(UUID guestId,
                                                    String guestEmail,
                                                    String guestLastName,
                                                    String guestFirstName,
                                                    ReservationRequestCreateDto dto) {
            ReservationRequest req = new ReservationRequest();
            req.setGuestId(guestId);
            req.setAccommodationId(dto.getAccommodationId());
            req.setStartDate(dto.getStartDate());
            req.setEndDate(dto.getEndDate());
            req.setGuestCount(dto.getGuestCount());
            req.setStatus(RequestStatus.PENDING);
            req.setGuestEmail(guestEmail);
            req.setGuestFirstName(guestFirstName);
            req.setGuestLastName(guestLastName);

            ReservationRequest saved = repository.save(req);

            ReservationCreatedEvent event = new ReservationCreatedEvent(
                    saved.getId(),
                    saved.getAccommodationId(),
                    saved.getGuestId(),
                    saved.getGuestFirstName(),
                    saved.getGuestLastName(),
                    saved.getGuestEmail(),
                    saved.getStartDate(),
                    saved.getEndDate(),
                    LocalDateTime.now()

            );

            reservationKafkaTemplate.send(
                    "reservation-created",
                    saved.getAccommodationId().toString(),
                    event
            );

            Boolean autoConfirm = restTemplate.getForObject(
                    accommodationServiceUrl + "/api/accommodations/" + dto.getAccommodationId() + "/auto-confirm",
                    AutoConfirmResponse.class
            ).isAutoConfirm();

            if (Boolean.TRUE.equals(autoConfirm)) {
                return updateStatus(saved.getId(), RequestStatus.APPROVED, "", "");
            }

            return toDto(saved);
        }

        public void delete(UUID id) {
            ReservationRequest req = repository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Request not found"));

            if (req.getStatus() != RequestStatus.PENDING) {
                throw new RuntimeException("Only PENDING requests can be deleted");
            }

            repository.deleteById(id);
        }


        public List<ReservationRequestResponseDto> findByGuest(UUID guestId, UUID accommodationId) {
            return repository.findByGuestIdAndAccommodationId(guestId, accommodationId).stream()
                    .map(req -> {
                        ReservationRequestResponseDto dto = toDto(req);
                        Optional<Reservation> reservationOpt = reservationRepository.findByRequest_Id(req.getId());
                        boolean cancelled = reservationOpt
                                .map(res -> res.getStatus() == ReservationStatus.CANCELLED)
                                .orElse(false);

                        dto.setConnectedReservationCancelled(cancelled);
                        return dto;
                    })
                    .collect(Collectors.toList());
        }


        public List<ReservationRequestResponseDto> findByAccommodation(UUID accommodationId) {
            return repository.findByAccommodationIdOrderByCreatedAtDesc(accommodationId).stream()
                    .map(this::toDto)
                    .toList();
        }



        public ReservationRequestResponseDto updateStatus(UUID id, RequestStatus status,
                                                          String hostName, String hostLastName) {
            System.out.println(">>> requestRespondedEventKafkaTemplate in service = " + requestRespondedEventKafkaTemplate);

            ReservationRequest req = repository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Request not found"));

            // 1. Ažuriraj status odmah
            req.setStatus(status);

            // 2. Ako je APPROVED – obradi overlap, kreiraj rezervaciju, ažuriraj availability
            if (status.equals(RequestStatus.APPROVED)) {
                List<ReservationRequest> overlapping = repository.findByAccommodationIdAndStatus(
                        req.getAccommodationId(), RequestStatus.PENDING
                );

                for (ReservationRequest other : overlapping) {
                    boolean overlaps = !(other.getEndDate().isBefore(req.getStartDate())
                            || other.getStartDate().isAfter(req.getEndDate()));
                    if (overlaps && !other.getId().equals(req.getId())) {
                        other.setStatus(RequestStatus.REJECTED);
                        repository.save(other);
                    }
                }

                Reservation reservation = new Reservation();
                reservation.setRequest(req);
                reservation.setConfirmedAt(LocalDateTime.now());
                reservation.setStatus(ReservationStatus.CONFIRMED);
                reservationRepository.save(reservation);

                adjustAvailabilitiesForReservation(req);
            }

            // 3. Sačuvaj req posle izmene
            ReservationRequest saved = repository.save(req);

            // 4. Kreiraj i pošalji event baziran na *saved* verziji
            RequestRespondedEvent event = new RequestRespondedEvent();
            event.setReservationRequestId(saved.getId());
            event.setStatus(saved.getStatus());
            event.setAccommodationId(saved.getAccommodationId());
            event.setRespondedAt(LocalDateTime.now());
            event.setGuestId(saved.getGuestId());
            event.setHostName(hostName);
            event.setHostLastName(hostLastName);

            requestRespondedEventKafkaTemplate.send(
                    "request-responded",
                    saved.getId().toString(),
                    event
            );

            return toDto(saved);
        }


        private void adjustAvailabilitiesForReservation(ReservationRequest req) {
            List<Availability> availabilities = availabilityRepository.findByAccommodationId(req.getAccommodationId());

            for (Availability availability : availabilities) {
                boolean overlaps = !(availability.getEndDate().isBefore(req.getStartDate())
                        || availability.getStartDate().isAfter(req.getEndDate()));

                if (!overlaps) continue;

                LocalDate Astart = availability.getStartDate();
                LocalDate Aend   = availability.getEndDate();
                LocalDate Rstart = req.getStartDate();
                LocalDate Rend   = req.getEndDate();

                // 1. Rezervacija potpuno pokriva availability
                if ((Rstart.isBefore(Astart) || Rstart.equals(Astart)) &&
                        (Rend.isAfter(Aend) || Rend.equals(Aend))) {
                    availability.setStatus(AvailabilityStatus.OCCUPIED);
                    availabilityRepository.save(availability);
                    sendAvailabilityEvent(availability, "AvailabilityStatusChanged");

                }
                // 2. Rezervacija unutar availability-ja → podeli na AVAILABLE i OCCUPIED
                else if (Rstart.isAfter(Astart) && Rend.isBefore(Aend)) {
                    Availability left = new Availability();
                    left.setAccommodationId(availability.getAccommodationId());
                    left.setStartDate(Astart);
                    left.setEndDate(Rstart.minusDays(1));
                    left.setPrice(availability.getPrice());
                    left.setPriceType(availability.getPriceType());
                    left.setStatus(AvailabilityStatus.AVAILABLE);

                    Availability occupied = new Availability();
                    occupied.setAccommodationId(availability.getAccommodationId());
                    occupied.setStartDate(Rstart);
                    occupied.setEndDate(Rend);
                    occupied.setPrice(availability.getPrice());
                    occupied.setPriceType(availability.getPriceType());
                    occupied.setStatus(AvailabilityStatus.OCCUPIED);

                    Availability right = new Availability();
                    right.setAccommodationId(availability.getAccommodationId());
                    right.setStartDate(Rend.plusDays(1));
                    right.setEndDate(Aend);
                    right.setPrice(availability.getPrice());
                    right.setPriceType(availability.getPriceType());
                    right.setStatus(AvailabilityStatus.AVAILABLE);

                    availabilityRepository.delete(availability);
                    availabilityRepository.save(left);
                    availabilityRepository.save(occupied);
                    availabilityRepository.save(right);
                    sendAvailabilityEvent(availability, "AvailabilityDeleted");
                    sendAvailabilityEvent(left, "AvailabilityStatusChanged");
                    sendAvailabilityEvent(occupied, "AvailabilityStatusChanged");
                    sendAvailabilityEvent(right, "AvailabilityStatusChanged");

                }
                // 3. Preklapanje na početku
                else if ((Rstart.isBefore(Astart) || Rstart.equals(Astart)) && Rend.isBefore(Aend)) {
                    availability.setStartDate(Rend.plusDays(1));

                    Availability occupied = new Availability();
                    occupied.setAccommodationId(availability.getAccommodationId());
                    occupied.setStartDate(Astart);
                    occupied.setEndDate(Rend);
                    occupied.setPrice(availability.getPrice());
                    occupied.setPriceType(availability.getPriceType());
                    occupied.setStatus(AvailabilityStatus.OCCUPIED);

                    availabilityRepository.save(availability);
                    availabilityRepository.save(occupied);
                    sendAvailabilityEvent(availability, "AvailabilityStatusChanged");
                    sendAvailabilityEvent(occupied, "AvailabilityStatusChanged");

                }
                // 4. Preklapanje na kraju
                else if (Rstart.isAfter(Astart) && (Rend.isAfter(Aend) || Rend.equals(Aend))) {
                    availability.setEndDate(Rstart.minusDays(1));

                    Availability occupied = new Availability();
                    occupied.setAccommodationId(availability.getAccommodationId());
                    occupied.setStartDate(Rstart);
                    occupied.setEndDate(Aend);
                    occupied.setPrice(availability.getPrice());
                    occupied.setPriceType(availability.getPriceType());
                    occupied.setStatus(AvailabilityStatus.OCCUPIED);

                    availabilityRepository.save(availability);
                    availabilityRepository.save(occupied);
                    sendAvailabilityEvent(availability, "AvailabilityStatusChanged");
                    sendAvailabilityEvent(occupied, "AvailabilityStatusChanged");

                }
            }
        }

        public void cancelReservation(UUID requestId) {
            ReservationRequest request = repository.findById(requestId)
                    .orElseThrow(() -> new RuntimeException("Request not found"));

            Reservation reservation = reservationRepository.findByRequest_Id(requestId)
                    .orElseThrow(() -> new RuntimeException("Reservation not found"));

            LocalDate start = request.getStartDate();
            LocalDate end = request.getEndDate();
            if (LocalDate.now().isAfter(start.minusDays(1))) {
                throw new RuntimeException("Too late to cancel reservation");
            }

            // 2. Obeleži rezervaciju kao CANCELLED
            reservation.setStatus(ReservationStatus.CANCELLED);
            reservationRepository.save(reservation);

            reservation.setStatus(ReservationStatus.CANCELLED);
            ReservationCreatedEvent event = new ReservationCreatedEvent(
                    request.getId(),
                    request.getAccommodationId(),
                    request.getGuestId(),
                    request.getGuestFirstName(),
                    request.getGuestLastName(),
                    request.getGuestEmail(),
                    request.getStartDate(),
                    request.getEndDate(),
                    LocalDateTime.now()

            );

            reservationKafkaTemplate.send(
                    "reservation-cancelled",
                    request.getAccommodationId().toString(),
                    event
            );


            // 3. Vrati availabilities na AVAILABLE
            List<Availability> availabilities = availabilityRepository.findByAccommodationId(request.getAccommodationId());

            for (Availability availability : availabilities) {
                boolean overlaps = !(availability.getEndDate().isBefore(start)
                        || availability.getStartDate().isAfter(end));

                if (overlaps && availability.getStatus() == AvailabilityStatus.OCCUPIED) {
                    availability.setStatus(AvailabilityStatus.AVAILABLE);
                    availabilityRepository.save(availability);
                    sendAvailabilityEvent(availability, "AvailabilityStatusChanged");

                }
            }

            mergeAdjacentAvailabilities(request.getAccommodationId());

        }

        public void mergeAdjacentAvailabilities(UUID accommodationId) {
            // Učitaj sve AVAILABLE intervale za smeštaj, sortirane po startDate
            List<Availability> availabilities = availabilityRepository
                    .findByAccommodationIdAndStatusOrderByStartDateAsc(accommodationId, AvailabilityStatus.AVAILABLE);

            if (availabilities.isEmpty()) {
                return;
            }

            List<Availability> merged = new ArrayList<>();
            Availability current = availabilities.get(0);

            for (int i = 1; i < availabilities.size(); i++) {
                Availability next = availabilities.get(i);

                boolean canMerge =
                        current.getEndDate().plusDays(1).equals(next.getStartDate()) && // moraju da se dodiruju
                                current.getPrice().compareTo(next.getPrice()) == 0 &&   // ista cena
                                current.getPriceType() == next.getPriceType();          // isti priceType

                if (canMerge) {
                    current.setEndDate(next.getEndDate());
                    availabilityRepository.delete(next);
                    sendAvailabilityEvent(next, "AvailabilityDeleted");
                    sendAvailabilityEvent(current, "AvailabilityUpdated");
                } else {
                    merged.add(current);
                    current = next;
                }
            }

            merged.add(current);

            for (Availability a : merged) {
                availabilityRepository.save(a);
                sendAvailabilityEvent(a, "AvailabilityUpdated");
            }
        }




        private ReservationRequestResponseDto toDto(ReservationRequest req) {
            ReservationRequestResponseDto dto = new ReservationRequestResponseDto();
            dto.setId(req.getId());
            dto.setGuestId(req.getGuestId());
            dto.setAccommodationId(req.getAccommodationId());
            dto.setGuestEmail(req.getGuestEmail());
            dto.setGuestFirstName(req.getGuestFirstName());
            dto.setGuestLastName(req.getGuestLastName());
            dto.setStartDate(req.getStartDate());
            dto.setEndDate(req.getEndDate());
            dto.setGuestCount(req.getGuestCount());
            dto.setCreatedAt(req.getCreatedAt());
            dto.setStatus(req.getStatus());

            dto.setConnectedReservationCancelled(
                    reservationRepository.findByRequest_Id(req.getId())
                            .map(r -> r.getStatus() == ReservationStatus.CANCELLED)
                            .orElse(false)
            );

            int cancellations = reservationRepository
                    .countByRequest_GuestIdAndStatus(req.getGuestId(), ReservationStatus.CANCELLED);
            dto.setCancellationsCount(cancellations);

            return dto;
        }


        public ReservationRequestResponseDto update(UUID id, ReservationRequestUpdateDto dto) {
            ReservationRequest req = repository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Request not found"));

            if (req.getStatus() != RequestStatus.PENDING) {
                throw new RuntimeException("Only PENDING requests can be updated");
            }

            req.setStartDate(dto.getStartDate());
            req.setEndDate(dto.getEndDate());
            req.setGuestCount(dto.getGuestCount());

            ReservationRequest saved = repository.save(req);
            return toDto(saved);
        }

        private void sendAvailabilityEvent(Availability availability, String type) {
            AvailabilityEvent event = AvailabilityEvent.builder()
                    .eventType(type)
                    .id(availability.getId().toString())
                    .accommodationId(availability.getAccommodationId().toString())
                    .startDate(availability.getStartDate())
                    .endDate(availability.getEndDate())
                    .price(availability.getPrice())
                    .priceType(availability.getPriceType().name())
                    .status(availability.getStatus().name())
                    .build();

            try {
                String json = objectMapper.writeValueAsString(event);
                stringKafkaTemplate.send("availability-events", availability.getAccommodationId().toString(), json);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize AvailabilityEvent", e);
            }
        }

        /**
         * Marks all reservations that ended before today and are still CONFIRMED
         * as COMPLETED.
         */
        @Transactional
        public void markCompletedReservations() {
            LocalDate today = LocalDate.now();

            List<Reservation> toComplete = reservationRepository
                    .findAllByStatusAndRequest_EndDateBefore(ReservationStatus.CONFIRMED, today);

            toComplete.forEach(r -> r.setStatus(ReservationStatus.COMPLETED));

            if (!toComplete.isEmpty()) {
                reservationRepository.saveAll(toComplete);
            }
        }


    }
