package InterviewProblems;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Functional Requirements
 * 
 * - Support multiple vehicle types (Bike, Car, Truck), each with a distinct
 * hourly parking rate.
 * - Assign an available spot to a vehicle on entry; each spot holds exactly one
 * vehicle at a time.
 * - A vehicle parks only in a spot matching its own type — no cross-type
 * substitution.
 * - Issue a ticket on entry recording the vehicle, assigned spot, and entry
 * timestamp.
 * - On exit, calculate the fee from actual elapsed time between entry and exit.
 * - Support multiple, swappable fee models (hourly rate vs. flat daily rate)
 * without changing exit/billing logic.
 * - Support multiple payment methods (cash, credit card).
 * - A vehicle may not leave, and its spot may not be freed, until payment
 * succeeds.
 * 
 * Non-Functional Requirements
 * 
 * Concurrency safety — two vehicles must never be assigned the same spot, even
 * on simultaneous entries.
 */

enum VehicleType {
  BIKE, CAR, TRUCK
}

class Vehicle {
  private final String licencePlate;
  private final VehicleType vehicleType;

  public Vehicle(String licencePlate, VehicleType vehicleType) {
    this.licencePlate = licencePlate;
    this.vehicleType = vehicleType;
  }

  public String getLicencePlate() {
    return licencePlate;
  }

  public VehicleType getVehicleType() {
    return vehicleType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (!(o instanceof Vehicle))
      return false;
    return licencePlate.equals(((Vehicle) o).licencePlate);
  }

  @Override
  public int hashCode() {
    return licencePlate.hashCode();
  }
}

class ParkingSpot {
  private final int spotNumber;
  private final VehicleType slotType;
  private final ReentrantLock lock = new ReentrantLock();
  private boolean occupied;
  private Vehicle vehicle;

  public ParkingSpot(int spotNumber, VehicleType slotType) {
    this.spotNumber = spotNumber;
    this.slotType = slotType;
  }

  public int getSpotNumber() {
    return spotNumber;
  }

  public VehicleType getSlotType() {
    return slotType;
  }

  public boolean isOccupied() {
    return occupied;
  }

  public Vehicle getVehicle() {
    return vehicle;
  }

  /** Returns false if someone else grabbed the spot first. */
  public boolean tryPark(Vehicle vehicle) {
    lock.lock();
    try {
      if (occupied)
        return false;
      this.vehicle = vehicle;
      this.occupied = true;
      return true;
    } finally {
      lock.unlock();
    }
  }

  public void vacate() {
    lock.lock();
    try {
      this.vehicle = null;
      this.occupied = false;
    } finally {
      lock.unlock();
    }
  }
}

interface ParkingFeeStrategy {
  double calculateFee(Vehicle vehicle, Duration parkedDuration);
}

/** Standard pay-by-the-hour billing, rounded up to the next hour. */
class HourlyFeeStrategy implements ParkingFeeStrategy {
  private static final Map<VehicleType, Double> RATE_PER_HOUR = Map.of(
      VehicleType.BIKE, 5.0,
      VehicleType.CAR, 10.0,
      VehicleType.TRUCK, 20.0);

  @Override
  public double calculateFee(Vehicle vehicle, Duration parkedDuration) {
    long hours = Math.max(1, (long) Math.ceil(parkedDuration.toMinutes() / 60.0));
    return hours * RATE_PER_HOUR.get(vehicle.getVehicleType());
  }
}

/** Flat daily rate regardless of exact hours - e.g. for a "day pass" spot. */
class FlatDailyFeeStrategy implements ParkingFeeStrategy {
  private static final double FLAT_RATE = 100.0;

  @Override
  public double calculateFee(Vehicle vehicle, Duration parkedDuration) {
    return FLAT_RATE;
  }
}

interface PaymentStrategy {
  boolean processPayment(double amount);
}

class CashPayment implements PaymentStrategy {
  @Override
  public boolean processPayment(double amount) {
    System.out.println("Collected cash payment of " + amount);
    return true;
  }
}

