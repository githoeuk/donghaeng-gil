# 동행길 작업 기록

## 2026-11-04 — 늦은 목적지 응답 방어 및 교체 가능한 모의 공유

### 작업 기준

- 활성 저장소: `D:\projects\donghaeng-gil`, 브랜치 `main`.
- 시작 HEAD: `a7f024a` (기존 미커밋 메인 화면·위치·상세주소 구현 보존).
- `D:\projects\AGENTS.md` 적용. 기존 SDK·JDK는 C:, 빌드·캐시·임시는 D: 유지.
- 이번 작업에서 커밋·푸시·스테이징하지 않았다. 기존 변경을 초기화하지 않았다.

### 구현한 내용

1. **늦은 목적지 응답 방어**
   - `NavigationSession.java`를 추가해 리스너 연결 수명과 취소·도착 종료 상태를 검사한다.
   - `PleosJourneySource.java`에서 목적지 변경·경로 시작 시 이전 리스너·SDK 연결을 해제하고 새 연결로 조회한다. 이전 연결의 큐에 남은 목적지·주소·상태 콜백을 버린다.
   - 취소·도착 후 목적지·최근 목적지 주소 응답과 늦은 driving 상태로 경로가 되살아나는 것을 막는다.
   - 초기 idle 상태와 목적지가 확인된 뒤 idle을 구분한다. 새 경로·수동 재연결로 새 세대에 진입한다.
   - 종료·변경·해제 시 목적지와 상세주소 캐시를 비운다. 동일 목적지의 더 빈약한 최근 목록 응답으로 상세주소를 되돌리지 않고, 주소 응답으로 ETA 수신 시각을 갱신하지 않는다.

2. **통신 교체 지점**
   - `SharingTransport.java`: 그룹 생성·참여, 공유 시작·중지·탈퇴, 메시지·확인, 스냅샷·리스너 계약.
   - `SharingModels.java`: 차량 최신 상태, 목적지 식별, 메시지, 전달 상태, 불변 스냅샷.
   - `SharingStore.java`: 이전 수명 콜백, 역순 위치·중복 메시지 거절, 전달 상태 보호, 그룹·공유 종료 정리.
   - 공식 앱 연동 어댑터는 지원이 확인되지 않아 만들지 않았다. 인터넷·DB·휴대폰 앱·실제 위치 전송을 추가하지 않았다.

3. **Debug 전용 모의 공유**
   - `MockSharingTransport.java`: 센서·SDK·네트워크·디스크를 사용하지 않는 가상 데이터 시나리오.
   - `SharingDemoActivity.java`: 그룹 만들기·데모 코드 참여(`DEMO42`), 공유 시작·중지·탈퇴, 차량 3대, 상황 메시지와 모의 확인 응답, 연결 장애·갱신 중단·재연결 조작.
   - 메인 화면에 **모의 공유 (정차 중)** 버튼 추가. 가상 데이터와 실제 내 차량 정보는 별도 화면으로 구분한다.
   - 차량 가·나는 같은 목적지, 다는 다른 목적지로 표시하며 다른 목적지의 도착 순서 비교를 제외한다.
   - 15초 넘게 갱신되지 않은 상태는 오래된 정보로 표시하고 최신 ETA를 숨긴다. 오프라인 상태에서는 메시지를 조용히 대기열에 넣지 않으며 실패 메시지를 자동 재전송하지 않는다.
   - 공유 중지 시 차량 수신 상태를 비우고, 탈퇴·화면 종료 시 메모리 그룹 상태를 삭제한다. 주행 최적화 메타데이터를 선언하지 않은 정차용 시험 화면이다.
   - Release에는 모의 Activity와 모의 통신 구현이 없다.

### 변경·추가 파일

- 기존 수정: `MainActivity.java`, `PleosJourneySource.java`, Debug 매니페스트, `README.md`, `docs/main-screen-integration.md`, 설치용 Debug APK.
- 신규 공용 코드: `NavigationSession.java`, `SharingModels.java`, `SharingStore.java`, `SharingTransport.java`.
- 신규 Debug 코드: `MockSharingTransport.java`, `SharingDemoActivity.java`.
- 신규 검사: `app/src/test/java/io/demo/companion/NavigationSessionTest.java`, `app/src/testDebug/java/io/demo/companion/SharingTest.java`.
- 신규 실행 도구: `scripts/test-state.ps1`.
- 설계·시연 문서: `docs/communication-plan.md`.
- 이 기록은 이번 작업 변경 목록이다. Git status에는 앞서 존재하던 변경·미추적 파일도 함께 남아 있다.

