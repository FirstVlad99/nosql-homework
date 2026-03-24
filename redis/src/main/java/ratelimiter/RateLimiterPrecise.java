package ratelimiter;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

public class RateLimiterPrecise {
  private final Jedis redis;
  private final String label;
  private final long maxRequestCount;
  private final long windowSizeMs;

  public RateLimiterPrecise(Jedis redis, String label, long maxRequestCount, long timeWindowSeconds) {
    this.redis = redis;
    this.label = label;
    this.maxRequestCount = maxRequestCount;
    this.windowSizeMs = timeWindowSeconds * 1000L;
  }

  public boolean pass() {
    String script =
        "local key = KEYS[1]\n" +
            "local timeArray = redis.call('TIME')\n" +
            "local nowMs = tonumber(timeArray[1]) * 1000 + math.floor(tonumber(timeArray[2]) / 1000)\n" +
            "local windowSizeMs = tonumber(ARGV[1])\n" +
            "local maxRequestCount = tonumber(ARGV[2])\n" +

            // удаляю старые запросы, метка времени которых от 0 до начала текущего окна (now - размер окна)
            "redis.call('ZREMRANGEBYSCORE', key, 0, nowMs - windowSizeMs)\n" +

            // считаю текущие
            "local count = redis.call('ZCARD', key)\n" +

            "if count < maxRequestCount then\n" +
            // tostring(math.random()) , иначе перезапишется запрос, если несколько пришли в одно и то же время
            "  redis.call('ZADD', key, nowMs, tostring(math.random()))\n" +
            "  redis.call('EXPIRE', key, math.floor(windowSizeMs / 1000))\n" +
            "  return true\n" +
            "else\n" +
            "  return false\n" +
            "end";

    Object result = redis.eval(
        script,
        Collections.singletonList(label),
        Arrays.asList(
            String.valueOf(windowSizeMs),
            String.valueOf(maxRequestCount)
        )
    );

    return Long.valueOf(1).equals(result);
  }

  public static void main(String[] args) {
    JedisPool pool = new JedisPool("localhost", 6379);

    try (Jedis redis = pool.getResource()) {
      RateLimiterPrecise rateLimiter = new RateLimiterPrecise(redis, "pr_rate", 1, 1);

      BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
      long prev = Instant.now().toEpochMilli();
      long now;

      while (true) {
        try {
          String s = br.readLine();
          if (s == null || s.equals("q")) {
            return;
          }
          boolean passed = rateLimiter.pass();

          now = Instant.now().toEpochMilli();
          if (passed) {
            System.out.printf("%d ms: %s", now - prev, "passed");
            prev = now;
          } else {
            System.out.printf("%d ms: %s", now - prev, "limited");
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }

    }
  }
}
