package com.bodeul.core.auth;

/** 민감한 새 쓰기 직전에 참여 계정의 삭제·비활성 상태를 확인한다. */
public interface FirebaseAccountStatus {

    boolean isActive(String firebaseUid);

    final class UnavailableException extends RuntimeException {
        public UnavailableException() {
            super("계정 활성 상태를 확인할 수 없습니다.");
        }
    }
}
