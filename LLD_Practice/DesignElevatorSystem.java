package LLD_Practice;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Functional Requirements -
 * 1. There are n no of elevators
 * 2. Floor selection from inside
 * 3. Elevator start moving if after last button press, no activity for 30sec
 * 4. Outside each floor there is arrow Up and down button
 * 5. Once pressed arrow button from outside elevator with min absolute floor
 * difference will start moving towards floor
 * 
 * Non functional requirement -
 * Thread Safety - Each elevator is moving in its own loop
 * 
 * Entities
 * 1. Enum - Direction (UP/DOWN)
 * 2. Elevator - id, currentFloor, direction, requestedFloor
 * 3. ElevatorManager - orchestrator
 * 4. ElevatorState - IDLE, MOVING
 * 
 */
public class DesignElevatorSystem {
  static enum Direction {
    UP, DOWN
  }

  static enum ElevatorState {
    IDLE, MOVING
  }

  static class Elevator {
    int id;
    int currentFloor;
    Direction direction;
    ElevatorState state;
    int requestedFloor;

    private final ReentrantLock lock = new ReentrantLock();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pendingTask;

    Elevator(int id, int currentFloor, int requestedFloor, Direction direction, ElevatorState elevatorState) {
      this.id = id;
      this.currentFloor = currentFloor;
      this.requestedFloor = requestedFloor;
      this.direction = direction;
      this.state = elevatorState;
    }

    void scheduleMovement() {
      lock.lock();
      try {
        if (pendingTask != null) {
          pendingTask.cancel(false);
        }
        pendingTask = scheduler.schedule(
            this::moveToRequestedFloor, 30, TimeUnit.SECONDS);
      } finally {
        lock.unlock();
      }
    }

    void assignRequest(int requestedFloor, Direction direction) {
      lock.lock();
      try {
        this.requestedFloor = requestedFloor;
        if (requestedFloor > this.currentFloor) {
          this.direction = Direction.UP;
        } else if (requestedFloor < this.currentFloor) {
          this.direction = Direction.DOWN;
        }
      } finally {
        lock.unlock();
      }
      this.scheduleMovement();
    }

    void moveToRequestedFloor() {
      lock.lock();
      try {
        if (state == ElevatorState.MOVING) {
          return;
        }
        state = ElevatorState.MOVING;

      } finally {
        lock.unlock();
      }

      while (true) {
        lock.lock();
        try {

          if (currentFloor == requestedFloor) {
            state = ElevatorState.IDLE;
            System.out.println("Elevator reached floor " + currentFloor);
            return;
          }
          if (currentFloor < requestedFloor) {
            direction = Direction.UP;
            currentFloor++;
          } else {
            direction = Direction.DOWN;
            currentFloor--;
          }

          System.out.println("Elevator " + id + " is at floor " + currentFloor);
        } finally {
          lock.unlock();
        }

        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
  }

  static class ElevatorManager {
    List<Elevator> elevators;

    ElevatorManager(List<Elevator> elevators) {
      this.elevators = elevators;
    }

    Elevator findNearestElevator(int requstedFloor) {
      int minDistance = Integer.MAX_VALUE;
      Elevator callingElevator = null;
      for (Elevator elevator : elevators) {
        int distance = Math.abs(requstedFloor - elevator.currentFloor);
        if (minDistance > distance) {
          callingElevator = elevator;
          minDistance = distance;
        }
      }
      return callingElevator;
    }

    void requestElevator(int requestedFloor, Direction direction) {
      Elevator nearestElevator = findNearestElevator(requestedFloor);
      if (nearestElevator == null) {
        System.out.println("No elevators available");
        return;
      }
      nearestElevator.assignRequest(requestedFloor, direction);
      System.out.println("Elevator " + nearestElevator.id + " asssigned to floor " + requestedFloor);
    }

  }

  public static void main(String[] args) throws InterruptedException {

    Elevator e1 = new Elevator(
        1, 0, 0, Direction.UP, ElevatorState.IDLE);

    Elevator e2 = new Elevator(
        2, 5, 5, Direction.UP, ElevatorState.IDLE);

    Elevator e3 = new Elevator(
        3, 10, 10, Direction.DOWN, ElevatorState.IDLE);

    List<Elevator> elevators = List.of(e1, e2, e3);

    ElevatorManager manager = new ElevatorManager(elevators);

    // External request: someone on floor 3 wants to go UP
    manager.requestElevator(3, Direction.UP);

    // Another request after 5 seconds
    Thread.sleep(5000);
    manager.requestElevator(8, Direction.DOWN);

    // Keep main alive long enough to observe the 30-second delay
    Thread.sleep(40000);

    // Shut down each elevator's scheduler
    for (Elevator elevator : elevators) {
      elevator.scheduler.shutdownNow();
    }
  }
}
