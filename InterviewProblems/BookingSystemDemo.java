package InterviewProblems;

/**
 * Functional Requirements
 * 1. User should be able to search for available seats
 * 2. Diff type of seats available - (PLATINUM, GOLD, SILVER)
 * 3. Different type of screens available - (3D, HD, DOLBY)
 * 4. Multiple Payment Methods - UPI, CARD
 * 5. User should be able to book show
 * 6. If a seat is locked, no other user is allowed to book a seat for same show
 * 7. Seat lock expiry is 5min
 * 8. Cancel Booking
 * 9. Idempotent payment / booking confirmation
 * 
 * Non Functional Requirements
 * 1. Thread Safety
 * 2. Atomicity - locking a multiple seat request is all-or-nothing
 * 
 * Entities
 * 1. Seat
 * 2. Show
 * 3. Screen
 * 4. Movie
 * 5. Booking
 * 6. SeatLock
 * 7. SeatLockManaget
 * 8. Payment
 * 9. PaymentStrategy
 * 10. BookingService
 * 
 */

import java.util.*;
import java.time.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.*;

enum SeatType {
  SILVER, GOLD, PLATINUM
}

enum SeatStatus {
  AVAILABLE, LOCKED, BOOKED
}

enum BookingStatus {
  CONFIRMED, FAILED, CANCELLED, PENDING
}

enum PaymentMethod {
  CARD, UPI
}

enum ScreenType {
  SCREEN_3D, SCREEN_2D, HD
}

class Seat {
  String seatId;
  SeatType seatType;
  String seatNumber;
  double price;

  public Seat(String seatId, String seatNumber, SeatType seatType, double price) {
    this.seatId = seatId;
    this.seatNumber = seatNumber;
    this.seatType = seatType;
    this.price = price;
  }
}

class Screen {
  String screenId;
  ScreenType screenType;
  List<Seat> seats;

  public Screen(String screenId, ScreenType screenType, List<Seat> seats) {
    this.screenId = screenId;
    this.screenType = screenType;
    this.seats = seats;
  }
}

class Movie {
  String movieId;
  String title;

  public Movie(String movieId, String movieName) {
    this.movieId = movieId;
    this.title = movieName;
  }
}

class Show {
  String showId;
  Movie movie;
  LocalDateTime startTime;
  Screen screen;
  Map<String, SeatStatus> seatStatusMap;

  public Show(String showId, Movie movie, Screen screen, LocalDateTime startTime) {
    this.showId = showId;
    this.movie = movie;
    this.screen = screen;
    this.startTime = startTime;
    this.seatStatusMap = new ConcurrentHashMap<>();
    for (Seat s : screen.seats) {
      seatStatusMap.put(s.seatId, SeatStatus.AVAILABLE);
    }
  }

  List<Seat> getAvailableSeats() {
    List<Seat> available = new ArrayList<>();
    for (Seat s : screen.seats) {
      if (seatStatusMap.get(s.seatId) == SeatStatus.AVAILABLE) {
        available.add(s);
      }
    }
    return available;
  }

  Seat getSeatById(String seatId) {
    for (Seat s : screen.seats) {
      if (s.seatId.equals(seatId)) {
        return s;
      }
    }
    throw new NoSuchElementException("Seat not found " + seatId);
  }

  void markBooked(List<String> seatIds) {
    for (String seatId : seatIds) {
      seatStatusMap.put(seatId, SeatStatus.BOOKED);
    }
  }

  void markAvailable(List<String> seatIds) {
    for (String seatId : seatIds) {
      seatStatusMap.put(seatId, SeatStatus.AVAILABLE);
    }
  }
}

class Booking {
  String bookingId;
  String showId;
  List<String> seatIds;
  String userId;
  String idempotencyKey;
  volatile BookingStatus status;
  double amount;

  public Booking(String bookingId, String userId, String showId, List<String> seatIds, double amount,
      String idempotencyKey) {
    this.bookingId = bookingId;
    this.userId = userId;
    this.showId = showId;
    this.seatIds = seatIds;
    this.amount = amount;
    this.idempotencyKey = idempotencyKey;
    this.status = BookingStatus.PENDING;
  }

