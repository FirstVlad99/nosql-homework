package ratelimiter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.Map;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RateLimiter {

    private final Jedis redis;
    private final String label;
    private final long maxRequestCount;
    private final long timeWindowSeconds;
    private final String currentWindow;
    private final String currentRequestCount;
    private final String prevRequestCount;
    private final long windowSize;
    private final String lastTimestamp;

    public RateLimiter(Jedis redis, String label, long maxRequestCount, long timeWindowSeconds) {
        this.redis = redis;
        this.label = label;
        this.maxRequestCount = maxRequestCount;
        this.timeWindowSeconds = timeWindowSeconds;
        this.windowSize = timeWindowSeconds * 1000L;
        this.currentWindow = "cur_window";
        this.currentRequestCount = "cur_request_count";
        this.prevRequestCount = "prev_request_count";
        this.lastTimestamp = "last_ts";
    }

    /**
     * Вспомогательный класс для инкапсуляции текущего window_id,
     * количества запросов в прошлом и текущем окнах, метку времени последнего запроса
     */
    private static class RequestState {
        long curWindow;
        int curCount;
        int prevCount;
        long lastTs;
    }

    /**
     * Метод для обработки поступающих запросов.
     *
     * @return возвращает true, в случае если лимит запросов не превышен и запрос пропускается, в ином случае false
     */
    public boolean pass() {
        long now = Instant.now().toEpochMilli();

        // инициализирую текущее window_id,
        // количества запросов в прошлом и текущем окнах, метку времени последнего запроса
        RequestState state = init(now);

        handleWindowTransition(state, now);

        double effectiveRequestCount = calculateEffectiveCount(
                now - state.lastTs,
                state.curCount,
                state.prevCount,
                windowSize
        );

        if (effectiveRequestCount >= maxRequestCount) {
            return false;
        }

        state.curCount++;
        saveCurrentState(state, now);

        return true;
    }

    /**
     * Метод для получения метки времени последнего запроса, количество запросов из предыдущего, текущего окон
     *
     * @param now - время обрабатываемого запроса
     * @return возвращает объект класса RequestState
     */
    private RequestState init(long now) {
        RequestState state = new RequestState();

        String curWindowStr = redis.hget(label, currentWindow);
        String prevCountStr = redis.hget(label, prevRequestCount);
        String curCountStr = redis.hget(label, currentRequestCount);
        String lastTsStr = redis.hget(label, lastTimestamp);

        // ЕСЛИ: текущих записей нет?
        // ДА - инициализирую прошлое окно - 0, текущее окно - 0
        if (curWindowStr == null) {
            state.curWindow = now / windowSize;
            state.curCount = 0;
            state.prevCount = 0;
            state.lastTs = now;
            redis.hset(label, Map.of(
                    currentWindow, String.valueOf(state.curWindow),
                    currentRequestCount, "0",
                    prevRequestCount, "0",
                    lastTimestamp, String.valueOf(now)
            ));
        } else {
            // ИНАЧЕ: получаю с предыдущего окна количество запросов и с текущего
            state.curWindow = Long.parseLong(curWindowStr);
            state.curCount = Integer.parseInt(curCountStr);
            state.prevCount = Integer.parseInt(prevCountStr);
            state.lastTs = Long.parseLong(lastTsStr);
        }

        return state;
    }

    /**
     * Метод для смены предыдущего и текущего окон при необходимости
     *
     * @param state - объект класса RequestState
     * @param now   - время обрабатываемого запроса
     */
    private void handleWindowTransition(RequestState state, long now) {
        long windowId = now / windowSize;
        // ЕСЛИ: наступило новое окно?
        // ДА: перезаписываю id прошлого окна на текущее из Redis, id текущего окна - windowIdString
        if (state.curWindow != windowId) {
            if (state.curWindow == windowId - 1) {
                state.prevCount = state.curCount;
            } else {
                // если запросов в предыдущем окне не было (т.е. пустые окна были)
                state.prevCount = 0;
            }
            state.curCount = 0;
            state.curWindow = windowId;

            redis.hset(label, Map.of(
                    currentWindow, String.valueOf(state.curWindow),
                    currentRequestCount, "0",
                    prevRequestCount, String.valueOf(state.prevCount)
            ));
        }
    }

    /**
     * Метод для сохранения количества запросов в текущем окне, метки времени последнего запроса
     *
     * @param state - объект класса RequestState
     * @param now   - время обрабатываемого запроса
     *
     */
    private void saveCurrentState(RequestState state, long now) {
        redis.hset(label, Map.of(
                currentRequestCount, String.valueOf(state.curCount),
                lastTimestamp, String.valueOf(now)
        ));
        redis.expire(label, timeWindowSeconds);
    }

    /**
     * Метод для вычисления допустимого числа запросов, учитывая вклад запросов из предыдущих окон.
     *
     * @param timeDifferent - разница между временем прошлого запроса и текущего.
     * @return количество допустимых запросов
     */
    private double calculateEffectiveCount(long timeDifferent, int currentCount, int prevCount, long windowSize) {
        double alpha = (double) timeDifferent / windowSize;
        return currentCount + prevCount * (1 - alpha);
    }

    public static void main(String[] args) {
        JedisPool pool = new JedisPool("localhost", 6379);

        try (Jedis redis = pool.getResource()) {
            RateLimiter rateLimiter = new RateLimiter(redis, "pr_rate", 1, 1);

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
