package com.shopsphere.orderservice.debug;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Manual diagnostic requires a running local Kafka broker; embedded-Kafka integration tests cover DLT behaviour")
class DltHeaderDiagnosticTest {

    @Test
    void shouldPrintDltRecordAndHeaders() {
        // Intentionally disabled in automated builds. Use Kafka CLI to inspect
        // inventory-events.DLT when debugging a local broker manually.
    }
}