  public void setStatus(BookingStatus status) {
    this.status = status;
  }

  @Override
  public String toString() {
    return "Booking{id=" + bookingId + ", user=" + userId + ", show=" + showId +
        ", seats=" + seatIds + ", status=" + status + ", amount=" + amount + "}";
  }

}

class SeatLock {
  String seatId;
  String showId;
  String userId;
  Instant expiryTime;

  public SeatLock(String seatId, String showId, String userId, Instant expiryTime) {
    this.seatId = seatId;
    this.showId = showId;
    this.userId = userId;
    this.expiryTime = expiryTime;
  }
}

class SeatLockManager {
  final long LOCK_TTL_SECONDS = 300;
  final ReentrantLock mutex = new ReentrantLock();
  final Map<String, SeatLock> locks = new HashMap<>();

  private String key(String showId, String seatId) {
    return showId + " # " + seatId;
  }

  boolean isExpired(SeatLock seatLock) {
    return Instant.now().isAfter(seatLock.expiryTime);
  }

  public boolean lockSeats(String showId, List<String> seatIds, String userId) {
    mutex.lock();
    try {
      for (String seatId : seatIds) {
        SeatLock existing = locks.get(key(showId, seatId));
        if (existing != null && !isExpired(existing)) {
          return false;
        }
      }
      Instant expiry = Instant.now().plusSeconds(LOCK_TTL_SECONDS);
      for (String seatId : seatIds) {
        locks.put(key(showId, seatId), new SeatLock(seatId, showId, userId, expiry));
      }
      return true;
    } finally {
      mutex.unlock();
    }
  }

  public void releaseLocks(String showId, List<String> seatIds) {
    mutex.lock();
    try {
      for (String seatId : seatIds) {
        locks.remove(key(showId, seatId));
      }
    } finally {
      mutex.unlock();
    }
  }

  public int activeLockCount() {
    mutex.lock();
    try {
      return locks.size();
    } finally {
      mutex.unlock();
    }
  }
}

class PaymentResult {
  String transactionId;
  boolean success;

  public PaymentResult(boolean success, String transactionId) {
    this.success = success;
    this.transactionId = transactionId;
  }
}

interface PaymentStrategy {
  PaymentResult pay(double amount);
}

class UpiPayment implements PaymentStrategy {
  @Override
  public PaymentResult pay(double amount) {
    System.out.println("Paid through UPI");
    return new PaymentResult(true, UUID.randomUUID().toString());
  }
}

class CardPayment implements PaymentStrategy {
  @Override
  public PaymentResult pay(double amount) {
    System.out.println("Paid through Card");
    return new PaymentResult(true, UUID.randomUUID().toString());
  }
}

class PaymentService {
  public PaymentResult pay(double amount, PaymentMethod method) {
    PaymentStrategy strategy = switch (method) {
      case UPI -> new UpiPayment();
      case CARD -> new CardPayment();
    };
    return strategy.pay(amount);
  }

  public void refund(String transactionId, double amount) {
    System.out.println("  [REFUND] ₹" + amount + " for txn " + transactionId);
  }
}

class BookingService {
  SeatLockManager lockManager = new SeatLockManager();
  PaymentService paymentService = new PaymentService();
  Map<String, Show> shows = new ConcurrentHashMap<>();
  Map<String, Booking> bookingsById = new ConcurrentHashMap<>();
  Map<String, Booking> idempotencyCache = new ConcurrentHashMap<>();

  public void addShow(Show show) {
    shows.put(show.showId, show);
  }

  private Show getShowOrThrow(String showId) {
    Show show = shows.get(showId);
    if (show == null)
      throw new NoSuchElementException("Show not found: " + showId);
    return show;
  }

  public List<Seat> searchAvailableSeats(String showId) {
    Show show = getShowOrThrow(showId);
    return show.getAvailableSeats();
  }

