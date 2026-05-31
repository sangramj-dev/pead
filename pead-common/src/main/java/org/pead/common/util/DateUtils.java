package org.pead.common.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Date/time utilities for US market hours and trading calendar.
 */
public final class DateUtils {

    public static final ZoneId EASTERN = ZoneId.of("America/New_York");

    private DateUtils() {}

    /**
     * Returns true if the given date is a weekday (not Saturday/Sunday).
     * Does not account for market holidays.
     */
    public static boolean isTradingDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    /**
     * Returns the next trading day (skips weekends, not holidays).
     */
    public static LocalDate nextTradingDay(LocalDate from) {
        LocalDate next = from.plusDays(1);
        while (!isTradingDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    /**
     * Returns N trading days after the given date.
     */
    public static LocalDate addTradingDays(LocalDate from, int days) {
        LocalDate result = from;
        int added = 0;
        while (added < days) {
            result = result.plusDays(1);
            if (isTradingDay(result)) {
                added++;
            }
        }
        return result;
    }

    /**
     * Returns current Eastern time.
     */
    public static ZonedDateTime nowEastern() {
        return ZonedDateTime.now(EASTERN);
    }

    /**
     * Returns true if currently in pre-market hours (4:00 AM - 9:30 AM ET).
     */
    public static boolean isPreMarket() {
        ZonedDateTime now = nowEastern();
        int hour = now.getHour();
        int minute = now.getMinute();
        return (hour >= 4 && hour < 9) || (hour == 9 && minute < 30);
    }

    /**
     * Returns true if currently in regular market hours (9:30 AM - 4:00 PM ET).
     */
    public static boolean isMarketHours() {
        ZonedDateTime now = nowEastern();
        int hour = now.getHour();
        int minute = now.getMinute();
        boolean afterOpen = (hour > 9) || (hour == 9 && minute >= 30);
        boolean beforeClose = hour < 16;
        return afterOpen && beforeClose && isTradingDay(now.toLocalDate());
    }

    /**
     * Converts a LocalDate to epoch milliseconds at midnight Eastern time.
     */
    public static long toEpochMillis(LocalDate date) {
        return date.atStartOfDay(EASTERN).toInstant().toEpochMilli();
    }
}
