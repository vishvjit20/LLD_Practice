package LLD_Practice;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/*
Requirements
1. Different type of notification channels (SMS/Email/Push)
2. User preferences
3. Rate limiting per recipient per channel
4. Easy to add new channel
5. Retry mechanism
6. Prevent duplicate notification

Design Patterns - 
1. NotificationStrategy 
2. Decorator

Entities - 
1. NotificationChannel
   - EmailNotification
   - PushNotification
   - SMSNotification
2. Notification (id, userId, channel, message)
*/

public class NotificationSystem {

  static interface NotificationChannel {
    void send(Notification notification);
  }

  static class EmailNotification implements NotificationChannel {

    @Override
    public void send(Notification notification) {
      System.out.println("Message sent through Email");
    }

  }

  static class SMSNotification implements NotificationChannel {
    @Override
    public void send(Notification notification) {
      System.out.println("Message sent through SMS");
    }
  }

  static class PushNotification implements NotificationChannel {
    @Override
    public void send(Notification notification) {
      System.out.println("Message sent through Push Notification");
    }
  }

  static enum ChannelType {
    SMS, EMAIL, PUSH
  }

  static class Notification {
    String id;
    ChannelType channel;
    String userId;
    String message;
    int retryAttempts;

    public Notification(String id, ChannelType channel, String userId, String message, int retryAttempts) {
      this.id = id;
      this.channel = channel;
      this.userId = userId;
      this.message = message;
      this.retryAttempts = retryAttempts;
    }
  }

  static class UserPreferenceService {
    Map<String, Set<ChannelType>> preferences;

    public UserPreferenceService(Map<String, Set<ChannelType>> preferences) {
      this.preferences = preferences;
    }

    void enableChannel(String userId, ChannelType channelType) {
      Set<ChannelType> channels = preferences.computeIfAbsent(userId, id -> new HashSet<>());
      channels.add(channelType);
    }

    void disableChannel(String userId, ChannelType channelType) {
      Set<ChannelType> channels = preferences.get(userId);
      if (channels == null) {
        return;
      }
      channels.remove(channelType);
    }

    boolean isChannelEnabled(String userId, ChannelType channelType) {
      Set<ChannelType> channels = preferences.get(userId);
      return channels != null && channels.contains(channelType);
    }
  }

  static class NotificationService {
    Notification notification;
    UserPreferenceService userPreferenceService;
    RateLimit rateLimit;
    Map<String, Notification> idempotentMap = new ConcurrentHashMap<>();

    NotificationService(Notification notification, UserPreferenceService userPreferenceService, RateLimit rateLimit) {
      this.notification = notification;
      this.rateLimit = rateLimit;
      this.userPreferenceService = userPreferenceService;
    }

    void sendNotification() {
      String key = notification.id;
      Notification existing = idempotentMap.putIfAbsent(key, notification);

      if (existing != null) {
        System.out.println("Duplicate notification");
        return;
      }
      if (!userPreferenceService.isChannelEnabled(notification.userId, notification.channel)) {
        System.out.println("Channel disabled by user");
        return;
      }
      if (!rateLimit.allowRequest(notification.userId + "#" + notification.channel, notification.channel)) {
        System.out.println("Rate limit exceeded");
        return;
      }
      NotificationChannel selectedNotification = switch (notification.channel) {
        case SMS -> new SMSNotification();
        case PUSH -> new PushNotification();
        case EMAIL -> new EmailNotification();
        default -> throw new IllegalStateException("Please select the valid nitification channel");
      };

      int retryLeft = notification.retryAttempts;

      while (true) {
        try {
          selectedNotification.send(notification);
          return;
        } catch (Exception e) {
          if (retryLeft == 0) {
            System.out.println("Notification delivery failed");
            return;
          }
          retryLeft--;
          System.out.println("Retrying notification...");
        }
      }
    }
  }

  static interface RateLimit {
    boolean allowRequest(String userId, ChannelType channelType);
  }

  static class SlidingWindowRateLimit implements RateLimit {
    private final int maxRequests;
    private final long windowMillis;

    private final Map<String, ArrayDeque<Long>> map = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> reentrantLocks = new ConcurrentHashMap<>();

    SlidingWindowRateLimit(int maxRequests, long windowMillis) {
      this.maxRequests = maxRequests;
      this.windowMillis = windowMillis;
    }

    @Override
    public boolean allowRequest(String rateLimitKey, ChannelType channelType) {
      Long timeInMillisec = System.currentTimeMillis();
      reentrantLocks.computeIfAbsent(rateLimitKey, (id) -> new ReentrantLock());
      ReentrantLock lock = reentrantLocks.get(rateLimitKey);
      lock.lock();
      try {
        ArrayDeque<Long> que = map.computeIfAbsent(rateLimitKey, id -> new ArrayDeque<>());
        while (!que.isEmpty() && timeInMillisec - que.peek() >= windowMillis) {
          que.poll();
        }

        if (que.size() >= maxRequests) {
          return false;
        }

        que.addLast(timeInMillisec);
        return true;
      } finally {
        lock.unlock();
      }
    }

  }

}
