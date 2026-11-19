package com.example.bodeul.domain.model;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** 인증 제공자가 확인한 로그인 방식만 표시한다. 프로필의 이메일만으로 비밀번호 계정을 추정하지 않는다. */
public final class AccountSignInInfo {
    public enum Provider { EMAIL, GOOGLE, KAKAO, NAVER, APPLE, UNKNOWN }

    private final String userId;
    private final String email;
    private final Set<Provider> providers;

    public AccountSignInInfo(String userId, String email, Collection<String> providerIds, String customProvider) {
        this.userId = clean(userId);
        this.email = clean(email);
        EnumSet<Provider> found = EnumSet.noneOf(Provider.class);
        if (providerIds != null) for (String id : providerIds) {
            if ("password".equals(id)) found.add(Provider.EMAIL);
            if ("google.com".equals(id)) found.add(Provider.GOOGLE);
            if ("apple.com".equals(id)) found.add(Provider.APPLE);
        }
        // 커스텀 로그인은 기존 서버가 발급하는 UID와 가입 제공자가 함께 일치해야 표시한다.
        if (this.userId.startsWith("kakao_") && "KAKAO".equals(customProvider)) found.add(Provider.KAKAO);
        if (this.userId.startsWith("naver_") && "NAVER".equals(customProvider)) found.add(Provider.NAVER);
        if (found.isEmpty()) found.add(Provider.UNKNOWN);
        providers = Collections.unmodifiableSet(found);
    }

    public String getUserId() { return userId; }
    public String getEmail() { return email; }
    public boolean has(Provider provider) { return providers.contains(provider); }
    public boolean canResetPassword() { return has(Provider.EMAIL) && !email.isEmpty() && !userId.isEmpty(); }
    public Provider getPrimaryProvider() { return providers.iterator().next(); }
    public boolean matches(String expectedId, String expectedEmail) {
        return !userId.isEmpty() && userId.equals(expectedId) && email.equalsIgnoreCase(clean(expectedEmail));
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
