package dev.vlearning.parcels.schema;

import java.util.List;

/** The published contract of {@code ParcelScan}, and the three ways step 7 tries to change it. */
public final class ScanSchemas {

    public static final RecordSchema V1 = new RecordSchema("ParcelScan", 1, List.of(
            FieldSpec.required("scanId", FieldType.STRING),
            FieldSpec.required("parcelId", FieldType.STRING),
            FieldSpec.required("customerId", FieldType.STRING),
            FieldSpec.required("status", FieldType.ENUM),
            FieldSpec.required("hubId", FieldType.STRING),
            FieldSpec.required("scannedAtEpochMs", FieldType.LONG),
            FieldSpec.required("sequence", FieldType.INT)));

    /** Compatible: a new field nobody has to know about, with a default for readers that don't. */
    public static final RecordSchema V2_OPTIONAL_DELIVERY_WINDOW =
            V1.plus(FieldSpec.optional("deliveryWindow", FieldType.STRING, "unknown"), 2);

    /** Breaking: old data has no carrierId, and there is no default to invent one from. */
    public static final RecordSchema V3_REQUIRED_CARRIER =
            V1.plus(FieldSpec.required("carrierId", FieldType.STRING), 3);

    /** Breaking: the field is still called scannedAtEpochMs, but it is no longer a number. */
    public static final RecordSchema V4_RETYPED_TIMESTAMP =
            V1.retyped("scannedAtEpochMs", FieldType.STRING, 4);

    /** Breaking, and the sneakiest: a rename is a removal plus an addition. */
    public static final RecordSchema V5_RENAMED_HUB =
            V1.without("hubId", 5).plus(FieldSpec.required("facilityId", FieldType.STRING), 5);

    private ScanSchemas() {
    }
}
