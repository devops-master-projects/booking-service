package org.example.booking.model;

public enum AvailabilityStatus {
    AVAILABLE,
    OCCUPIED,   // kad postoji aktivna rezervacija
    EXPIRED     // prošli datumi, opcionalno
}