### 검증 결과

| 검증 | 결과 |
| --- | --- |
| 기존 JourneyState | 25개 통과 |
| 기존 상세주소 매칭 | 11개 통과 |
| 내비 연결 세대·콜백 순서 | 15개 통과 |
| 공유 수명·모의 흐름·중복·역순·실패·재연결 | 40개 통과 |
| 합계 | **91개 통과** |
| Debug·Release 최종 빌드 | 성공 |
| Release APK manifest·dex 검사 | 모의 Activity·모의 통신 클래스 및 INTERNET 권한 없음 |
| git diff --check | 통과 |

독립 실행 검사 명령:

```powershell
.\scripts\test-state.ps1 -JdkHome 'C:\Program Files\Amazon Corretto\jdk21.0.10_7'
& 'D:\projects\build-donghaeng.ps1' -Tasks @('assembleDebug','assembleRelease')
```

기존 Java 8 source/target의 obsolete 경고, deprecated API 및 Gradle 향후 호환성 경고는 남아 있다. 이번 작업의 빌드 실패는 없다.

Pleos Connect v2.1.0 / `emulator-5554` / 사용자 10에 APK를 설치하고 다음을 화면·UI XML로 확인했다.

- 데모 그룹 생성·공유 시작 후 가상 차량 3대와 서로 다른 목적지 표시.
- 상황 메시지에 ‘모의 확인됨’, 가상 상대 응답에 ‘모의 전달됨’ 표시.
- 갱신 중단 뒤 ‘오래된 정보 · 최신 ETA 표시 보류’ 표시.
- 연결 끊김, 공유 중지 뒤 차량 목록 제거, 그룹 나가기 뒤 메시지 제거.
- 메인 화면 복귀 및 **다시 연결** 후 실제 내비 목적지 `스타벅스 분당궁내DT점`, `경기도 성남시 분당구 대왕판교로 302`, 약 11분·5.6km 표시. ETA 숫자는 에뮬레이터 내비 응답이며 고정값이 아니다.
- 최종 앱은 에뮬레이터 메인 화면에 두었다. 모의 그룹은 종료됐다.

로컬 증거: `.gradle/sharing-active.png`, `.gradle/sharing-message.png`, `.gradle/nav-final.png`, `.gradle/nav-final.xml`. 일부 UI XML은 에뮬레이터 `/sdcard/sharing-*.xml`에 있다. 이 파일들은 Git 제출물에 추가하지 않았다.

### 한계와 미완료 항목

- SDK 공개 목적지 콜백에 요청 ID가 없다. 연결 토큰은 과거 리스너 응답을 차단하는 방어이지 개별 SDK 요청의 식별자가 아니다. SDK 서비스가 과거 결과를 새 리스너로 재전달하거나 상태 이벤트 자체를 역순으로 보내는 경우는 판별할 수 없다. SDK 보장 또는 추가 식별 수단 확인이 필요하다.
- 지연 콜백 순서는 순수 Java 세대·상태 검사로 재현했다. 실제 SDK에 임의 지연·역순 응답을 주입한 시험이나 실제 차량 주행 시험은 하지 않았다.
- 모의 그룹·초대 코드는 실제 인증·권한·다중 기기 연결이 아니다. 모의 전달·확인은 실제 상대 수신 증거가 아니다.
- 공식 앱 중계 API·차량 내장 데이터망 사용 범위·백그라운드 연결은 미확인이다.
- 공유 모델의 순서 번호는 동일 송신 수명 기준이다. 실제 연동에는 송신 세션 ID, 서버 수신 시각·시계 오차, 권한·초대 만료·접근 취소·데이터 삭제 정책이 추가로 필요하다.

### 다음 작업

