package LLD_Practice;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 
 * Functional Requirement
 * 1. Different vehicle type supported (CAR, TRUCK, BIKE)
 * 2. Different ParkingSlots are there
 * 3. Assign available spot at entry
 * 4. Ticket must be generated at exit
 * 5. Diferent Pricing Strategies must support (e.g. Hourly, Monthly)
 * 6. Payment must be done at exit
 * 
 * Non Functional Requirement
 * 1. Thread safety - When two vehicle enter parking area simultaneously they
 * can't park at same spot
 * 
 * Entities -
 * 1. Enum - VehicleType (CAR, TRUCK. BIKE)
 * 2. ParkingSlot - id, slotNo, isOccupied, VehicleType
 * 3. Vehicle - licenceNo, id, VehicleType
 * 4. Ticket - entryTime, exitTime, price, parkingSlot
 * 5. PaymentStrategy - UPI, CARD
 */

class ParkingLotSystem {
  static enum VehicleType {
    CAR, TRUCK, BIKE,
  }

  static class Vehicle {
    int id;
    String licenceNo;
    VehicleType vehicleType;

    Vehicle(int id, String licenceNo, VehicleType vehicleType) {
      this.id = id;
      this.licenceNo = licenceNo;
      this.vehicleType = vehicleType;
    }
  }

  static class ParkingSlot {
    int id;
    int slotNo;
    boolean isOccupied;
    VehicleType vehicleType;

    ParkingSlot(int id, int slotNo, boolean isOccupied, VehicleType vehicleType) {
      this.id = id;
      this.slotNo = slotNo;
      this.isOccupied = isOccupied;
      this.vehicleType = vehicleType;
    }

    ReentrantLock lock = new ReentrantLock();

    boolean occupy() {
      lock.lock();
      try {
        if (isOccupied) {
          return false;
        }
        isOccupied = true;
        return true;
      } finally {
        lock.unlock();
      }
    }

    boolean release() {
      lock.lock();
      try {
        if (!isOccupied) {
          return false;
        }
        isOccupied = false;
        return true;
      } finally {
        lock.unlock();
      }
    }
  }

  static class Ticket {
    String ticketId;
    LocalDateTime entryTime;
    LocalDateTime exitTime;
    double price;
    ParkingSlot parkingSlot;

    public Ticket(String ticketId, LocalDateTime entryTime, LocalDateTime exitTime, double price,
        ParkingSlot parkingSlot) {
      this.price = price;
      this.entryTime = entryTime;
      this.exitTime = exitTime;
      this.ticketId = ticketId;
      this.parkingSlot = parkingSlot;
    }

    @Override
    public String toString() {
      return "Ticket id: " + ticketId +
          "\n[ Entry Time : " + entryTime + ", Exit Time : " + exitTime + ", Price : " + price + " Slot No "
          + parkingSlot.slotNo;
    }

  }

  static interface PaymentStrategy {
    String pay(double amount);
  }

  static interface PricingStrategy {
    double calculatePrice(int duration, VehicleType vehicleType);
  }

  static class HourlyPricingStrategy implements PricingStrategy {

    @Override
    public double calculatePrice(int duration, VehicleType vehicleType) {
      return switch (vehicleType) {
        case CAR -> duration * 10.0;
        case BIKE -> duration * 5.0;
        case TRUCK -> duration * 15.0;
        default -> throw new RuntimeException("Vehicle Type not supported");
      };
    }
  }

  static class UPIPayment implements PaymentStrategy {
    @Override
    public String pay(double amount) {
      System.out.println("Paid Successfully");
      return "Paid Successfully";
    }
  }

  static class CardPayment implements PaymentStrategy {
    @Override
    public String pay(double amount) {
      System.out.println("Paid Successfully");
      return "Paid Successfully";
    }
  }

  static class ParkingLot {
    // Manages - Parking slot
    // Vehicle entry/ exit

    List<ParkingSlot> parkingSlots;
    Map<String, LocalDateTime> vehicleEntryTimeMap = new ConcurrentHashMap<>();
    Map<String, ParkingSlot> vehicleSlotMap = new ConcurrentHashMap<>();
    PaymentStrategy paymentStrategy;
    PricingStrategy pricingStrategy;

    ParkingLot(List<ParkingSlot> parkingSlots,
        PaymentStrategy paymentStrategy,
        PricingStrategy pricingStrategy) {
      this.parkingSlots = parkingSlots;
      this.paymentStrategy = paymentStrategy;
      this.pricingStrategy = pricingStrategy;
    }

    boolean parkVehicle(Vehicle vehicle) {
      ParkingSlot availableSlot = findAvailableSlot(vehicle.vehicleType);
      if (availableSlot != null && availableSlot.occupy()) {
        vehicleEntryTimeMap.put(vehicle.licenceNo, LocalDateTime.now());
        vehicleSlotMap.put(vehicle.licenceNo, availableSlot);
        return true;
      }
      return false;
    }

    boolean unparkVehicle(Vehicle vehicle) {
      ParkingSlot slot = vehicleSlotMap.get(vehicle.licenceNo);
      if (slot != null && slot.isOccupied) {
        LocalDateTime entryTime = vehicleEntryTimeMap.get(vehicle.licenceNo);
        LocalDateTime exitTime = LocalDateTime.now();
        long minutes = Duration.between(LocalDateTime.now().minusHours(3), exitTime).toMinutes();
        int hours = (int) Math.ceil(minutes / 60.0);
        double price = pricingStrategy.calculatePrice(hours, vehicle.vehicleType);

        Ticket ticket = new Ticket(UUID.randomUUID().toString(),
            entryTime, exitTime, price, slot);

        generateTicket(ticket);
        processPayment(ticket, paymentStrategy);
        vehicleSlotMap.remove(vehicle.licenceNo);
        vehicleEntryTimeMap.remove(vehicle.licenceNo);
        return true;
      }

      return slot.release();
    }

    ParkingSlot findAvailableSlot(VehicleType vehicleType) {
      // Iterate over the list of vehicles
      for (ParkingSlot slot : parkingSlots) {
        if (slot.vehicleType == vehicleType && !slot.isOccupied) {
          return slot;
        }
      }
      return null;
    }

    void generateTicket(Ticket ticket) {
      System.out.println(ticket);
    }

    void processPayment(Ticket ticket, PaymentStrategy paymentStrategy) {
      paymentStrategy.pay(ticket.price);
    }
  }

  public static void main(String[] args) {
    List<ParkingSlot> slots = new ArrayList<>();

    slots.add(new ParkingSlot(1, 101, false, VehicleType.CAR));
    slots.add(new ParkingSlot(2, 102, false, VehicleType.CAR));
    slots.add(new ParkingSlot(3, 201, false, VehicleType.BIKE));
    slots.add(new ParkingSlot(4, 301, false, VehicleType.TRUCK));

    PaymentStrategy paymentStrategy = new UPIPayment();
    PricingStrategy pricingStrategy = new HourlyPricingStrategy();

    ParkingLot parkingLot = new ParkingLot(
        slots,
        paymentStrategy,
        pricingStrategy);

    Vehicle car = new Vehicle(1, "HR26AB1234", VehicleType.CAR);

    boolean parked = parkingLot.parkVehicle(car);
    System.out.println("Vehicle parked: " + parked);

    // Vehicle exit: generate ticket and process payment
    boolean unparked = parkingLot.unparkVehicle(car);
    System.out.println("Vehicle exited: " + unparked);
  }

}