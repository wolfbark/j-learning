package dev.vlearning.trips.messages;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Message ⇄ JSON envelope: {@code {"type":"FlightReserved","data":{...}}}.
 * The type name travels inside the payload so that a human with a console
 * consumer (or a failing test's log) can read the traffic — you will spend
 * step 3 doing exactly that. The sealed interface doubles as the type
 * registry: every permitted record is decodable, nothing else is.
 * (Jackson 3 lives under {@code tools.jackson}; records just work.)
 */
@Component
public class TripMessageCodec {

    private static final Map<String, Class<? extends TripMessage>> TYPES =
            Arrays.stream(TripMessage.class.getPermittedSubclasses())
                    .collect(Collectors.toUnmodifiableMap(Class::getSimpleName,
                            type -> type.asSubclass(TripMessage.class)));

    // BigDecimals must survive the JSON tree round-trip with their scale intact
    // (499.50 is not 499.5 when money is involved).
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    public String encode(TripMessage message) {
        return mapper.writeValueAsString(
                new Envelope(message.getClass().getSimpleName(), mapper.valueToTree(message)));
    }

    public TripMessage decode(String json) {
        Envelope envelope = mapper.readValue(json, Envelope.class);
        var type = TYPES.get(envelope.type());
        if (type == null) {
            throw new IllegalArgumentException(
                    "Unknown message type '%s' — not one of the records sealed under TripMessage".formatted(envelope.type()));
        }
        return mapper.treeToValue(envelope.data(), type);
    }

    private record Envelope(String type, JsonNode data) {}
}