1. 공식 앱 중계 SDK 제공 여부와 차량 앱의 직접 인터넷 사용 조건을 운영 측에 확인한다.
2. 지원되는 통신 방식이 정해지면 실제 SharingTransport 어댑터와 인증·그룹 권한을 구현한다.
3. 명시적 공유 시작 뒤 유효한 실제 내 차량 정보만 보내도록 수집 수명주기를 연결한다.
4. 두 에뮬레이터 간 실제 송수신·재연결·권한 격리를 검증한다. 이후 실차 검증은 별도 단계다.

## 작업별 커밋 정리

이전 절의 ‘커밋하지 않았다’는 구현 완료 당시 기록이다. 이후 사용자의 명시적 요청에 따라 다음 작업 단위로 로컬 main에 커밋했다.

- `dc2fb70` — 실제 Pleos 위치·목적지·상세주소·ETA 연동 및 늦은 응답 방어, 관련 상태 테스트.
- `7349db9` — 교체 가능한 공유 통신 계약·상태와 Debug 모의 그룹·차량·메시지·장애 시연, 관련 테스트와 실행 스크립트.
- `docs: record journey verification and refresh demo APK` — README·설계·검증·작업 기록과 최신 설치용 Debug APK를 함께 정리한 문서·산출물 커밋.

커밋 과정에서 코드의 의미를 바꾸는 수정은 하지 않았으며 일부 파일 끝의 불필요한 빈 줄만 정리했다. 앞서 통과한 91개 검사와 Debug·Release 빌드 결과는 위 구현 검증 절을 따른다. 커밋을 위해 빌드·에뮬레이터 시험을 다시 실행하지는 않았다. 원격 푸시는 하지 않았다.

## 2026-11-04 — 제출용 Release 범위와 작업 가이드라인 수립

사용자 요청으로 `docs/submission-release-guidelines.md`를 작성했다. 공식 제출 안내를 재확인하고 공식 요구와 프로젝트 자체 품질 기준을 구분했다.

- 1차 제출 범위는 실제 Pleos 정보 화면 + 명시적으로 구분된 정차 전용 모의 공유 시연이다. SDK 진단·강제 경로 시험·기존 알림 시험은 제외한다.
- 현재 Debug 전용 모의 기능을 제출 Release로 자동 포함시키지 않는다. 후속 작업에서 submissionRelease / standardRelease 구분과 전용 모의 소스셋을 적용할 계획으로 기록했다.
- 주행·정차·상태 미확인에 따른 조작 제한, 서명 키 관리·누락 시 빌드 실패·서명/설치 검증, apk 폴더·최종 팀명 기반 파일명·버전·해시 기록 기준을 정했다.
- 실제 팀명, 실제 키 보관 위치·alias·비밀번호는 미정이다. 이번 작업에서 키나 APK를 생성하지 않았다.
- 저장소 루트 `AGENTS.md`에 작업 전 가이드라인 참조 규칙을 추가하고 README에 링크를 연결했다. 부모 `D:\projects\AGENTS.md`는 변경하지 않았다.
- 문서·작업 규칙만 변경했다. 앱·Gradle·권한·서명 설정은 변경하지 않았고 빌드·에뮬레이터 시험은 실행하지 않았다. 커밋·푸시하지 않았다.
- 검증: 문서 링크 대상 존재와 Git 변경 범위·공백 검사 확인. 현재 구현이 새 제출 기준을 모두 충족했다는 의미는 아니다.

공식 출처: https://appdevchallenge.auto-hmg.io/guide/submission 및 https://appdevchallenge.auto-hmg.io/guide/judging-awards (2026-11-04 확인).

## 2026-11-04 — 일반·제출 Release 분리와 모의 기능 소스셋 구성

### 구현

