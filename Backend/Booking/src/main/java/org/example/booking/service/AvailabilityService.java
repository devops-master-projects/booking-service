package org.example.booking.service;
import lombok.RequiredArgsConstructor;
import org.example.booking.dto.CalendarIntervalDto;
import org.example.booking.model.Availability;
import org.example.booking.model.PriceType;
import org.example.booking.model.Reservation;
import org.example.booking.model.ReservationStatus;
import org.example.booking.repository.AvailabilityRepository;
import org.example.booking.repository.ReservationRepository;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final AvailabilityRepository availabilityRepository;
    private final ReservationRepository reservationRepository;
    public Availability defineAvailability(UUID accommodationId,
                                           LocalDate startDate,
                                           LocalDate endDate,
                                           BigDecimal price,
                                           PriceType priceType) {

        Availability availability = new Availability();
        availability.setAccommodationId(accommodationId);
        availability.setStartDate(startDate);
        availability.setEndDate(endDate);
        availability.setPrice(price);
        availability.setPriceType(priceType != null ? priceType : PriceType.NORMAL);

        return availabilityRepository.save(availability);
    }



    public Availability updateAvailability(UUID id,
                                           LocalDate startDate,
                                           LocalDate endDate,
                                           BigDecimal price,
                                           PriceType priceType) {
        Availability availability = availabilityRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Availability not found"));

        availability.setStartDate(startDate);
        availability.setEndDate(endDate);
        availability.setPrice(price);
        availability.setPriceType(priceType);

        return availabilityRepository.save(availability);
    }

    public Set<CalendarIntervalDto> getCalendar(UUID accommodationId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now();
        if (endDate == null) endDate = startDate.plusMonths(3);

        Set<Availability> availabilities =
                availabilityRepository.findByAccommodationIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        accommodationId, endDate, startDate
                );

        Set<Reservation> reservations =
                reservationRepository.findByRequest_AccommodationIdAndRequest_StartDateLessThanEqualAndRequest_EndDateGreaterThanEqual(
                        accommodationId, endDate, startDate
                );

        Set<CalendarIntervalDto> result = new HashSet<>();

        // availabilities
        for (Availability a : availabilities) {
            result.add(new CalendarIntervalDto(
                    a.getId(),
                    a.getStartDate(),
                    a.getEndDate(),
                    "AVAILABLE",
                    a.getPrice(),
                    a.getPriceType()
            ));
        }

        // reservations
        for (Reservation r : reservations) {
            result.add(new CalendarIntervalDto(
                    r.getId(),
                    r.getRequest().getStartDate(),
                    r.getRequest().getEndDate(),
                    "RESERVED",
                    null,
                    null // rezervacija nema priceType
            ));
        }

        return result;
    }
    public void deleteAvailability(UUID id) {
        Availability availability = availabilityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Availability not found"));

        boolean hasReservations = reservationRepository.existsByRequestAccommodationIdAndRequestStartDateLessThanEqualAndRequestEndDateGreaterThanEqualAndStatus(
                availability.getAccommodationId(),
                availability.getEndDate(),
                availability.getStartDate(),
                ReservationStatus.CONFIRMED
        );

        if (hasReservations) {
            throw new IllegalStateException("Cannot delete availability with active reservations");
        }

        availabilityRepository.deleteById(id);
    }




}
