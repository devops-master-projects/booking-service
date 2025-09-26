package org.example.booking.service;
import lombok.RequiredArgsConstructor;
import org.example.booking.dto.CalendarIntervalDto;
import org.example.booking.model.*;
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


    private Set<CalendarIntervalDto> collectCalendarIntervals(
            UUID accommodationId, LocalDate startDate, LocalDate endDate, boolean includeReservations) {

        if (startDate == null) startDate = LocalDate.now();
        if (endDate == null) endDate = startDate.plusMonths(3);

        Set<CalendarIntervalDto> result = new HashSet<>();

        // availabilities - uzmi samo one koji su AVAILABLE
        Set<Availability> availabilities =
                availabilityRepository.findByAccommodationIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        accommodationId, AvailabilityStatus.AVAILABLE, endDate, startDate
                );

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

        if (includeReservations) {
            Set<Reservation> reservations = reservationRepository
                    .findByRequest_AccommodationIdAndRequest_StartDateLessThanEqualAndRequest_EndDateGreaterThanEqualAndStatus(
                            accommodationId,
                            endDate,
                            startDate,
                            ReservationStatus.CONFIRMED
                    );


            for (Reservation r : reservations) {
                result.add(new CalendarIntervalDto(
                        r.getId(),
                        r.getRequest().getStartDate(),
                        r.getRequest().getEndDate(),
                        "RESERVED",
                        null,
                        null
                ));
            }
        }

        return result;
    }


    public Set<CalendarIntervalDto> getCalendar(UUID accommodationId, LocalDate startDate, LocalDate endDate) {
        return collectCalendarIntervals(accommodationId, startDate, endDate, false);
    }

    public Set<CalendarIntervalDto> getCalendarHost(UUID accommodationId, LocalDate startDate, LocalDate endDate) {
        return collectCalendarIntervals(accommodationId, startDate, endDate, true);
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
