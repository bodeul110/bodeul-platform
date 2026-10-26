package com.bodeul.core.consent;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** 패키지 비공개 저장소를 실제 트랜잭션 프록시로 연결하는 격리 DB 테스트 구성. */
@TestConfiguration(proxyBeanMethods = false)
@EnableTransactionManagement(proxyTargetClass = true)
@Import({DefaultGuardianBookingApprovalService.class, JdbcGuardianBookingApprovalRepository.class,
        DefaultGuardianSharingConsentService.class, JdbcGuardianSharingConsentRepository.class,
        GuardianSharingConsentAuthorizer.class})
public class GuardianBookingPostgresConfiguration { }
