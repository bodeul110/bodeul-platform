# Kakao Local Core API 경계

기준일: 2026-09-27

## 작업 목적

Android APK에 Kakao Local REST API 키를 넣지 않고, 병원·약국 검색의 인증, 캐시, 호출 제한과 오류 계약을 Spring Core API에서 통제한다.

## 선택한 방식

- Android는 Firebase ID token과 발급 가능한 경우 App Check token을 함께 `GET /api/places/search`로 보낸다.
- Core API는 PostgreSQL `app_users.role`까지 확인된 사용자만 요청을 허용한다.
- 서버가 Kakao Local 키워드 검색 API를 호출하고 `HP8` 병원 또는 `PM9` 약국 결과만 반환한다.
- Kakao REST API 키는 Google Secret Manager에서 Cloud Run 환경 변수로 주입한다.
- preview는 Cloud Run 최대 인스턴스가 1개이므로 서버 메모리에서 결과를 6시간, 최대 1,000개 캐시하고 사용자별 분당 60회로 제한한다.
- Core API가 실패하면 Android는 Kakao를 직접 호출하지 않고 기존 로컬 병원 목록 또는 기본 지도 안내로 복구한다.

## 동행 가이드 9 외부 검색 경계

- `PHARMACY_ROUTE`의 `카카오맵에서 약국 찾기`는 Core API 검색이 아니라 카카오맵 장소 검색 화면을 여는 탐색용 CTA다.
- Android는 `kakaomap://open?page=placeSearch`를 먼저 열고, 실패하면 카카오 모바일 웹과 설치 화면으로 내려간다.
- 외부 앱을 여는 동작만으로 현재 단계를 완료하거나 변경하지 않는다. 일반 복귀에서는 기존 ViewModel의 같은 세션과 단계를 유지한다.
- 외부 카카오맵에서 입력한 검색어나 선택 결과는 BoDeul 서버와 PostgreSQL에 저장하지 않는다.
- 예약 병원 검색, 앱 안의 병원·약국 후보 목록과 내장 지도 표시는 계속 `GET /api/places/search`를 사용한다.

## API 계약

| 항목 | 값 |
| --- | --- |
| 메서드와 경로 | `GET /api/places/search` |
| 인증 | `Authorization: Bearer <Firebase ID token>` |
| 앱 검증 | `X-Firebase-AppCheck: <App Check token>`, preview는 observe 모드 |
| 역할 | `PATIENT`, `GUARDIAN`, `MANAGER`, `ADMIN` 중 PostgreSQL에 등록된 사용자 |
| `query` | 공백 제거 후 1~100자 |
| `category` | `HOSPITAL` 또는 `PHARMACY` |
| 최대 결과 | 15개 |
| 응답 캐시 | 클라이언트 응답은 `no-store`, Kakao 결과는 서버에서 6시간 캐시 |

요청 예시:

```http
GET /api/places/search?query=서울대병원&category=HOSPITAL
Authorization: Bearer <Firebase ID token>
X-Firebase-AppCheck: <App Check token>
```

응답 예시:

```json
{
  "places": [
    {
      "name": "서울대학교병원",
      "latitude": 37.5796,
      "longitude": 126.999
    }
  ]
}
```

## 오류 계약

| HTTP | 오류 코드 | 의미 |
| ---: | --- | --- |
| 400 | `invalid_place_search_request` | 검색어 또는 범주가 잘못됨 |
| 401 | `missing_authorization`, `invalid_firebase_token` | Firebase 인증 실패 |
| 401 | `missing_app_check`, `invalid_app_check` | App Check enforce 모드에서 token 누락 또는 검증 실패 |
| 403 | `role_not_found` | PostgreSQL 서비스 역할 미등록 |
| 429 | `place_search_rate_limit_exceeded` | 사용자별 서버 요청 제한 초과 |
| 429 | `kakao_local_quota_exceeded` | Kakao Local 쿼터 또는 초당 제한 초과 |
| 502 | `kakao_local_response_invalid` | Kakao 요청 거절 또는 응답 파싱 실패 |
| 503 | `kakao_local_not_configured` | 서버 REST API 키 미설정 |
| 503 | `kakao_local_credentials_invalid` | Kakao 서버 인증 설정 오류 |
| 503 | `kakao_local_unavailable` | timeout 또는 Kakao 장애 |
| 503 | `app_check_not_configured` | App Check enforce 모드에서 서버 검증기 미설정 |