  public Booking bookShow(String userId, String showId, List<String> seatIds, PaymentMethod method,
      String idempotencyKey) {
    Booking cached = idempotencyCache.get(idempotencyKey);
    if (cached != null) {
      System.out.println("[Idenpotent] returning esisting result for key " + idempotencyKey);
      return cached;
    }

    Show show = getShowOrThrow(showId);
    boolean locked = lockManager.lockSeats(showId, seatIds, userId);

    if (!locked) {
      throw new NoSuchElementException("One or more seats already locked / booked");
    }
    double amount = 0L;
    for (String seatId : seatIds)
      amount += show.getSeatById(seatId).price;
    Booking booking = new Booking(UUID.randomUUID().toString(), userId, showId, seatIds, amount, idempotencyKey);
    idempotencyCache.put(idempotencyKey, booking);
    bookingsById.put(booking.bookingId, booking);
    try {
      PaymentResult result = paymentService.pay(amount, method);

      if (result.success) {
        lockManager.releaseLocks(showId, seatIds);
        show.markBooked(seatIds);
        booking.setStatus(BookingStatus.CONFIRMED);
      } else {
        lockManager.releaseLocks(showId, seatIds);
        booking.setStatus(BookingStatus.FAILED);
      }
    } catch (Exception e) {
      lockManager.releaseLocks(showId, seatIds);
      booking.setStatus(BookingStatus.FAILED);
    }
    return booking;
  }

  public void cancelBooking(String bookingId) {
    Booking booking = bookingsById.get(bookingId);
    if (booking == null) {
      throw new NoSuchElementException("Booking not found with id: " + bookingId);
    }
    if (booking.status != BookingStatus.CONFIRMED) {
      throw new IllegalStateException("Only confirmed bookings can be cancelled");
    }
    Show show = getShowOrThrow(booking.showId);
    show.markAvailable(booking.seatIds);
    booking.setStatus(BookingStatus.CANCELLED);
    paymentService.refund(bookingId, booking.amount);
  }

  public SeatLockManager getLockManager() {
    return lockManager;
  }
}

public class BookingSystemDemo {
  public static void main(String[] args) {
    List<Seat> seatList = List.of(
        new Seat("S1", "A1", SeatType.PLATINUM, 500),
        new Seat("S2", "A2", SeatType.PLATINUM, 500),
        new Seat("S3", "B1", SeatType.GOLD, 300),
        new Seat("S4", "B2", SeatType.GOLD, 300),
        new Seat("S5", "C1", SeatType.SILVER, 150));

    Screen screen = new Screen("SCR1", ScreenType.SCREEN_3D, seatList);
    Movie movie = new Movie("M1", "Interstellar");
    Show show = new Show("SHOW1", movie, screen, LocalDateTime.now().plusHours(3));

    BookingService bookingService = new BookingService();
    bookingService.addShow(show);

    System.out.println("Available seats: " + bookingService.searchAvailableSeats("SHOW1").size());

    System.out.println("\n--- Demo 1: concurrent booking race for seats S1,S2 ---");
    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<String> targetSeats = List.of("S1", "S2");

    Callable<Booking> userA = () -> bookingService.bookShow("user-A", "SHOW1", targetSeats, PaymentMethod.UPI, "key-1");
    Callable<Booking> userB = () -> bookingService.bookShow("user-B", "SHOW1", targetSeats, PaymentMethod.CARD,
        "key-2");

    Future<Booking> fA = pool.submit(userA);
    Future<Booking> fB = pool.submit(userB);

    String resultA, resultB;
    try {
      resultA = fA.get().toString();
    } catch (Exception e) {
      resultA = "FAILED -> " + e.getCause().getMessage();
    }

    try {
      resultB = fB.get().toString();
    } catch (Exception e) {
      resultB = "FAILED -> " + e.getMessage();
    }

    System.out.println("User A result " + resultA);
    System.out.println("User B result " + resultB);

    pool.shutdown();

  }
}