class CreditCardPayment implements PaymentStrategy {
  @Override
  public boolean processPayment(double amount) {
    System.out.println("Charged credit card " + amount);
    return true;
  }
}

class Ticket {
  private final Vehicle vehicle;
  private final ParkingSpot spot;
  private final LocalDateTime entryTime;
  private final ParkingFeeStrategy feeStrategy;
  private LocalDateTime exitTime;
  private boolean paid;

  public Ticket(Vehicle vehicle, ParkingSpot spot, LocalDateTime entryTime,
      ParkingFeeStrategy feeStrategy) {
    this.vehicle = vehicle;
    this.spot = spot;
    this.entryTime = entryTime;
    this.feeStrategy = feeStrategy;
  }

  public double calculateFee(LocalDateTime now) {
    return feeStrategy.calculateFee(vehicle, Duration.between(entryTime, now));
  }

  /** Returns true only if payment succeeded; exit should be refused otherwise. */
  public boolean pay(PaymentStrategy paymentStrategy, LocalDateTime now) {
    double fee = calculateFee(now);
    boolean success = paymentStrategy.processPayment(fee);
    if (success) {
      this.exitTime = now;
      this.paid = true;
    }
    return success;
  }

  public boolean isPaid() {
    return paid;
  }

  public Vehicle getVehicle() {
    return vehicle;
  }

  public ParkingSpot getSpot() {
    return spot;
  }

  public LocalDateTime getExitTime() {
    return exitTime;
  }
}

class ParkingLot {
  private final List<ParkingSpot> spots;
  private final ReentrantLock allocationLock = new ReentrantLock();

  public ParkingLot(List<ParkingSpot> spots) {
    this.spots = spots;
  }

  public ParkingSpot allocateSpot(Vehicle vehicle) {
    allocationLock.lock();
    try {
      for (ParkingSpot spot : spots) {
        if (spot.getSlotType() == vehicle.getVehicleType() && spot.tryPark(vehicle)) {
          return spot;
        }
      }
      return null;
    } finally {
      allocationLock.unlock();
    }
  }

  public boolean exit(Ticket ticket, PaymentStrategy paymentStrategy) {
    LocalDateTime now = LocalDateTime.now();
    if (!ticket.pay(paymentStrategy, now)) {
      System.out.println("Payment failed - vehicle cannot leave yet.");
      return false;
    }
    ticket.getSpot().vacate();
    return true;
  }
}

public class ParkingLotSystem {
  public static void main(String[] args) {
    List<ParkingSpot> spots = new ArrayList<>();
    spots.add(new ParkingSpot(1, VehicleType.CAR));
    spots.add(new ParkingSpot(2, VehicleType.CAR));
    spots.add(new ParkingSpot(3, VehicleType.BIKE));
    spots.add(new ParkingSpot(4, VehicleType.TRUCK));

    ParkingLot parkingLot = new ParkingLot(spots);

    ParkingFeeStrategy hourlyFee = new HourlyFeeStrategy();

    Vehicle car = new Vehicle("CAR123", VehicleType.CAR);
    Vehicle bike = new Vehicle("BIKE456", VehicleType.BIKE);

    ParkingSpot carSpot = parkingLot.allocateSpot(car);
    ParkingSpot bikeSpot = parkingLot.allocateSpot(bike);

    if (carSpot == null || bikeSpot == null) {
      System.out.println("Lot full.");
      return;
    }
    System.out.println(car.getLicencePlate() + " parked at spot " + carSpot.getSpotNumber());
    System.out.println(bike.getLicencePlate() + " parked at spot " + bikeSpot.getSpotNumber());

    // Simulate a 2-hour stay by backdating entry time instead of sleeping the
    // thread.
    LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2);
    Ticket carTicket = new Ticket(car, carSpot, twoHoursAgo, hourlyFee);

    boolean left = parkingLot.exit(carTicket, new CreditCardPayment());
    System.out.println(car.getLicencePlate() + " allowed to leave: " + left);
  }
}