오류 응답과 로그에는 Kakao REST API 키, Firebase ID token, Kakao 원본 오류 본문을 넣지 않는다.

## 검토한 대안

| 대안 | 장점 | 현재 제외 이유 |
| --- | --- | --- |
| Android 직접 호출 유지 | 서버 비용과 구현이 가장 단순함 | APK에서 키를 추출할 수 있고 사용자별 호출 제한과 공용 캐시를 적용하기 어려움 |
| Firebase Functions proxy | 기존 Firebase 운영 경로를 재사용함 | 사용자 서비스의 최종 HTTP 계약을 Spring Core API로 모으기로 한 구조와 중복됨 |
| Kakao 응답을 PostgreSQL에 영구 저장 | 인스턴스 재시작 후에도 캐시가 유지됨 | 장소 검색 결과의 신선도와 삭제 정책이 추가되고 현재 MVP 호출량에는 과함 |

## 선택 이유

현재 MVP 규모에서는 Cloud Run 1개 preview 인스턴스와 짧은 서버 캐시만으로 키 비노출, 중복 호출 감소, 사용자별 제한을 함께 검증할 수 있다. 별도 Redis나 API Gateway를 먼저 도입하면 운영 대상만 늘어나므로 실제 트래픽이 확인되기 전에는 추가하지 않는다.

## 호출 허용 IP와 outbound 결정

### 개발·운영 키 관리

2026-09-27 사용자 승인에 따라 현재 MVP의 개발·운영 Core API는 기존 Kakao Local REST 키를 함께 사용한다. 새 카카오 앱이나 키를 만드는 대신 각 Google Cloud 프로젝트의 preview/production Secret Manager 항목으로 나누어 보관하고 런타임은 자기 프로젝트의 숫자 version만 참조한다. 등록·배포 완료 여부는 [환경 분리 실행 기록](../reports/dev-production-separation-2026-09-27.md)을 따른다.

- 작업 목적: 서버·DB의 개발/운영 분리를 유지하면서 장소 검색 연결을 준비한다.
- 대안: 별도 카카오 앱 또는 환경별 키로 회전 영향 범위를 분리할 수 있으나 관리·설정 대상이 늘어난다.
- 선택 이유: 현재 MVP에서는 기존 앱을 유지하고 서버 인증·캐시·호출 제한과 환경별 secret 접근 권한을 사용하는 편이 운영 부담이 작다.
- 리스크: 카카오 쿼터는 앱 기준으로 합산된다. 동일 키 유출·폐기는 양쪽 환경에 영향을 주므로 교체 시 각 secret version 등록과 재배포를 함께 관리한다. 개발 호출량이나 키 접근 인원이 늘면 분리를 다시 검토한다.

### 네트워크 경계

- preview와 초기 production Cloud Run은 기본 동적 outbound IP를 사용한다.
- Kakao REST API 키의 호출 허용 IP는 비워 두며, 현재 MVP에서는 VPC와 Cloud NAT를 만들지 않는다.
- Kakao 호출 허용 IP는 필수 연결 조건이 아니라 키 유출 피해를 줄이는 선택적 보안 기능이다. 현재는 REST 키를 Android와 GitHub에서 제거하고 Secret Manager로만 주입하며, Firebase 인증·PostgreSQL 역할 인가·사용자별 호출 제한·비밀값 로그 비노출을 먼저 적용했다.
- 고정 IP를 위해서는 모든 outbound를 VPC로 보내고 Cloud NAT와 외부 IP를 계속 운영해야 한다. Cloud NAT에는 gateway 시간, 외부 IP 시간과 처리량 비용이 발생하므로 호출량이 적고 외부 시스템이 고정 IP를 요구하지 않는 MVP에 선제 도입하지 않는다.
- 고정 outbound가 필요해지면 Serverless VPC Access connector보다 Cloud Run의 Direct VPC egress를 우선 검토하고, 별도 변경에서 reserved IP와 Cloud NAT를 구성한다.

