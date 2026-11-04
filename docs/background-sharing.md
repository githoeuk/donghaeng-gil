# 내비 화면에서도 유지되는 실제 공유

2026-11-04. 사용자가 동행길 화면에서 정차 확인·위치 전송 동의를 거쳐 시작한 공유를 location Foreground Service로 유지한다. 모의 공유에는 적용하지 않는다.

## 확인한 플랫폼 조건

- [Pleos 앱 구조 및 동작 모델](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model)은 앱 화면이 사라지는 것과 프로세스 종료를 구분하고, 지속 통신의 실행·중단 정책과 리소스 절약을 설계하도록 안내한다. 모든 앱의 무제한 상주를 승인한다는 뜻은 아니다.
- [Android 위치 FGS 유형](https://developer.android.com/develop/background-work/services/fgs/service-types#location)은 위치 공유를 location 서비스 용도로 설명한다. FOREGROUND_SERVICE/FOREGROUND_SERVICE_LOCATION과 위치 권한·위치 설정이 필요하며, 사용 중 위치 권한으로 시작할 때는 앱이 보이는 상태여야 한다. [시작 절차](https://developer.android.com/develop/background-work/services/fgs/launch)에 따라 화면에서 startForegroundService를 요청하고 서비스가 즉시 startForeground를 호출한다.
- [Pleos FusedLocation 권한 문서](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions)는 Android의 포그라운드/백그라운드 위치 정책을 SDK가 별도로 보장하지 않는다고 명시한다. 이번 구현은 기존 Android LocationManager와 NaviHelper 3.0.5를 유지한다. 별도의 FusedLocation SDK로 바꾸지 않았다.
- 확인 범위는 Pleos Connect v2.1.0/API34 에뮬레이터다. 실차의 앱 종료·절전·차량 시동/사용자 전환 정책, App Market 심사 허용·배포 권한은 별도 검증 대상이다.

## 수명주기

SharingService가 실제 PleosJourneySource, JourneyState, HttpSharingTransport, 동의 수명을 소유한다. GroupSharingActivity는 바인딩으로 표시·정차 조작만 담당하고, 화면 이탈 시 UI 타이머/차량 조작 판별과 바인딩을 정리한다. 동의한 서비스의 SDK 수집과 통신은 종료하지 않는다. 복귀 시 같은 서비스의 그룹·토큰·순서·메시지를 다시 표시하며 새 그룹이나 동의를 자동 생성하지 않는다.

1. 화면에서 서버 연결/그룹 참여. 이 단계는 바인딩 서비스이며 위치 수집·FGS는 시작하지 않는다. 공유 시작 전 화면을 벗어나면 연결을 닫는다.
2. 정차 상태에서 공유 시작 → 필요하면 알림 권한 요청 → 다시 공유 시작 → 다른 앱에서도 전송된다는 동의. 위치/알림 설정·그룹 연결·정차를 재확인한다.
3. 유효한 동의 세대에 대해서만 location FGS를 시작하고 지속 알림을 표시한 뒤 SDK 수집·서버 공유를 시작한다. 중복 시작·3초 넘게 지연된 시작·취소 후 시작은 거절한다.
4. 내비 등 다른 앱으로 이동해도 유지한다. 화면 갱신·조작용 센서는 정리하고 서버 수신 간격은 약 1초에서 5초로 낮춘다. 최신 상태 송신·내비 조회는 약 5초, 유효 위치만 전송한다. 메시지 앱 수신 확인과 사용자 확인은 계속 구분한다. 백그라운드에서 팝업·음성·주의 분산 메시지를 추가하지 않는다.
5. 화면의 공유 중지/탈퇴 또는 지속 알림의 공유 중지로 SDK 수집·업로드·FGS 알림을 중지한다. 화면이 없으면 그룹 연결도 닫는다. 화면이 있으면 중지 상태의 그룹 연결만 유지할 수 있다.
6. 위치 권한/위치 설정 또는 알림 권한·채널이 꺼지면 중지한다. 시작 응답 10초 초과, 연결 오류 60초 초과, 동의 세션 2시간 경과도 중지하며 사용자가 다시 시작해야 한다.
7. START_NOT_STICKY와 stopWithTask=true를 사용한다. 작업 제거·OS 종료·강제 중지·재부팅 뒤 자동 공유 재시작/자동 로그인은 없다. 프로세스 강제 종료에서는 HTTP 탈퇴 완료를 보장할 수 없어 서버의 15초 지연 표시·60초 무응답 제거를 사용한다. 토큰·좌표·동의를 디스크에 복원하지 않는다.

항상 위치 접근(ACCESS_BACKGROUND_LOCATION), 부팅 수신기, 절전 예외, 영구 WakeLock은 추가하지 않는다. 플랫폼상 FGS에 알림 권한 거부 예외가 있더라도 이 앱은 사용자가 알림을 확인하고 중지할 수 있도록 알림 허용을 시작 조건으로 삼는다. Doze·화면 꺼짐·시동 종료에도 5초 주기가 보장된다는 의미는 아니다.

## 확인 방법

1. 로컬 중계 실행 후 정차한 Pleos 에뮬레이터에서 실제 그룹을 만들고 상대 기기 또는 독립 HTTP 클라이언트를 참여시킨다.
2. 공유 전에는 위치가 없고, 동의 후 지속 알림이 나타나는지 확인한다. 실제 내비 경로 안내와 유효 GPS를 준비한다.
3. 내비 앱을 열고 dumpsys activity activities에서 동행길 화면들이 STOPPED/비표시인지, dumpsys activity services io.demo.companion에서 SharingService가 isForeground=true/types=location(0x8)인지 확인한다. 분할 화면에서 앱이 계속 보이는 경우와 구분한다.
4. 내비가 보이는 동안 GPS를 바꾸고 상대가 새 좌표·증가 순서·유효 ETA를 계속 받는지 15초 신선도보다 충분히 길게 확인한다. 에뮬레이터 GPS와 SDK 진단 경로는 시험 입력임을 기록한다.
5. 돌아왔을 때 같은 그룹·공유 상태를 확인하고 내비로 다시 전환한다. 알림의 공유 중지를 누르면 서비스/알림이 없어지고 상대의 해당 차량 위치가 제거되는지 확인한다.
6. 다시 들어와 자동 공유가 재개되지 않는지 확인한다. 권한·위치 설정 해제와 연결 오류도 시험한다. 실제 결과·증거는 work-log를 따른다.
