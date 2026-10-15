package LLD_Practice;

/*
Requirements
1. Insert card
2. Authenticate PIN
3. Check balance
4. Withdraw cash
5. Eject card
6. Handle Invalid Operation
--------------------------
7. Atm is an state machine, state can be managed using state design pattern
8. There can be has card state, or no card state
*/
public class DesignAtmMachine {

  static interface AtmState {
    void insertCard();

    void authenticatePin(String pin);

    double checkBalance();

    boolean withdrawCash(double amount);

    void ejectCard();
  }

  static class NoCardState implements AtmState {

    private final ATM atm;

    NoCardState(ATM atm) {
      this.atm = atm;
    }

    @Override
    public void insertCard() {
      System.out.println("Card inserted");
      atm.setState(atm.getCardInsertedState());
    }

    @Override
    public void authenticatePin(String pin) {
      throw new IllegalStateException("Insert card to authenticate using pin...");
    }

    @Override
    public double checkBalance() {
      throw new IllegalStateException("Insert card to check balance");
    }

    @Override
    public boolean withdrawCash(double amount) {
      throw new IllegalStateException("Insert card to withdraw cash");
    }

    @Override
    public void ejectCard() {
      throw new IllegalStateException("No card inserted");
    }

  }

  static class CardInsertedState implements AtmState {
    private final ATM atm;

    CardInsertedState(ATM atm) {
      this.atm = atm;
    }

    @Override
    public void insertCard() {
      throw new IllegalStateException("Card already inserted");
    }

    @Override
    public void authenticatePin(String pin) {
      User user = atm.getUser();
      if (user != null && pin != null && user.getPin().equals(pin)) {
        System.out.println("Pin authenticated successfully");
        atm.setState(atm.getAuthenticatedState());
      } else {
        System.out.println("Invalid PIN. Please retry...");
      }
    }

    @Override
    public double checkBalance() {
      throw new IllegalStateException("PIN not authenticated");
    }

    @Override
    public boolean withdrawCash(double amount) {
      throw new IllegalStateException("PIN not authenticated");
    }

    @Override
    public void ejectCard() {
      System.out.println("Card ejected successfully...");
      atm.setState(atm.getNoCardState());
    }

  }

  static class AuthenticatedState implements AtmState {
    private final ATM atm;

    AuthenticatedState(ATM atm) {
      this.atm = atm;
    }

    @Override
    public void insertCard() {
      throw new IllegalStateException("Card already inserted");
    }

    @Override
    public void authenticatePin(String pin) {
      throw new IllegalStateException("Pin already verified");
    }

    @Override
    public double checkBalance() {
      return atm.getUser().getAmount();
    }

    @Override
    public boolean withdrawCash(double amount) {
      User user = atm.getUser();
      if (amount <= 0 || user.getAmount() < amount) {
        throw new IllegalStateException("Enter valid amount");
      }
      user.setAmount(user.getAmount() - amount);
      return true;
    }

    @Override
    public void ejectCard() {
      System.out.println("Card ejected successfully...");
      atm.setState(atm.getNoCardState());
    }

  }

  static class ATM {
    private final AtmState noCardState;
    private final AtmState cardInsertedState;
    private final AtmState authenticatedState;
    private final User user;
    private AtmState currentState;

    public ATM() {
      this.noCardState = new NoCardState(this);
      this.cardInsertedState = new CardInsertedState(this);
      this.authenticatedState = new AuthenticatedState(this);
      this.user = new User(500.00, "U101", "1234");
      this.currentState = noCardState;
    }

    public void setState(AtmState state) {
      this.currentState = state;
    }

    public AtmState getNoCardState() {
      return noCardState;
    }

    public AtmState getCardInsertedState() {
      return cardInsertedState;
    }

    public AtmState getAuthenticatedState() {
      return authenticatedState;
    }

    public void insertCard() {
      currentState.insertCard();
    }

    public void authenticatePin(String pin) {
      currentState.authenticatePin(pin);
    }

    public double checkBalance() {
      return currentState.checkBalance();
    }

    public boolean withdrawCash(double amount) {
      return currentState.withdrawCash(amount);
    }

    public void ejectCard() {
      currentState.ejectCard();
    }

    public User getUser() {
      return this.user;
    }

  }

  static class User {
    double amount;
    String userId;
    String pin;

    public User(double amount, String userId, String pin) {
      this.amount = amount;
      this.userId = userId;
      this.pin = pin;
    }

    public String getPin() {
      return this.pin;
    }

    private double getAmount() {
      return this.amount;
    }

    public void setAmount(double amount) {
      this.amount = amount;
    }
  }

  public static void main(String[] args) {
    ATM atm = new ATM();
    try {
      atm.insertCard();
      atm.authenticatePin("1234");
      System.out.println(atm.checkBalance());
      atm.withdrawCash(299);
      System.out.println(atm.checkBalance());
      atm.ejectCard();
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
  }
}
