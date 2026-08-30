package vlearning.payments;

import java.util.Objects;
import java.util.function.Supplier;

public final class RequestContext {

    private static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<RequestContext>();

    private final String requestId;
    private final String region;

    public RequestContext(String requestId, String region) {
        this.requestId = requestId;
        this.region = region;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRegion() {
        return region;
    }

    public static void set(RequestContext context) {
        CURRENT.set(context);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static boolean isSet() {
        return CURRENT.get() != null;
    }

    public static RequestContext current() {
        return CURRENT.get();
    }

    public static <T> T callWith(RequestContext context, Supplier<T> action) {
        set(context);
        try {
            return action.get();
        } finally {
            clear();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RequestContext that = (RequestContext) o;
        return Objects.equals(requestId, that.requestId) && Objects.equals(region, that.region);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, region);
    }

    @Override
    public String toString() {
        return "RequestContext{requestId='" + requestId + "', region='" + region + "'}";
    }
}
