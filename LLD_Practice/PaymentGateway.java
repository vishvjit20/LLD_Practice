package LLD_Practice;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/*
Functional Requirements
1. Accept payment request for a given amount
2. Support multiple payment provider
3. Check payment status
4. Refund a successful payment
5. Handle provider failure and duplicate requests

Non functional
1. Thread safety
2. Idempotency

Design Patterns
1. Adapter
2. PaymentStrategy 

Entities
1. Payment - id, amount, userId, PaymentStatus
2. PaymentStatus(Enum -> INITIATED, FAILED, SUCCESSFULL, PENDING, REFUNDED)
3. PaymentProviderType (Enum -> Stripe, RazorPay)

*/
public class PaymentGateway {
  static enum PaymentStatus {
    INITIATED, PENDING, FAILED, SUCCESSFULL, REFUNDED
  }

  static enum PaymentProviderType {
    STRIPE,
    RSZORPAY
  }

  static interface PaymentProvider {
    PaymentStatus pay(Payment payment);

    boolean refund(String paymentId);

    PaymentStatus checkStatus(String paymentId);
  }

  static class Payment {
    String id;
    BigDecimal amount;
    String userId;
    PaymentStatus paymentStatus;
  }

  static class StripeSdk {
    String createCharge(long amount) {
      // Calls Stripe's actual API
      return "stripe-txn-101";
    }

    String fetchChargeStatus(String transactionId) {
      return "succeeded";
    }

    boolean issueRefund(String transactionId) {
      return true;
    }
  }

  static class StripePaymentAdapter implements PaymentProvider {

    private final StripeSdk stripeSdk;

    StripePaymentAdapter(StripeSdk stripeSdk) {
      this.stripeSdk = stripeSdk;
    }

    @Override
    public PaymentStatus pay(Payment payment) {
      long amount = payment.amount.multiply(BigDecimal.valueOf(100)).longValue();
      stripeSdk.createCharge(amount);
      return PaymentStatus.SUCCESSFULL;

    }

    @Override
    public boolean refund(String paymentId) {
      return stripeSdk.issueRefund(paymentId);
    }

    @Override
    public PaymentStatus checkStatus(String paymentId) {
      String status = stripeSdk.fetchChargeStatus(paymentId);

      return switch (status) {
        case "succeeded" -> PaymentStatus.SUCCESSFULL;
        case "pending" -> PaymentStatus.PENDING;
        default -> PaymentStatus.FAILED;
      };
    }

  }

  static class RazorpayPaymentAdapter implements PaymentProvider {
    @Override
    public PaymentStatus pay(Payment payment) {
      return PaymentStatus.SUCCESSFULL;
    }

    @Override
    public boolean refund(String paymentId) {
      return true;
    }

    @Override
    public PaymentStatus checkStatus(String paymentId) {

      return PaymentStatus.SUCCESSFULL;
    }
  }

  static class PaymentService {
    Map<PaymentProviderType, PaymentProvider> providers = new ConcurrentHashMap<>();
    Map<String, Payment> payments = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> refundLocks = new ConcurrentHashMap<>();

    PaymentService() {
      providers.put(PaymentProviderType.STRIPE, new StripePaymentAdapter(new StripeSdk()));
      providers.put(PaymentProviderType.RSZORPAY, new RazorpayPaymentAdapter());
    }

    public PaymentStatus makePayment(Payment payment, PaymentProviderType providerType) {
      Payment existing = payments.putIfAbsent(payment.id, payment);

      if (existing != null) {
        throw new IllegalArgumentException("Payment already exists: " + payment.id);
      }

      PaymentProvider provider = providers.get(providerType);
      payment.paymentStatus = PaymentStatus.INITIATED;
      try {
        PaymentStatus status = provider.pay(payment);
        payment.paymentStatus = status;
        return status;
      } catch (Exception e) {
        payment.paymentStatus = PaymentStatus.FAILED;
        return PaymentStatus.FAILED;
      }
    }

    public PaymentStatus checkPaymentStatus(String paymentId, PaymentProviderType providerType) {
      Payment payment = payments.get(paymentId);
      PaymentProvider provider = providers.get(providerType);

      PaymentStatus status = provider.checkStatus(paymentId);
      payment.paymentStatus = status;
      return status;
    }

    public boolean refund(String paymentId, PaymentProviderType providerType) {
      Payment payment = payments.get(paymentId);

      ReentrantLock lock = refundLocks.computeIfAbsent(paymentId, id -> new ReentrantLock());
      lock.lock();
      try {
        if (payment.paymentStatus != PaymentStatus.SUCCESSFULL) {
          throw new IllegalStateException("Only successful payments can be refunded");
        }
        PaymentProvider provider = providers.get(providerType);
        boolean refunded = provider.refund(paymentId);
        if (refunded) {
          payment.paymentStatus = PaymentStatus.REFUNDED;
        }
        return refunded;
      } finally {
        lock.unlock();
      }
    }
  }

  public static void main(String[] args) {

    PaymentService paymentService = new PaymentService();

    // 1. Create a payment
    Payment payment = new Payment();
    payment.id = "P101";
    payment.userId = "U101";
    payment.amount = new BigDecimal("500.00");

    // 2. Make payment using Stripe
    PaymentStatus status = paymentService.makePayment(payment, PaymentProviderType.STRIPE);

    System.out.println("Payment Status: " + status);

    // 3. Check payment status
    PaymentStatus currentStatus = paymentService.checkPaymentStatus("P101", PaymentProviderType.STRIPE);

    System.out.println("Current Payment Status: " + currentStatus);

    // 4. Refund the payment
    boolean refunded = paymentService.refund("P101", PaymentProviderType.STRIPE);

    System.out.println("Refund Successful: " + refunded);
    System.out.println("Final Payment Status: " + payment.paymentStatus);

    // 5. Try creating a duplicate payment
    try {
      Payment duplicatePayment = new Payment();
      duplicatePayment.id = "P101";
      duplicatePayment.userId = "U101";
      duplicatePayment.amount = new BigDecimal("500.00");

      paymentService.makePayment(
          duplicatePayment,
          PaymentProviderType.STRIPE);
    } catch (IllegalArgumentException e) {
      System.out.println("Duplicate Request: " + e.getMessage());
    }
  }

}
