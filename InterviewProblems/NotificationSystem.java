package InterviewProblems;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/*
 * ============================================================
 * Notification System - SDE2 LLD
 *
 * Design Patterns:
 * 1. Strategy   -> NotificationChannel
 * 2. Factory    -> NotificationChannelFactory
 * 3. Decorator  -> Retry, RateLimit, Logging
 *
 * Requirements:
 * - Email / SMS / Push / WhatsApp
 * - User preferences
 * - Async notification processing
 * - Retry transient failures
 * - Per recipient + per channel rate limiting
 * - Thread safe
 * - Easy to add new channels / behaviors
 * ============================================================
 */

enum ChannelType {
  EMAIL, SMS
}

enum NotificationStatus {
  SUCCESS, FAILED, RATE_LIMITED, DISABLED
}

class Notification {
  String id;
  String recipientId;
  ChannelType channelType;
  String message;

  public Notification(String id, String recipientId, ChannelType channelType, String message) {
    this.id = id;
    this.recipientId = recipientId;
    this.channelType = channelType;
    this.message = message;
  }

  @Override
  public String toString() {
    return "Notification{" +
        "id='" + id + '\'' +
        ", recipientId='" + recipientId + '\'' +
        ", channelType=" + channelType +
        ", message='" + message + '\'' +
        '}';
  }

}

interface NotificationChannel {
  NotificationStatus send(Notification notification);

  ChannelType getChannelType();
}

class EmailChannel implements NotificationChannel {
  @Override
  public NotificationStatus send(Notification notification) {
    System.out.println(
        Thread.currentThread().getName() + " -> Email -> " + notification.recipientId + " : " + notification.message);

    return NotificationStatus.SUCCESS;
  }

  @Override
  public ChannelType getChannelType() {
    return ChannelType.EMAIL;
  }
}

class SMSChannel implements NotificationChannel {

  private final AtomicInteger attempts = new AtomicInteger(0);

  @Override
  public NotificationStatus send(Notification notification) {
    int attempt = attempts.incrementAndGet();
    if (attempt <= 2) {
      System.out.println(
          Thread.currentThread().getName()
              + " -> SMS FAILED "
              + "(attempt "
              + attempt
              + ")");

      throw new RuntimeException(
          "Temporary SMS provider failure");
    }

    System.out.println(
        Thread.currentThread().getName()
            + " -> SMS SUCCESS -> "
            + notification.recipientId
            + " : "
            + notification.message);

    return NotificationStatus.SUCCESS;
  }

  @Override
  public ChannelType getChannelType() {
    return ChannelType.SMS;
  }

}

class NotificationChannelFactory {

  public NotificationChannel getChannel(ChannelType type) {
    return switch (type) {
      case EMAIL -> new EmailChannel();
      case SMS -> new SMSChannel();
    };
  }
}

class PreferenceService {
  private final Map<String, Set<ChannelType>> preferences = new ConcurrentHashMap<>();

  public void setPreferences(String userId, Set<ChannelType> channels) {
    preferences.put(userId, ConcurrentHashMap.newKeySet());
    preferences.get(userId).addAll(channels);
  }

  public void enableChannel(String userId, ChannelType channel) {
    preferences.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(channel);
  }

  public void disableChannel(String userId, ChannelType channel) {
    Set<ChannelType> channels = preferences.get(userId);
    if (channels != null)
      channels.remove(channel);
  }

  public boolean isAllowed(String userId, ChannelType channel) {
    Set<ChannelType> channels = preferences.get(userId);
    return channels != null && channels.contains(channel);
  }
}

interface RateLimiter {
  boolean allow(String recipientId, ChannelType channelType);
}

class SlidingWindowRateLimiter implements RateLimiter {

  private final Deque<Long> timestamps = new ArrayDeque<>();

  private final int maxRequests;
  private final long windowMillis;
  private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();
  private final ReentrantLock lock = new ReentrantLock();

  public SlidingWindowRateLimiter(
      int maxRequests,
      int windowMillis) {
    this.maxRequests = maxRequests;
    this.windowMillis = windowMillis;
  }

