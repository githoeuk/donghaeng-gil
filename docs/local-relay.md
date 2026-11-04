# 실제 로컬 중계 / 온라인 이전

2026-11-04. SharingTransport의 HttpSharingTransport가 실제 HTTP API를 사용한다. MockSharingTransport는 기존 모의 화면에만 남는다. 공식 마이현대·Kia App API를 사용하거나 지원을 확인한 구현은 아니다.

## 실행

Node.js 22 이상(검증: D:\node.exe 24.18.0), 외부 패키지 설치 불필요.

```powershell
cd D:\projects\donghaeng-gil
& D:\node.exe relay/server.mjs
# 다른 터미널에서
& D:\node.exe --test relay/server.test.mjs
```

기본 127.0.0.1:8787에서만 수신한다. 에뮬레이터 두 대에서 같은 Debug APK를 실행하고 정차(P, 속도 0, UX 제한 없음)를 확인한다. 처음 부팅한 Pleos에서는 내비 앱을 한 번 실행해 초기화하고 양쪽에서 경로 안내를 시작한다. 메인 → 동행 그룹 → 차량 이름 입력 → 서버 연결. A가 그룹을 만들고 표시된 초대 코드를 B가 입력해 참여한다. 각자 위치 공유 동의 후 공유 시작을 누른다. 주소는 Pleos와 GPS가 일치할 때, ETA는 유효한 내비 안내·정확한 최신 GPS 조건에서만 전송한다. 내비 목적지가 없으면 ETA를 만들지 않는다.

Debug 기본 URL은 http://10.0.2.2:8787 (Android 에뮬레이터의 호스트 별칭). Debug 평문 통신도 10.0.2.2/127.0.0.1/localhost 세 호스트만 허용한다. 실제 장비 LAN HTTP 주소를 허용하지 않는다. Release는 HTTPS만 허용하며 서버 주소를 직접 지정한다. 서버 주소·인증 토큰은 저장하지 않는다.

명시적인 동의 후 location Foreground Service로 SDK 수집·최신 위치 송신 약 5초를 유지한다. 화면 수신은 약 1초, 다른 앱 사용 중에는 약 5초다. 지속 알림의 공유 중지로 종료할 수 있고 화면 복귀 시 같은 그룹에 연결한다. 수명주기·권한·종료 정책은 [백그라운드 공유](background-sharing.md)를 따른다. 정차 중에만 그룹 설정·공유 시작·메시지 조작을 허용한다. 주행/상태 미확인 중 메시지를 숨기고 이미 시작한 공유는 내비 등 다른 앱에서도 유지한다. 공유 중지·탈퇴는 항상 가능하다. 안전 승인 완료를 의미하지 않는다.

## 프로토콜 v1

모든 변경 요청은 JSON, 세션 발급 외에는 Authorization: Bearer 토큰. 토큰·차량 이름·위치·메시지 본문을 서버 로그에 남기지 않는다.

| 요청 | 용도 |
| --- | --- |
| GET /health | 개인정보 없는 상태 확인 |
| POST /v1/sessions | 차량 이름으로 임시 토큰·차량 ID 발급 |
| POST /v1/groups | 초대 그룹 생성 |
| POST /v1/groups/join | 초대 코드로 참여 |
| GET /v1/group | 현재 본인 그룹 스냅샷 |
| POST /v1/sharing | enabled로 공유 시작/중지, 시작마다 새 epoch |
| PUT /v1/state | epoch·증가 sequence·최신 위치/목적지/ETA 또는 available:false |
| POST /v1/messages | UUID·REST/WAIT/GO/OK, 중복 ID 재생성 방지 |
| POST /v1/messages/{id}/received | 상대 앱이 실제 수신했다는 확인 |
| POST /v1/messages/{id}/ack | 상대 사용자의 명시적 확인 |
| POST /v1/groups/leave | 탈퇴 및 본인 위치 제거 |

클라이언트는 본인 세션의 상태만 쓸 수 있고 그룹 밖 메시지에 확인할 수 없다. 공유 중지/재시작 후 이전 epoch의 업로드는 거절한다. 15초 초과 원본은 업로드하지 않는다. 수신 때 원본 나이·오프라인·요청 지연을 반영해 오래된 ETA를 숨긴다. 전달됨은 상대 앱 수신 확인, 확인됨은 사용자 버튼이다. 여러 구성원에서는 1명 이상이 수신/확인했다는 의미이며 전원 확인이 아니다. 네트워크 오류 시 메시지 자동 재발송이나 이동 이력 큐를 만들지 않는다. 서버 재시작 후 재연결·새 그룹·새 동의가 필요하다.

## 저장·운영 경계

단일 프로세스 RAM만 사용한다. 세션 최대 100개/2시간, 그룹 최대 50개/8대, 초대 코드 40비트·생성 후 30분 만료, 그룹 빈 경우 삭제. 15초 무응답은 오프라인, 60초 무응답은 탈퇴·위치 삭제, 상태 보관 최대 60초, 메시지 최대 50건/30분. 마지막 그룹 구성원이 나가거나 서버 종료 시 그룹 데이터가 없어진다. 응답 본문·요청 크기 제한, 요청/초대 시도 제한이 있다. 계정 인증·차량 소유 증명·참여 승인·강제 퇴장 기능은 없다. 초대 코드와 토큰 소지자를 신뢰하는 개발용 모델이다.

## 온라인 배포 준비

서버 코드는 HOST/PORT 환경변수를 받고 relay/Dockerfile을 제공한다. 다음은 향후 배포 예시이며 이번에는 Docker 빌드나 외부 배포를 하지 않았다.

```powershell
docker build -t donghaeng-relay ./relay
docker run --rm -p 127.0.0.1:8787:8787 donghaeng-relay
```

HTTPS 도메인의 역방향 프록시가 이 포트로 전달하게 구성하고 앱의 서버 URL을 해당 HTTPS 루트 주소로 변경한다. 인증 헤더·위치 본문 로깅을 꺼야 한다. 프록시 연결·요청 제한, 인증서 갱신, 서비스 모니터링을 적용한다. Node 포트를 직접 인터넷에 열지 않는다. 현재 메모리 모델은 인스턴스 1개만 지원하며 수평 확장·영구 로그인·장기 보관은 구현하지 않았다. 실서비스 전 계정/차량 인증·참여 승인·회수/탈퇴 권한·개인정보 처리/보관 정책을 확정하고 필요하면 저장소를 교체한다. 클라이언트 모델과 SharingTransport 경계는 그대로 유지할 수 있다.

자료: [에뮬레이터 네트워크](https://developer.android.com/studio/run/emulator-networking), [Android 네트워크 보안 설정](https://developer.android.com/privacy-and-security/security-config), [Node HTTP](https://nodejs.org/api/http.html).
