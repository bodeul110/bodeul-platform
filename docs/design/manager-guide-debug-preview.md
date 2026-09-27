# 매니저 가이드 debug 미리보기

기준일: 2026-09-26

## 작업 목적

Core API가 `GUIDE_NOT_READY`를 반환하거나 개발용 동행 세션이 아직 준비되지 않은 상태에서도 매니저 가이드 1~13단계 화면을 독립적으로 구현·검증한다.

## 선택한 방식

- 운영 `ManagerGuideActivity`는 기존 `ServiceLocator`와 서버 진행 판정을 계속 사용한다.
- 인증·매니저 저장소·realtime 구독을 교체할 수 있는 작은 `protected` 주입 지점만 운영 Activity에 둔다.
- 단계 선택기, 13단계 코드 픽스처, 로컬 저장소와 미리보기 Activity는 모두 `app/src/debug` 소스 셋에 둔다.
- Preview Activity는 Firebase, Core API, Supabase realtime, 기존 위치 공유를 사용하지 않고 가이드 진행·메모·리포트를 로컬 메모리에만 반영한다.
- 실제 가이드 화면과 혼동하지 않도록 화면 상단에 `DEBUG 미리보기 · 서버에 저장되지 않음` 배너를 고정한다.

## 진입 방법

1. debug APK를 설치한다.
2. Android 런처에서 `보들` 아이콘을 길게 누른다.
3. `가이드 미리보기` 바로가기를 선택한다.
4. 1~13단계 중 확인할 화면을 선택한다.

정적 바로가기를 지원하지 않는 Android 7.0 단말이나 자동화에서는 다음 컴포넌트를 직접 실행한다.

```shell
adb shell am start -n com.example.bodeul/.debug.ManagerGuidePreviewSelectorActivity
```

## 대안과 선택 이유

- 운영 화면에 `BuildConfig.DEBUG` 분기로 강제 다음 버튼을 넣는 방식은 release 바이너리에 우회 경로가 남아 제외했다.
- Preview PostgreSQL fixture는 실제 API 통합 검증에는 유용하지만 현재 9단계로 고정되고 인프라 권한이 필요해 모든 화면 개발의 기본 진입로는 사용하지 않았다.
- 두 번째 launcher 아이콘은 기존 `monkey` 기반 자동화가 다른 Activity를 선택할 수 있어 정적 앱 바로가기를 사용했다.

## 리스크와 남은 범위

- 미리보기는 화면과 로컬 입력 흐름을 검증하지만 Core API 인가, DB snapshot, 실제 파일 업로드, realtime 연동을 검증하지 않는다.
- 상봉 단계의 일회성 현재 위치, 약국 이동의 카카오맵, 증빙·처방 자료의 Android 파일 선택기는 실제 기기 기능과 외부 SDK를 사용한다. 이 동작들은 서버 세션을 변경하지 않지만 위치 권한 요청, 외부 앱·네트워크 연결, 로컬 URI 읽기 권한이 발생할 수 있다.
- 서버 통합 검증은 Preview fixture와 실제 개발 매니저 UID를 사용해 별도로 수행한다.
- Core API의 13단계 코드·순서·의미가 바뀐다면 debug 카탈로그와 계약 테스트를 같이 갱신해야 한다.