- `app/build.gradle`: distribution flavor로 standard/submission을 정의해 standardDebug·submissionDebug·standardRelease·submissionRelease 네 변형을 구성했다. applicationId·SDK 버전·기존 내비 기능은 유지했다. 두 Release는 debuggable=false다.
- 모의 포함 설정은 `BuildConfig.HAS_SHARING_DEMO`로 분리했다. Debug는 모두 모의를 포함하며, Release는 submission만 포함한다. DEBUG=true로 Release를 위장하지 않는다.
- `MockSharingTransport.java`, `SharingDemoActivity.java`를 src/debug에서 **src/demo**로 이동했다. 별도 demo 매니페스트의 Activity는 exported=false이며 DUMP 권한이 없다. src/debug 전체를 제출용에 포함하지 않아 SDK 진단·강제 경로 시험·기존 알림 시험과 NAVI_ROUTE_SEARCH 권한은 Release에서 제외된다.
- MainActivity에 명시적인 모의 안내를 추가했다. Debug에서만 안내를 확인하고 개발용 시연을 실행할 수 있다. 실제 정차 상태 SDK를 아직 연결하지 않았으므로 submissionRelease는 시연 안내에서 실행을 잠그며, Activity 자체도 같은 조건으로 모의 엔진을 시작하지 않는다. 이는 안전 기능 완료가 아니라 미확인 상태에서의 임시 차단이다.
- 모의 화면 재시작 시 메시지 렌더 캐시를 초기화해 종료·복귀 후 빈 메시지 상태가 올바르게 그려지게 했다.
- 서명 설정은 만들지 않았다. `verifyReleaseDistributionConfigured`는 의도적으로 Release APK/AAB 패키징을 차단한다. 후속 서명·안전 검증 단계에서 실제 준비 상태 검사로 교체해야 한다. unsigned나 Debug 서명으로 대체하는 옵션은 없다.
- `scripts/test-state.ps1`의 이동된 모의 소스 경로를 갱신하고 `scripts/verify-build-variants.ps1`에 변형별 컴파일·매니페스트·클래스·플래그 검사 56개를 추가했다.
- README, 통신 설계, 제출 가이드라인을 현재 구성·잠김 상태·검증 명령에 맞춰 갱신했다. 앞서 미커밋이던 가이드라인·작업 규칙은 보존했다. 저장소 밖의 로컬 빌드 스크립트는 수정하지 않았다.

### 검증

- 독립 Java 상태 검사 **91개 통과**.
- 네 변형 모두 Java 컴파일·리소스 처리·매니페스트 병합 성공. assembleRelease/package/bundle은 실행하지 않았다.
- 생성된 변형별 클래스·매니페스트·BuildConfig에 대한 **56개 구성 검사 통과**. standardRelease 모의 제외, submissionRelease 모의 포함, Release 진단 도구 제외, 동일 applicationId, Release 비디버깅, 비공개 모의 Activity, 새 INTERNET 권한 없음 확인.
- `verifyReleaseDistributionConfigured` 단독 실행은 `RELEASE_DISTRIBUTION_NOT_CONFIGURED`로 **의도한 실패**를 확인했다. 컴파일 실패와 구분한다.
- 두 Release의 APK/AAB 작업 그래프를 --dry-run으로 확인해 패키징 전에 차단 작업이 실행되도록 연결된 것을 검증했다. 실제 패키징은 하지 않았다.
- 기존 app/build/outputs/apk 파일 목록과 SHA-256을 전후 비교해 **APK 생성·변경 없음**을 확인했다. artifacts의 이전 개발용 APK도 갱신하지 않았다. 서명 키·서명 설정·apk/ 제출 파일을 만들지 않았다.
- 이번 검증은 컴파일·구성 검증이다. 새 APK 설치·에뮬레이터 런타임·실차 주행·서명 검증은 하지 않았다. 이전 작업의 런타임 결과를 이번 변형 검증으로 대체하지 않는다.
- 기존 Java 8 source/target·deprecated API 경고는 남아 있다. 커밋·푸시하지 않았다.

검증 명령:

```powershell
.\scripts\test-state.ps1 -JdkHome 'C:\Program Files\Amazon Corretto\jdk21.0.10_7'
.\scripts\verify-build-variants.ps1
# 이미 컴파일한 결과만 검사할 때
.\scripts\verify-build-variants.ps1 -SkipCompile
```

### 다음 작업

실제 차량 상태 SDK로 정차·주행·상태 미확인을 판별하고 시연 조작 제한을 연결한다. 이 단계 전에는 submissionRelease의 모의 실행 잠금을 해제하지 않는다. 이후 사용자가 팀명과 키 관리 방법을 정한 뒤 서명 준비 검사·제출 산출물 생성·실제 설치 검증을 진행한다.
