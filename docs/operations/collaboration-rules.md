# 협업 규칙

기준일: 2026-09-27

## 브랜치와 배포

| 범위 | 기준 |
| --- | --- |
| 기능 개발 | 최신 `origin/dev`에서 작업 브랜치를 만들고 `dev`로 PR |
| 기능 병합 | 변경 범위의 테스트와 필수 CI를 통과한 뒤 squash merge |
| 운영 승격 | 검증된 `dev` → `master` release PR을 merge commit으로 병합 |
| 긴급 수정 역반영 | `master` → `dev` 동기화 PR을 merge commit으로 병합 |
| 관리자 웹 | `dev`는 Vercel Preview, `master`는 Production |
| Core API | 개발은 `dev` push/수동 배포, 운영은 `master`의 보호된 수동 workflow |
| DB migration | 앱 배포와 분리한 수동 workflow. 개발 검증·운영 백업 증적 필요 |

두 저장소의 기본 브랜치는 `master`지만 일상 개발 기준선은 `dev`다. 기존에 쌓아 둔 PR은 선행 PR과 diff 범위를 확인한 후 대상 브랜치를 바꾼다. 이름만 바꿔 의존 관계를 끊지 않는다. 상세 기준은 [환경 전환 계획](dev-production-branch-transition-plan.md)을 따른다.

## 시작 전 확인

```powershell
git status --short
git branch --show-current
git fetch origin
git log origin/dev --format="%h %an %ad %s" --date=short -10
git rev-list --left-right --count HEAD...origin/dev
git diff --stat HEAD..origin/dev
```

- 왼쪽은 로컬에만, 오른쪽은 원격에만 있는 커밋 수다. 양쪽 모두 있으면 바로 pull하지 말고 분기 이력과 변경 파일을 확인한다.
- [현재 구현 상태](../status/implementation-status.md) 1~5장, 관련 Issue·PR과 담당 영역을 확인한다. 뒤의 누적 이력은 당시 결과다.
- 기존 사용자 변경과 다른 작업자의 파일을 삭제하거나 되돌리지 않는다. dirty worktree는 그대로 보존하고 별도 worktree를 쓰거나 변경 소유자와 조율한다.
- 담당 파일·범위가 겹치면 작업을 나눈다. 진행 중인 공유 브랜치를 임의 rebase/force push하지 않는다.

깨끗한 로컬 `dev`를 동기화할 때만 다음을 사용한다.

```powershell
git switch dev
git pull --ff-only origin dev
git switch -c feature/작업이름
```

다른 worktree에서 `dev`를 사용 중이면 현재 worktree의 깨끗한 상태를 확인하고 `origin/dev`에서 새 작업 브랜치를 만든다. Codex 작업 브랜치는 `codex/작업이름`을 사용한다. 자동 stash/pop이나 소유자를 모르는 파일 정리로 동기화를 우회하지 않는다.

## 작업 경계

- 메인 저장소는 Android, Spring Core API, Flyway, Firebase Rules·Functions와 공용 계약을 소유한다.
- 관리자 UI·Next.js 서버·Vercel 환경은 별도 `bodeul-admin-web` 저장소에서 변경한다.
- 공용 DB 계약을 바꾸면 두 서버의 영향, 롤링 배포 호환성과 rollback을 기록한다.
- 공용 문자열, Repository/Service, workflow, 운영 스크립트와 기준 문서의 동시 수정을 피한다.
- 백업·복원·seed·cleanup·retention은 먼저 dry-run으로 확인하고 실제 쓰기는 명시 승인 범위에서만 수행한다.
- 개발·운영 비밀값을 섞거나 DB 접속 정보를 PR·로그·문서에 붙이지 않는다.

## PR과 리뷰

- 제목에는 실제 변경 내용을 적고 도구 이름을 표시하지 않는다.
- 본문은 `배경`, `변경 내용`, `확인`, 필요한 경우 `참고할 점`만 짧게 작성한다.
- 설계·보안·인프라는 선택한 방식, 대안, 현재 규모에서의 이유와 리스크를 덧붙인다.
- 리뷰는 결론과 재현·코드 근거를 바로 적는다. 실행하지 않은 실기기·DB·운영 검증을 통과했다고 쓰지 않는다.
- 의존성 변경은 승인 범위를 확인하고, 봇 PR도 최신 기준선의 diff·lockfile·필수 CI를 검토한다.
- 리뷰 중 새 커밋이 올라오면 최신 SHA를 다시 확인한다. 팀원 PR의 검토와 실제 병합 권한은 요청 범위를 따른다.
- 취약점과 실제 비밀값은 공개 댓글에 올리지 않는다.

## 검증과 문서

| 변경 | 기본 검증 |
| --- | --- |
| Android | `.\gradlew.bat assembleDebug --console=plain`, 영향 범위의 `testDebugUnitTest` |
| Core API | `.\core-api\gradlew.bat -p core-api check --console=plain` |
| Functions | `npm --prefix functions test` |
| Firebase 도구·Rules | 관련 toolkit/preflight와 Rules 에뮬레이터 테스트 |
| 관리자 웹 | 별도 저장소의 test·lint·Next build·빌드 런타임 점검·Vite rollback build |
| 문서 | UTF-8·링크·경로·현재 코드/환경 정합성, `git diff --check` |

구조 변경은 [설계 판단 기록 규칙](../architecture/decision-log.md)을 따른다. 기준 문서는 해당 주제에, 날짜별 실행 증거는 `docs/reports/`에 둔다. 현재 상태 1~5장은 최신으로 유지하고 누적 이력은 과거 상태를 덮어쓰지 않는다.

## 종료 전 확인

1. 변경 범위와 검증 결과, 남은 항목을 기록한다.
2. `git diff --check`와 `git status --short`로 자신의 파일과 비공개 산출물을 구분한다.
3. `git fetch origin`으로 PR 기준선과 최신 head를 확인한다.
4. 필수 체크·검토 결과를 확인한 뒤 승인된 범위만 병합한다.
5. 코드 병합, Preview 배포, 운영 배포와 실제 업무 검증을 각각 구분해 알린다.
