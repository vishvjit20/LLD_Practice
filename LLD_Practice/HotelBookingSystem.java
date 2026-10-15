package LLD_Practice;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/*
Functional Requirements
1. User should be able to book room in a hotel
2. Cancel booking
3. BookingState (PENDING/CONFIRM/CANCEL)
4. Payment on confirm booking
5. PaymentMethods (UPI / CARD)
6. Refund on cancellation
7. Haandle duplicate payment 

Non Functional 
1. Thread safety - No 2 users can book the same room at a time
2. Retry Mechanism
3. Extensible

Core Entities 
1. Hotel - hotelId, name, address, bookings
2. Booking - bookingId, roomId, checkInTime, checkoutTime, bookingStatus, userId
3. Room - roomId, hotelId, roomNo, checkInTime, checkoutTime
4. BookingState - PENDING, CONFIRM, CANCEL
5. PaymentMethod - UPI, CARD
6. PaymentStrategy - UpiPaymentStrategy, CardPaymentStrategy
      - pay()
      - refund()
 
7. HotelService
    - book(),
    - cancelBooking()
8. Payment - paymentId, amount, paymentStatus, paymentMethod, booking
*/
import java.util.concurrent.locks.ReentrantLock;

public class HotelBookingSystem {

  static enum BookingState {
    PENDING, CONFIRM, CANCEL
  }

  static enum PaymentState {
    PENDING, CONFIRM, CANCEL, REFUND
  }

  static enum PaymentMethod {
    UPI, CARD
  }

  static interface PaymentStrategy {
    void pay(double amount);

    boolean refund(String transactionId);
  }

  static class UpiPaymentStrategy implements PaymentStrategy {
    @Override
    public void pay(double amount) {
      System.out.println("Payment done through UPI");
    }

    @Override
    public boolean refund(String transactionId) {
      System.out.println("Refunded for id " + transactionId);
      return true;
    }

  }

  static class CardPaymentStrategy implements PaymentStrategy {
    @Override
    public void pay(double amount) {
      System.out.println("Payment done through card");
    }

    @Override
    public boolean refund(String transactionId) {
      System.out.println("Refunded for id " + transactionId);
      return true;
    }
  }

  static class Booking {
    String bookingId;
    String roomId;
    Instant checkInTime;
    Instant checkoutTime;
    String userId;
    BookingState bookingStatus;
  }

  static class Hotel {
    String hotelId;
    String hotelName;
    String address;
    List<Room> rooms;
    List<Booking> bookings;

  }

  static class Room {
    String roomId;
    int roomNo;
  }

  static class Payment {
    String paymentId;
    Booking booking;
    PaymentMethod paymentMethod;
    double amount;
    PaymentState paymentStatus;
    int retryCount;
  }

  static class PaymentService {
    double amount;

    Map<String, Payment> paymentByIds = new ConcurrentHashMap<>();
    Map<PaymentMethod, PaymentStrategy> paymentStrategyMap = new ConcurrentHashMap<>();

    PaymentService(double amount) {
      this.amount = amount;
      paymentStrategyMap.put(PaymentMethod.CARD, new CardPaymentStrategy());
      paymentStrategyMap.put(PaymentMethod.UPI, new UpiPaymentStrategy());
    }

    Payment pay(Payment payment, Booking booking) {
      if (booking.bookingStatus != BookingState.PENDING) {
        throw new IllegalStateException("Something went wrong");
      }
      if (paymentByIds.putIfAbsent(payment.paymentId, payment) != null) {
        throw new IllegalStateException("Duplicate payment");
      }

      int retryCount = payment.retryCount;
      while (retryCount > 0) {
        try {
          paymentStrategyMap.get(payment.paymentMethod).pay(amount);
          payment.amount = amount;
          payment.booking = booking;
          payment.paymentStatus = PaymentState.CONFIRM;
          return payment;
        } catch (Exception e) {
          if (retryCount == 0) {
            System.out.println("Payment failed");
          }
          retryCount--;
          System.out.println("Retrying ...");
        }
      }

      payment.paymentStatus = PaymentState.CANCEL;
      throw new IllegalStateException("Payment failed after retries");

    }

    boolean refundBooking(String paymentId) {
      return paymentStrategyMap.get(paymentByIds.get(paymentId).paymentMethod).refund(paymentId);
    }
  }

  static class BookingService {
    Map<String, Booking> bookings = new ConcurrentHashMap<>();
    Map<String, String> roomReservations = new ConcurrentHashMap<>();
    Map<String, Payment> payments = new ConcurrentHashMap<>();
    Map<String, ReentrantLock> bookingLocks = new ConcurrentHashMap<>();
    PaymentService paymentService;

    BookingService(PaymentService paymentService) {
      this.paymentService = paymentService;
    }

    void book(Booking booking, Payment payment) {
      if (bookings.putIfAbsent(booking.bookingId, booking) != null) {
        throw new IllegalStateException("Duplicate booking");
      }
      String existingBooking = roomReservations
          .putIfAbsent(booking.roomId, booking.bookingId);
      if (existingBooking != null) {
        bookings.remove(booking.bookingId, booking);
        throw new IllegalStateException("Room is already reserved");
      }
      booking.bookingStatus = BookingState.PENDING;

      try {
        paymentService.pay(payment, booking);
        booking.bookingStatus = BookingState.CONFIRM;
        payments.put(booking.bookingId, payment);
      } catch (RuntimeException ex) {
        roomReservations.remove(booking.roomId, booking.bookingId);
        booking.bookingStatus = BookingState.CANCEL;
        bookings.remove(booking.bookingId, booking);
        throw ex;
      }
    }

    void cancelBooking(String bookingId) {
      ReentrantLock lock = bookingLocks.computeIfAbsent(bookingId, (id) -> new ReentrantLock());
      lock.lock();
      try {
        // Validate booking
        Booking booking = bookings.get(bookingId);
        Payment payment = payments.get(bookingId);

        if (payment == null || payment.paymentStatus != PaymentState.CONFIRM) {
          throw new IllegalStateException("No confirmed payment found");
        }
        if (booking == null) {
          throw new IllegalStateException("No booking to cancel");
        }
        // Refund payment
        boolean refunded = paymentService.refundBooking(payment.paymentId);
        if (!refunded) {
          throw new IllegalStateException("Refund failed, please retry");
        }
        roomReservations.remove(booking.roomId, bookingId);
        payment.paymentStatus = PaymentState.REFUND;

        // Cancel booking
        booking.bookingStatus = BookingState.CANCEL;
      } finally {
        lock.unlock();
      }
    }
  }

}
