# 개발 앱·관리자 웹의 예약 금액 실연동 검증

기준일: 2026-09-29 (KST)

대상: [#27](https://github.com/bodeul110/bodeul-platform/issues/27), [PR #471](https://github.com/bodeul110/bodeul-platform/pull/471)

## 결론

실제 개발 Firebase 인증을 사용하는 Galaxy S24 앱에서 합성 예약 1건을 접수하고, Core API·PostgreSQL 결제 원장·관리자 웹이 같은 **40,000원**을 반환하는 것을 확인했다. 검증 후 앱에서 해당 예약만 취소했다. 원장 금액과 생성·취소 이벤트는 보존됐으며 실제 입금·환불은 없었다.

이는 가격 생성·조회·취소의 개발 실연동 결과다. 관리자 결제 상태 변경, MFA·App Check 강제 검증, 개별 입금기한, 운영 수취까지 완료했다는 뜻은 아니다.

## 변경된 범위

- 기존 개발 환자와 관리자 계정을 재사용했다. 새 계정 생성, 비밀번호 재설정, 관리자 권한 추가는 하지 않았다.
- 사용자가 허용한 개발용 합성 예약 1건만 생성·취소했다. 건강·연락 정보는 테스트 값이며 실제 환자·보호자를 연결하지 않았다.
- 예약을 삭제하거나 원장·감사 이력을 정리하지 않았다. 개발 DB migration·직접 SQL 쓰기, 운영 배포·설정 변경, 실제 계좌 안내·송금·환불은 하지 않았다.
- 제품 코드는 변경하지 않았다. 이번 PR은 검증 기록만 추가한다.

## 검증 대상

| 구분 | 확인한 대상 |
| --- | --- |
| 앱·Core API 소스 | `85f20f01e247f3bc334196197fa676789754cf5f` (`dev`, #471 병합) |
| 개발 Core API | `bodeul-core-api-preview-00025-hsp`, 트래픽 100% |
| 개발 배포 | [자동 배포 #36425240181](https://github.com/bodeul110/bodeul-platform/actions/runs/36425240181) |
| Android | Galaxy S24 / Android 16, 실제 개발 Firebase·Core API·Realtime 설정 |
| 관리자 웹 | `643cb86933d1851724333900a39e5c75f89363b5`의 Vercel Preview |
| 관리자 주소 | [개발 브랜치 주소](https://bodeul-admin-web-git-dev-bodeul110.vercel.app/)의 개발 환경 배너 확인 |
| 합성 예약 식별 메모 | `QA-27-20260929-PRICE40000-NO-TRANSFER` |

앱은 Firebase 설정이 없는 Mock/CI APK가 아니다. 아래 APK를 다시 빌드·설치한 뒤 원격 업무 흐름과 별도의 합성 화면 회귀를 실행했다. 기기 식별자, 계정·토큰·비밀번호와 원격 DB 접속 문자열은 기록하지 않는다.

```text
app-debug.apk SHA-256
48D7A0F0DDBBE5597513E85AF7436169A87716B24C7D5E1E4167BAB696A242F2

app-debug-androidTest.apk SHA-256
620A63B52F3A89B6A8AF5083D8E4CB80E69E5B6AD055755D4615674BCC02500E
```

## 실제 연동 결과

| 단계 | 결과 |
| --- | --- |
| 예약 폼 | 기존 개발 환자로 병원·일정·합성 건강 정보·만남 장소를 입력. 기본 요금 40,000원, 추가요금 0원, 할인 0원 표시 |
| 결제 안내 | 무통장입금 선택, 실제 계좌 없는 개발 합성 모드·40,000원 안내 확인 |
| 생성 | 16:43:19, 같은 Cloud Run 리비전의 `POST /api/appointments/price-confirmed` HTTP 201 |
| 앱 재조회 | 예약 상세 HTTP 200, 접수 대기·40,000원·무통장입금 표시 |
| 앱 결제 원장 | `GET /api/appointments/{id}/payment` HTTP 200, 확인 예정 금액 40,000원·입금 확인 대기 |
| 관리자 조회 | 실제 브라우저 로그인 후 예약 코드 검색·결제 API 각각 HTTP 200. 예약과 원장의 예상 금액 40,000원, `AWAITING_DEPOSIT`, 버전 0, `CREATED` 이벤트 1건 |
| 실제 금전 경계 | `receivedAmount`, 입금 확인·환불 시각 모두 `null`. 관리자 상태 변경 기능은 잠긴 상태 유지 |
| 앱 취소 | 16:48:24, 해당 예약의 `POST /api/appointments/{id}/cancel` HTTP 200. 앱에 입금 처리 취소·40,000원 표시 |
| 관리자 취소 재조회 | HTTP 200, `CANCELED`, 버전 1, 예상 금액 40,000원 유지. 생성 이벤트와 예약 취소에 따른 자동 취소 이벤트 2건 보존 |

PostgreSQL 결과는 실제 DB를 읽는 환자 Core API와 별도 관리자 서버의 응답으로 교차 확인했다. 직접 DB 접속이나 전체 테이블 대조를 수행한 것으로 적지 않는다.

환자 예약 목록은 검증 전 총 8건(진행·대기 4, 완료 1, 취소 3), 생성 후 9건(5, 1, 3), 취소 후 9건(4, 1, 4)이었다. 기존 예약에는 쓰기 요청을 보내지 않았고 목록의 과거 69,000원 예약도 그대로 표시됐다. 기존 8건 전체의 필드별 DB 비교나 96,000원 예약·과거 쿠폰 요청의 실제 재전송까지 검증한 것은 아니다.

## 빌드·화면 회귀

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest :app:verifyReleaseAppCheckClasspath --console=plain
```

- Android 빌드·개발/운영 설정 경계·Release App Check 의존성 검사 통과.
- 개발/운영 혼합 설정 회귀의 재현 명령은 `node tools/android/check-environment-boundary.mjs`이며, 선행 [#471 preflight 통과 기록](https://github.com/bodeul110/bodeul-platform/actions/runs/36414023086/job/108900715928)에서 확인할 수 있다.
- Android 단위 테스트 312건 통과.
- 위 앱·계측 APK를 Galaxy S24에 설치하고 선택한 계측 30건을 다시 실행해 `OK (30 tests)` 확인.
- 계측 구성: 예약 폼 4, 하단 메뉴 6, 예약 목록 inset 1, 가이드 메모 변경 2, 첨부·인증 복구 6, 후반 가이드 7, 복약 단계 3, 보호자 리포트 1.

계측 30건은 합성 모델·저장소를 주입하는 화면 회귀다. 원격 인증·결제 실연동의 근거는 앞 절의 수동 앱·관리자 브라우저 흐름이며, 서로 대신하지 않는다. 휴대폰의 전역 내비게이션·글자 크기·위치 권한은 변경하지 않았다.

## 발견 사항과 남은 범위

- [#448](https://github.com/bodeul110/bodeul-platform/issues/448): 취소 상세의 `예약 목록 보기`가 빈 예약 폼을 열었다. 이어 홈으로 복귀했을 때 `일정·이력` 탭 선택 표시가 남고 같은 탭을 눌러도 이동하지 않았다. 홈 탭을 한 번 선택한 뒤 일정·이력을 누르면 목록에 진입했다. 메뉴 잘림과 별개의 실제 Activity 복귀 경로 회귀이므로 전체 왕복 검증을 완료로 표시하지 않는다.
- [관리자 웹 #79](https://github.com/bodeul110/bodeul-admin-web/issues/79): 개발 관리자에서 예약·결제 조회는 정상이나 매니저 심사 목록은 HTTP 503을 반환했다. 별도 관리자 서버의 런타임 자격 증명 구성 점검이 필요하다. 결제 DB 조회 실패나 앱 금액 오류로 합쳐 판단하지 않는다.
- 개별 `paymentDueAt`은 여전히 `null`이다. 앱은 미지정 상태·24시간 원칙·임박 예약 운영자 확인·실제 계좌 안내 전 송금 금지를 표시하고 관리자는 기록 없음으로 표시했다. 개별 기한 저장·공유 계약은 아직 남아 있다.
- 관리자 로그인과 조회 성공만으로 MFA·App Check 강제 검증을 통과했다고 보지 않는다. 이번에 인증·인가 정책이나 결제 쓰기 설정을 바꾸지 않았다.
- 기존 96,000원·쿠폰 요청의 실제 재시도, 혼합 서버 리비전·롤백 종단 흐름, 전체 개발 참여자의 앱 갱신과 운영 수취 게이트는 미완료다. [기존 가격 계약 회귀](issue-27-price-contract-2026-09-28.md)와 이번 실연동 범위를 구분한다.

#27은 위 후속 범위 때문에 유지하며, 이번 결과만으로 production 전환이나 실제 금전 수취를 승인하지 않는다.