  @Override
  public boolean allow(String recipientId, ChannelType channelType) {
    String key = recipientId + "-" + channelType;
    windows.computeIfAbsent(key, k -> new ArrayDeque<>());

    lock.lock();
    try {
      long now = System.currentTimeMillis();
      while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowMillis) {
        timestamps.pollFirst();
      }
      if (timestamps.size() >= maxRequests) {
        return false;
      }

      timestamps.addLast(now);
      return true;
    }
    finally {
      lock.unlock();
    }
  }
}

abstract class NotificationChannelDecorator implements NotificationChannel {

  protected final NotificationChannel wrappedChannel;

  protected NotificationChannelDecorator(NotificationChannel wrappedChannel) {
    this.wrappedChannel = wrappedChannel;
  }

  @Override
  public ChannelType getChannelType() {
    return wrappedChannel.getChannelType();
  }
}

class RateLimitDecorator extends NotificationChannelDecorator {

  private final RateLimiter rateLimiter;

  RateLimitDecorator(NotificationChannel wrappedChannel, RateLimiter rateLimiter) {
    super(wrappedChannel);
    this.rateLimiter = rateLimiter;
  }


  @Override
  public NotificationStatus send(Notification notification) {
    boolean allowed = rateLimiter.allow(notification.recipientId, notification.channelType);
    if (!allowed) {
      System.out.println("RATE LIMITED -> " + notification.recipientId + " : " + notification.channelType);
      return NotificationStatus.RATE_LIMITED;
    }
    return wrappedChannel.send(notification);
  }
}

class RetryDecorator extends NotificationChannelDecorator {

  private final int maxRetries;
  private final long initialBackoffMillis;

  protected RetryDecorator(NotificationChannel wrappedChannel,
                           int maxRetries,
                           long initialBackoffMillis) {
    super(wrappedChannel);
    this.maxRetries = maxRetries;
    this.initialBackoffMillis = initialBackoffMillis;
  }

  @Override
  public NotificationStatus send(Notification notification) {
    int attempt = 0;
    while (attempt <= maxRetries) {
      try {
        return wrappedChannel.send(notification);
      } catch(RuntimeException e) {
        attempt++;
        System.out.println("Retry #" + attempt + " -> " + notification.id);
        if (attempt > maxRetries) {
          System.out.println("MAX RETRIES EXCEEDED " + maxRetries);
          return NotificationStatus.FAILED;
        }

        long backoff = initialBackoffMillis * (1L << (attempt - 1));
        try {
          Thread.sleep(backoff);
        } catch (InterruptedException e1) {
          Thread.currentThread().interrupt();
          return NotificationStatus.FAILED;
        }
      }
    }
    return NotificationStatus.FAILED;
  }
}

class NotificationService {

  private final NotificationChannelFactory factory;
  private final PreferenceService preferenceService;
  private final RateLimiter rateLimiter;
  private final ExecutorService executor = Executors.newFixedThreadPool(3);

  public NotificationService(
          PreferenceService preferenceService,
          RateLimiter rateLimiter,
          NotificationChannelFactory factory
  ) {
    this.factory = factory;
    this.preferenceService = preferenceService;
    this.rateLimiter = rateLimiter;
  }

  private void process(Notification notification) {
    if (!preferenceService.isAllowed(notification.recipientId, notification.channelType)) {
      System.out.println("Channel Disabled for user");
      return;
    }
    NotificationChannel channel = factory.getChannel(notification.channelType);
    channel = new RateLimitDecorator(channel, rateLimiter);
    channel = new RetryDecorator(channel, 3, 500);
    channel.send(notification);
  }

  public void send(Notification notification) {
    executor.submit(() -> process(notification));
  }

  public void shutDown() {
    executor.shutdown();
  }

}

public class NotificationSystem {
  public static void main(String[] args) throws InterruptedException{
    PreferenceService preferenceService = new PreferenceService();
    preferenceService.setPreferences("user1", Set.of(ChannelType.EMAIL, ChannelType.SMS));

    NotificationChannelFactory factory = new NotificationChannelFactory();
    RateLimiter rateLimiter = new SlidingWindowRateLimiter(5, 1000);
    NotificationService notificationService = new NotificationService(preferenceService, rateLimiter, factory);
    notificationService.send(new Notification("123", "user2", ChannelType.EMAIL, "Welcome!"));
    notificationService.send(new Notification("123", "user1", ChannelType.SMS, "Welcome!"));

    Thread.sleep(1000);
    notificationService.shutDown();
  }
}
