package LLD_Practice;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/*
Requirements
1. There are multiple theatres in a city
2. There are multiple screens in a theatre
3. There might be multiple shows running on a screen at different time
4. Screen has multiple seats
5. Concurrent booking not allowed
6. User can cancel the booking till 2 hours before show start time
7. User can search for shows in a city

Additional (Optional) -
8. Retry mechanism for failed booking
9. Max allowed seat bookings - 5
10. Payment 

Core Entities 
1. Theatre (id, city, address, List<Screen> screens)
2. Screen (id, screenNo, List<Seat> seats, ScreenType)
3. ScreenType (S_2D, S_3D, S_HD)
4. Show (id, name, startTime, endTime, screen)
5. Seat - (id, seatNo, seatType)
6. SeatType - (GOLD, PREMIUM, SILVER)
7. ShowSeat - (Show, Seat, SeatStatus, price)
8. SeatStatus - (BOOKED, AVAILABLE)
*/
public class DesignMovieBookingSystem {

  static class Theatre {
    int id;
    String city;
    String address;
  }

  static enum SeatType {
    GOLD, PLATINUM, SILVER
  }

  static class Seat {
    int id;
    int seatNo;
    SeatType seatType;
  }

  static enum ScreenType {
    S_2D, S_3D, S_HD
  }

  static class Screen {
    int id;
    int screenNo;
    List<Seat> seats;
    ScreenType screenType;
    Theatre theatre;
  }

  static class Show {
    int id;
    Instant startTime;
    Instant endTime;
    String name;
    Screen screen;
  }

  static enum SeatStatus {
    AVAILABLE, BOOKED
  }

  static class ShowSeat {
    int id;
    Show show;
    Seat seat;
    SeatStatus status;
    double price;
  }

  static enum BookingStatus {
    CONFIRMED, CANCELLED,
  }

  static class Booking {
    String bookingId;
    int userId;
    Show show;
    List<ShowSeat> seats;
    BookingStatus status;
    Instant createdAt;
  }

  static class ShowService {
    Map<String, Show> shows;

    ShowService(Map<String, Show> shows) {
      this.shows = shows;
    }

    List<Show> searchShowsByCity(String city) {
      return shows.values().stream()
          .filter(show -> show.screen != null)
          .filter(show -> show.screen.theatre != null)
          .filter(show -> show.screen.theatre.city == city)
          .toList();
    }
  }

  static class BookingService {
    Map<Integer, Show> shows;
    Map<Integer, ShowSeat> showSeats;
    Map<String, Booking> bookings;
    private final ReentrantLock lock;

    BookingService(Map<Integer, Show> shows, Map<Integer, ShowSeat> showSeats, Map<String, Booking> bookings) {
      lock = new ReentrantLock();
      this.showSeats = showSeats;
      this.shows = shows;
      this.bookings = bookings;
    }

    Booking bookSeats(int userId, int showId, List<Integer> showSeatIds) {
      lock.lock();
      try {
        Show show = shows.get(showId);
        if (show == null)
          throw new IllegalArgumentException("Show not found");

        if (showSeatIds == null || showSeatIds.isEmpty())
          throw new IllegalArgumentException("Select at least one seat");

        if (new HashSet<>(showSeatIds).size() != showSeatIds.size())
          throw new IllegalArgumentException("Duplicate seats are not allowed");

        List<ShowSeat> bookingSeats = new ArrayList<>();

        for (int showSeatId : showSeatIds) {
          ShowSeat showSeat = showSeats.get(showSeatId);

          if (showSeat == null)
            throw new IllegalArgumentException("Show seat not found");

          if (showSeat.show.id != showId)
            throw new IllegalArgumentException("Seat does not belong to this show");

          if (showSeat.status != SeatStatus.AVAILABLE)
            throw new IllegalStateException("Seat is unavailable");

          bookingSeats.add(showSeat);
        }

        // Mutate state only after all seats pass validation
        bookingSeats.forEach(
            seat -> seat.status = SeatStatus.BOOKED);

        Booking booking = new Booking();
        booking.bookingId = UUID.randomUUID().toString();
        booking.userId = userId;
        booking.show = show;
        booking.seats = bookingSeats;
        booking.status = BookingStatus.CONFIRMED;
        booking.createdAt = Instant.now();

        bookings.put(booking.bookingId, booking);
        return booking;

      } finally {
        lock.unlock();
      }
    }

    void cancelBooking(String bookingId, int userId) {
      lock.lock();
      try {
        // Validate booking
        Booking booking = bookings.get(bookingId);
        if (booking == null) {
          throw new IllegalArgumentException("Booking does not exists");
        }
        if (booking.status == BookingStatus.CANCELLED) {
          throw new IllegalArgumentException("Booking already cancelled");
        }
        if (booking.userId != userId) {
          throw new IllegalArgumentException("You cannot cancel another user's booking");
        }
        Instant cancellationDuration = booking.show.startTime.minus(Duration.ofHours(2));
        if (Instant.now().isAfter(cancellationDuration)) {
          throw new IllegalStateException("Cancellation is allowed until 2 hours before show");
        }

        // Change seats status as available
        booking.seats.forEach((seat) -> seat.status = SeatStatus.AVAILABLE);
        // Change booking status as Cancel
        booking.status = BookingStatus.CANCELLED;
      } finally {
        lock.unlock();
      }
    }
  }
}
