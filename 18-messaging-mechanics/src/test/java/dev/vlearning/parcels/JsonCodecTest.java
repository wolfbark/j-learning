package dev.vlearning.parcels;

import dev.vlearning.parcels.scan.ParcelScan;
import dev.vlearning.parcels.scan.ScanStatus;
import dev.vlearning.parcels.wire.JsonCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonCodecTest {

    private final JsonCodec codec = new JsonCodec();

    @Test
    void roundTripsAScan() {
        var scan = ParcelScan.of("P-1", "C-1", ScanStatus.AT_HUB, 7);

        var json = codec.toJson(scan);
        assertThat(json).contains("\"parcelId\":\"P-1\"").contains("\"status\":\"AT_HUB\"");
        assertThat(codec.fromJson(json, ParcelScan.class)).isEqualTo(scan);
    }

    @Test
    void isATolerantReader() {
        String futureVersion = """
                {"scanId":"P-1-7","parcelId":"P-1","customerId":"C-1","status":"AT_HUB",
                 "hubId":"HUB-1","scannedAtEpochMs":1,"sequence":7,"deliveryWindow":"08:00-12:00"}
                """;

        var scan = codec.fromJson(futureVersion, ParcelScan.class);

        assertThat(scan.parcelId()).isEqualTo("P-1");
        assertThat(scan.status()).isEqualTo(ScanStatus.AT_HUB);
    }
}
