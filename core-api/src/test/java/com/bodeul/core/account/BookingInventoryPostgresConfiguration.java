package com.bodeul.core.account;

import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@TestConfiguration(proxyBeanMethods = false)
@Import({JdbcAccountDeletionImpactRepository.class, DefaultAccountDeletionReadinessService.class})
public class BookingInventoryPostgresConfiguration {
    public static Map<String, Long> completeCounts(ApplicationContext context, UUID userId, String firebaseUid) {
        var source = context.getBean(AccountDeletionReadinessService.class).inspect(userId, firebaseUid).sources().getFirst();
        assertThat(source.status()).isEqualTo(AccountDeletionReadinessService.SourceStatus.COMPLETE);
        return source.counts();
    }
}