다음 중 하나가 확인되면 고정 outbound와 Kakao 호출 허용 IP를 다시 검토한다.

1. Kakao 계약·정책 또는 production 사용 승인이 호출 허용 IP를 필수로 요구한다.
2. 보안 검토에서 Secret Manager, 최소 권한, 키 회전과 호출 제한만으로는 키 오용 위험을 수용할 수 없다고 판단한다.
3. 다른 production 의존성도 고정 outbound를 요구해 VPC와 Cloud NAT의 운영 비용을 함께 분담할 수 있다.

전환할 때는 고정 outbound 리비전의 실제 출발 IP와 Kakao 실호출을 먼저 확인한 뒤 Kakao 콘솔의 호출 허용 IP를 활성화한다. rollback은 Kakao 호출 허용 IP를 먼저 해제한 뒤 VPC·NAT 설정을 되돌려 장소 검색 중단을 피한다.

## 리스크와 전환 조건

- Cloud Run을 2개 이상으로 확장하면 인메모리 캐시와 rate limit은 인스턴스별로 분리된다. production 확장 전 Redis, API Gateway 또는 Cloud Armor 기반의 공용 제한을 검토한다.
- Kakao 호출 허용 IP 제한은 고정 outbound IP가 있어야 한다. 초기 production은 동적 outbound를 유지하며, 위 전환 조건을 충족할 때 Direct VPC egress와 Cloud NAT를 별도 변경으로 도입한다.
- 동적 outbound 동안 REST 키가 Secret Manager 밖으로 유출되면 Kakao가 출발 IP로 직접 차단하지 못한다. Secret 접근 감사, 키 회전과 Kakao 쿼터 이상 징후 확인을 보완 통제로 유지한다.
- Android의 직접 호출과 `kakaoRestApiKey` 리소스는 CodeQL 검토 후 제거했다. Core API 성공률과 로컬 목록 fallback 동작은 실기기에서 확인한다.
- App Check token 발급 실패는 observe 단계에서 헤더 누락으로 기록하고 기존 검색을 유지한다. enforce 전환은 Android 실기기에서 `valid`가 확인된 뒤 수행한다.
- Kakao 쿼터 사용량은 Kakao Developers 앱 관리 페이지에서 확인하며, 429 발생 건수는 원본 응답 없이 오류 코드 기준으로 집계한다.

## 공식 근거

- [Kakao Local 키워드 장소 검색](https://developers.kakao.com/docs/ko/local/dev-guide#search-by-keyword)
- [Kakao REST API 오류와 429](https://developers.kakao.com/docs/en/rest-api/reference#response-code)
- [Kakao API 보안 권장 사항](https://developers.kakao.com/docs/ko/getting-started/security-guideline)
- [Kakao 호출 허용 IP 주소](https://developers.kakao.com/docs/ko/app-setting/app#allowed-ip-address)
- [Kakao API 쿼터](https://developers.kakao.com/docs/ko/getting-started/quota)
- [Cloud Run 고정 outbound IP](https://cloud.google.com/run/docs/configuring/static-outbound-ip)
- [Cloud Run Direct VPC egress와 connector 비교](https://cloud.google.com/run/docs/configuring/connecting-vpc)
- [Cloud NAT 가격](https://cloud.google.com/nat/pricing)
