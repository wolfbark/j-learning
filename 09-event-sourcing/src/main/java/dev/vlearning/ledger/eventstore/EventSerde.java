package dev.vlearning.ledger.eventstore;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.vlearning.ledger.domain.AccountEvent;
import tools.jackson.databind.json.JsonMapper;

/**
 * Event ⇄ (type name, JSON payload). Two deliberate choices:
 *
 * <p><b>Its own mapper, not the web layer's.</b> The JSON in the events table is a PERSISTED
 * CONTRACT — old payloads must deserialize forever. The Boot-managed ObjectMapper is tuned
 * for HTTP responses and reconfigured casually; coupling your storage format to it is how
 * "someone enabled snake_case for the API" corrupts your history. (Jackson 3 lives under
 * {@code tools.jackson}; exceptions are unchecked, records just work.)
 *
 * <p><b>The sealed interface is the type registry.</b> {@code getPermittedSubclasses()}
 * discovers every event type at startup, so step 6's new events register themselves. The
 * simple class name becomes the {@code type} column. Honest caveat, expanded in the lesson:
 * production systems name types explicitly (e.g. {@code account-opened.v2}) precisely so a
 * class RENAME cannot orphan history — the map below is where an upcaster would hook in.
 */
@Component
public class EventSerde {

    private static final Map<String, Class<? extends AccountEvent>> TYPES =
            Arrays.stream(AccountEvent.class.getPermittedSubclasses())
                    .collect(Collectors.toUnmodifiableMap(Class::getSimpleName,
                            type -> type.asSubclass(AccountEvent.class)));

    private final JsonMapper mapper = JsonMapper.builder().build();

    public String typeName(AccountEvent event) {
        return event.getClass().getSimpleName();
    }

    public String toJson(AccountEvent event) {
        return mapper.writeValueAsString(event);
    }

    public AccountEvent fromJson(String typeName, String json) {
        var type = TYPES.get(typeName);
        if (type == null) {
            throw new IllegalStateException(
                    "Unknown event type '%s' in the store. Was a record renamed or deleted? Old events never go away — this is where an upcaster belongs."
                            .formatted(typeName));
        }
        return mapper.readValue(json, type);
    }
}
