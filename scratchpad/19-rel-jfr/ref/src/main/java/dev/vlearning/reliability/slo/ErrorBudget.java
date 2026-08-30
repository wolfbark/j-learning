package dev.vlearning.reliability.slo;

import java.time.Duration;
import java.util.Optional;

public record ErrorBudget(double objective) {

    public static ErrorBudget of(double objective) {
        if (objective <= 0 || objective >= 1) {
            throw new IllegalArgumentException("objective must be between 0 and 1: " + objective);
        }
        return new ErrorBudget(objective);
    }

    public double allowedBadEvents(long valid) {
        return (1 - objective) * valid;
    }

    public double compliance(long good, long bad) {
        long valid = good + bad;
        return valid == 0 ? 1.0 : good / (double) valid;
    }

    public double remainingFraction(long good, long bad) {
        double allowed = allowedBadEvents(good + bad);
        return allowed == 0 ? 0 : (allowed - bad) / allowed;
    }

    public double burnRate(long good, long bad) {
        long valid = good + bad;
        if (valid == 0) {
            return 0;
        }
        return (bad / (double) valid) / (1 - objective);
    }

    public boolean exhausted(long good, long bad) {
        return remainingFraction(good, bad) <= 0;
    }

    public Optional<Duration> timeToExhaustion(long good, long bad, Duration window) {
        double burn = burnRate(good, bad);
        if (burn == 0) {
            return Optional.empty();
        }
        double remaining = remainingFraction(good, bad);
        if (remaining <= 0) {
            return Optional.of(Duration.ZERO);
        }
        return Optional.of(Duration.ofNanos((long) (window.toNanos() * (remaining / burn))));
    }
}
