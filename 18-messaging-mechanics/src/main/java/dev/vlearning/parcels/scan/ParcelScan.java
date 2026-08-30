package dev.vlearning.parcels.scan;

/**
 * A fact: this parcel was scanned at this hub at this instant. Past tense, addressed to nobody,
 * cannot be rejected. Scans for one parcel form a small stream whose order matters
 * (ACCEPTED before DELIVERED); scans for different parcels are unrelated.
 *
 * @param sequence producer-side sequence number — a test convenience, not something a real
 *                 event would carry. It lets a test reconstruct "the order the producer sent"
 *                 without trusting clocks.
 */
public record ParcelScan(String scanId,
                         String parcelId,
                         String customerId,
                         ScanStatus status,
                         String hubId,
                         long scannedAtEpochMs,
                         int sequence) {

    public static ParcelScan of(String parcelId, String customerId, ScanStatus status, int sequence) {
        return new ParcelScan(parcelId + "-" + sequence, parcelId, customerId, status, "HUB-1",
                1_760_000_000_000L + sequence, sequence);
    }
}
