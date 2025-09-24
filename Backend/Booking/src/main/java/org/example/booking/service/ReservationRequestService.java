package org.example.booking.service;

import lombok.RequiredArgsConstructor;
import org.example.booking.dto.ReservationRequestCreateDto;
import org.example.booking.dto.ReservationRequestResponseDto;
import org.example.booking.dto.ReservationRequestUpdateDto;
import org.example.booking.model.ReservationRequest;
import org.example.booking.model.RequestStatus;
import org.example.booking.repository.ReservationRequestRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationRequestService {

    private final ReservationRequestRepository repository;

    public ReservationRequestResponseDto create(UUID guestId, ReservationRequestCreateDto dto) {
        ReservationRequest req = new ReservationRequest();
        req.setGuestId(guestId);
        req.setAccommodationId(dto.getAccommodationId());
        req.setStartDate(dto.getStartDate());
        req.setEndDate(dto.getEndDate());
        req.setGuestCount(dto.getGuestCount());
        req.setStatus(RequestStatus.PENDING);

        ReservationRequest saved = repository.save(req);
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
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<ReservationRequestResponseDto> findByAccommodation(UUID accommodationId) {
        return repository.findByAccommodationId(accommodationId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public ReservationRequestResponseDto updateStatus(UUID id, RequestStatus status) {
        ReservationRequest req = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        req.setStatus(status);
        return toDto(repository.save(req));
    }

    private ReservationRequestResponseDto toDto(ReservationRequest req) {
        ReservationRequestResponseDto dto = new ReservationRequestResponseDto();
        dto.setId(req.getId());
        dto.setGuestId(req.getGuestId());
        dto.setAccommodationId(req.getAccommodationId());
        dto.setStartDate(req.getStartDate());
        dto.setEndDate(req.getEndDate());
        dto.setGuestCount(req.getGuestCount());
        dto.setStatus(req.getStatus());
        dto.setGuestFirstName(req.getGuestFirstName());
        dto.setGuestEmail(req.getGuestEmail());
        dto.setGuestLastName(req.getGuestLastName());
        dto.setCreatedAt(req.getCreatedAt());
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

}
