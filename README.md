# booking-service

## Running Tests

To run all unit and integration tests, use the following command from the `Backend/Booking` directory:

**Windows:**
```
gradlew.bat test
```
**Linux/macOS:**
```
./gradlew test
```

To run a specific test class (e.g., ReservationRequestRepositoryTest):
```
gradlew.bat test --tests org.example.booking.repository.ReservationRequestRepositoryTest
```

**Requirements:**
- No additional database setup is required; tests use an in-memory H2 database by default.

## Test Coverage Report (JaCoCo)

Running tests will automatically create a code coverage report using JaCoCo.

The HTML report will be available at:
```
Backend/Booking/build/reports/jacoco/test/html/index.html
```

Open this file in your browser to view detailed coverage metrics for your tests.
