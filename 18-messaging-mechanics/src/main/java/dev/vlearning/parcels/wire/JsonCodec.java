package dev.vlearning.parcels.wire;

import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON codec used on the wire. Two decisions are deliberate and both are lesson material:
 * <ul>
 *   <li>{@code FAIL_ON_UNKNOWN_PROPERTIES} is disabled — this codec is a <em>tolerant reader</em>,
 *       which is what makes "add a field" a non-event for existing consumers.</li>
 *   <li>timestamps are written as epoch millis, so the payload has no library-specific date
 *       encoding a non-Java consumer would have to guess at.</li>
 * </ul>
 */
@Component
public class JsonCodec {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public String toJson(Object value) {
        return mapper.writeValueAsString(value);
    }

    public <T> T fromJson(String json, Class<T> type) {
        return mapper.readValue(json, type);
    }
}
