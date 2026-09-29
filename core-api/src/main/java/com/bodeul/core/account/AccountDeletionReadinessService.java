package com.bodeul.core.account;

import java.util.List;
import java.util.Map;
import java.util.UUID;

interface AccountDeletionReadinessService {

    ReadinessResult inspect(UUID userId, String firebaseUid);

    enum Decision {
        NOT_EVALUATED
    }

    enum Source {
        POSTGRESQL,
        FIRESTORE,
        STORAGE,
        FIREBASE_AUTH,
        BACKUP
    }

    enum SourceStatus {
        COMPLETE,
        PARTIAL,
        NOT_EVALUATED,
        ERROR
    }

    enum ObservationCode {
        ACTIVE_APPOINTMENT_PRESENT,
        ACTIVE_SESSION_PRESENT,
        ACTIVE_BOOKING_APPROVAL_PRESENT,
        POSTGRES_PROFILE_MISSING
    }

    enum BlockerCode {
        SOURCE_UNAVAILABLE,
        INVENTORY_INCOMPLETE
    }

    record SourceInventory(Source source, SourceStatus status, Map<String, Long> counts) {
        public SourceInventory {
            counts = Map.copyOf(counts);
        }
    }

    record ReadinessResult(
            boolean readOnly,
            boolean deletionExecuted,
            Decision decision,
            boolean complete,
            List<SourceInventory> sources,
            List<ObservationCode> observationCodes,
            List<BlockerCode> blockerCodes) {
        public ReadinessResult {
            sources = List.copyOf(sources);
            observationCodes = List.copyOf(observationCodes);
            blockerCodes = List.copyOf(blockerCodes);
        }
    }
}
