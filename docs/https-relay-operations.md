# HTTPS 중계 배포와 운영

2026-11-06. 공모전 검증용 단일 메모리 중계를 HTTPS 뒤에 배치하는 구성이다. 앱의 SharingTransport/API 계약은 바꾸지 않는다. 계정·차량 소유 인증과 영구 저장은 아직 없다. 온라인 서버·도메인은 미정이며 Windows 네이티브 Caddy/Node와 Docker 컨테이너 양쪽에서 로컬 HTTPS를 검증했다. 공인 인증서 발급·외부 네트워크 검증은 별도다.

## 구성과 수명

`앱 → HTTPS:443 Caddy → 내부 HTTP:8787 Node 한 프로세스`다. Compose의 relay는 호스트 포트를 열지 않고 내부 네트워크에만 연결한다. Caddy만 80/443을 연다. 80은 인증서 발급·갱신 및 HTTPS 리다이렉트용이며 앱에는 반드시 HTTPS URL을 입력한다. 기본 내비 지도 미표시 문제와는 별개다.

- Node 24/비관리자 node 사용자, 읽기 전용 파일시스템, 권한 제거, healthcheck를 사용한다. Caddy는 버전2.11.9를 지정하고 인증서 저장소를 Docker volume에 보존한다. 실제 배포 전 이미지 pull/build 및 digest를 기록한다. Node24 태그의 패치 갱신은 재검증한다.
- 인스턴스는 반드시1개다. replica 추가·오토스케일·여러 Node worker를 쓰면 그룹 메모리가 달라진다. DB가 없는 현재 구성에서는 롤링 업데이트도 세션 보존을 보장하지 않는다.
- Node 종료/재시작/재배포 시 모든 세션·그룹·위치·메시지가 사라진다. 사용자에게 재연결·새 그룹 생성/참여 승인·각자 공유 동의가 필요하다고 안내한다. 인증서 volume을 보존해도 그룹 RAM은 복원되지 않는다.
- 그룹장60초 유휴/탈퇴는 그룹 전체 종료, 일반 구성원60초 유휴는 해당 구성원 제거다. 앱은 장기 실패 시 공유 서비스를 멈추며 자동으로 다시 동의하지 않는다. 두 에뮬레이터의 로컬 HTTP 검증은 work-log를 따른다.
- 세션100개/그룹50개/그룹당8대 등 기존 한도는 유지한다. 역방향 프록시 뒤 세션 발급30회/분 제한은 프록시의 공통 연결 IP에 묶인다. 인터넷 서비스 규모의 인증·남용 방어를 완료했다는 뜻은 아니다.

## 서버 준비와 시작

Linux Docker Engine와 Compose가 동작하는 서버, 소유한 DNS 호스트명, 운영 이메일이 필요하다. DNS A/AAAA를 실제 서버로 연결하고 제공하지 않는 IPv6의 AAAA는 등록하지 않는다. 방화벽은80/443과 관리용 접속만 허용하고8787은 외부에 열지 않는다. 기존80/443 서비스가 있으면 충돌을 먼저 해결한다. 아래 명령은 저장소 루트에서 실행한다.

```sh
cp relay/deploy/.env.example relay/deploy/.env
# .env의 RELAY_DOMAIN과 ACME_EMAIL을 실제 값으로 편집한다.
docker compose --env-file relay/deploy/.env -f relay/deploy/compose.yaml config --quiet
docker compose --env-file relay/deploy/.env -f relay/deploy/compose.yaml build relay
docker compose --env-file relay/deploy/.env -f relay/deploy/compose.yaml run --rm --no-deps caddy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker compose --env-file relay/deploy/.env -f relay/deploy/compose.yaml up -d
docker compose --env-file relay/deploy/.env -f relay/deploy/compose.yaml ps
```

Caddy가 공인 인증서를 발급·갱신한다. `.env`는 Git 제외다. 서버 TLS 개인 키는 Caddy data volume에만 두고 저장소에 복사하지 않는다. APK 서명 키와 서버 TLS 키는 별개다. 이번 작업에서는 APK Release 서명 키를 만들지 않는다.

## 상태 확인과 로그

```sh
# Node22 이상, 기본 시스템 CA 검증을 그대로 사용한다.
node relay/deploy/smoke.mjs --url https://실제호스트명
# 컨테이너 내부의 개인정보 없는 healthcheck
docker compose --env-file relay/deploy/.env -f relay/deploy/compose.yaml exec relay node healthcheck.mjs
# 최근 실행 상태와 TLS 발급/갱신 오류 확인
docker compose --env-file relay/deploy/.env -f relay/deploy/compose.yaml logs --tail 50 relay caddy
```

`GET /health`가200이며 `ok:true/apiVersion:2`여야 한다. 외부 HTTPS health를 별도로 감시한다. 컨테이너 healthcheck만으로 DNS·인증서·프록시를 검증하지 않는다. Compose는 relay가 healthy가 된 뒤 Caddy를 시작한다. `restart: unless-stopped`는 프로세스 종료에 대한 정책이며 unhealthy 표시만으로 자동 재시작해 주는 것은 아니다.

smoke는 합성 시험 차량2개를 생성해 승인·개별 동의·상태·메시지 확인·종료를 실제 API로 검사하고 정리한다. 실차 SDK 시험은 아니다. 토큰·위치 본문은 출력하지 않는다. 공인 서버에서는 `--ca`를 생략한다. `--ca`는 로컬 사설 CA의 부정/긍정 검사용이며 인증서 검증을 끄는 옵션은 없다.

공통 proxy.caddy가 경로·16KB 본문·연결/응답 대기 시간을 제한하고 인증 헤더를 정상 전달한다. 요청 access log는 켜지 않는다. 요청 메타데이터가 포함될 수 있는 `http.log.error` 런타임 로그도 제외한다. TLS 수명 로그와 Node 시작 로그만 사용하고 debug/log_credentials/본문 덤프를 켜지 않는다. Docker 실행 로그는5MB×2개로 회전한다. 장애 진단에는 시각·HTTP 상태·health와 서비스 유무만 공유한다.

## 업데이트·복구·종료

시연 중 업데이트를 피하고 참가자에게 공유 종료를 안내한 뒤 실행한다. 업데이트 전 현재 소스 커밋·이미지 digest를 기록하고 필요하면 이전 이미지를 별도 태그로 보관한다.

```sh
docker image tag donghaeng-relay:local donghaeng-relay:rollback
docker compose --env-file relay/deploy/.env -f relay/deploy/compose.yaml build relay
docker compose --env-file relay/deploy/.env -f relay/deploy/compose.yaml up -d --no-deps relay
# 실패 시 이전 이미지로 복귀; 그룹 메모리는 복원되지 않는다.
RELAY_IMAGE=donghaeng-relay:rollback docker compose --env-file relay/deploy/.env -f relay/deploy/compose.yaml up -d --no-deps --no-build relay
# 전체 종료. 인증서 volume은 유지한다.
docker compose --env-file relay/deploy/.env -f relay/deploy/compose.yaml down
```

Caddy 설정 수정도 validate 후 Caddy만 재시작한다. admin API는 꺼져 있으므로 CLI reload는 사용하지 않는다. relay 재시작과 다르게 Caddy만 재시작하면 Node RAM은 유지되지만 앱의 실패 시간 제한을 넘으면 재동의가 필요하다. `down -v`는 인증서 volume까지 삭제하므로 일반 종료에 사용하지 않는다.

health 실패는 Node 상태→Caddy HTTPS health→DNS/인증서/외부80·443 순서로 확인한다.502는 upstream 상태를 확인하고,401/그룹 소실은 서버 재시작 여부를 확인한다. 인증서 오류를 TrustAll·hostname 검증 해제·HTTP 전환으로 해결하지 않는다. 인증서 volume은 서버 비밀정보로 관리하며 OS/계정 접근을 제한한다.

## Windows 로컬 HTTPS 검증

이 경로는 외부 서버를 만들거나 OS/에뮬레이터 CA 저장소를 바꾸지 않는다. `127.0.0.1:8443`의 Caddy가 `127.0.0.1:8787`로 전달하며 두 에뮬레이터는 `https://10.0.2.2:8443`을 사용한다. 로컬·온라인이 같은 proxy.caddy를 공유한다. Windows용 Caddy2.11.9는 [공식 릴리스](https://github.com/caddyserver/caddy/releases/tag/v2.11.9)의 SHA512 checksums와 대조한 파일을 D:\projects\tools\caddy-2.11.9에 준비했다. OpenSSL은 기존 Git 설치, Node는 D:\node.exe를 사용한다. 다른 PC는 스크립트 매개변수로 경로를 지정한다.

```powershell
Set-Location 'D:\projects\donghaeng-gil'
.\scripts\start-local-https.ps1
& D:\node.exe relay/deploy/smoke.mjs --url https://127.0.0.1:8443 --ca .gradle/local-https/root.crt
& 'D:\projects\build-donghaeng.ps1' -Tasks @('assembleStandardDebug','-PdonghaengLocalTlsCa=D:/projects/donghaeng-gil/.gradle/local-https/root.crt')
```

시작 스크립트가7일 유효한 시험 TLS CA/서버 인증서를 Git 제외 `.gradle/local-https`에 만든다. 개인 키는 거기에만 있고 APK에는 공개 CA 한 개만 들어간다. 인증서를 갱신하려면 먼저 종료하고 `start-local-https.ps1 -RenewCertificates` 후 시험 Debug도 다시 빌드한다. 이것은 APK 서명 키 생성이 아니다.

`donghaengLocalTlsCa`를 명시한 빌드만 Debug의 정확한 세 호스트10.0.2.2/127.0.0.1/localhost에 CA를 추가한다. 다른 HTTPS 호스트는 시스템 CA만 신뢰하고 hostname 검증을 유지한다. 일반 Debug·두 Release에는 CA 리소스와 추가 신뢰가 없다. 속성을 줘도 Release 소스셋에는 포함되지 않는다. 인증서 입력에 개인 키·CA가 아닌 인증서가 있으면 빌드를 거절한다. 속성을 gradle.properties에 저장하지 않는다.

이번 환경에서는 화면 표시/입력 대상이 어긋나 실행 인자 `-feature -Vulkan`을 추가한 재부팅으로 화면 시험을 진행했다. AVD config.ini는 수정하지 않았다. 같은 현상이 있으면 기존 실행을 종료한 뒤 두 실행 명령에 해당 인자를 추가한다. 이 조치를 내비 배경 지도 문제 해결로 설명하지 않는다.

두 기기 실행은 [가이드](two-emulator-guide.md)를 따른다. 시험 APK를 양쪽에 `adb -s 기기번호 install -r`로 설치하고 내비 초기화/국내 시험 GPS·목적지를 준비한 뒤, 동일 HTTPS URL로 연결한다. A 생성→B 요청→A 승인→각자 공유 동의→서로 위치/ETA·메시지→공유 중지/그룹장 탈퇴를 확인한다. 기기 시각이 인증서 유효 기간 안인지도 확인한다. 시험 종료 후 다음을 실행하고 일반 Debug를 두 기기에 다시 설치한다.

```powershell
.\scripts\stop-local-https.ps1
& 'D:\projects\build-donghaeng.ps1' -Tasks @('assembleStandardDebug')
# 위 일반 Debug APK를 두 기기에 install -r. 시험용 CA가 없는 기본 빌드로 돌아간다.
```

이번 작업의 실제 통과/미검증 범위와 정리 결과는 [work-log](work-log.md)에 기록한다. 로컬 사설 인증서로 성공해도 공인 인증서 발급·다른 네트워크 검증 완료로 설명하지 않는다. Docker 실동작은 아래 별도 구성으로 검증했다.

근거: [Caddy 역방향 프록시](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy), [Caddy 로그](https://caddyserver.com/docs/caddyfile/directives/log), [Compose 시작 순서](https://docs.docker.com/compose/how-tos/startup-order/), [Android 네트워크 보안](https://developer.android.com/privacy-and-security/security-config).

## Docker 로컬 HTTPS 확인

Docker 엔진이 정상 실행된 환경에서 아래 순서를 사용한다. `compose.local.yaml`은 기본 배포 구성을 합치되80/443을 대체해 호스트127.0.0.1:8443만 연다. `!override`를 사용하므로 Compose2.24.4 이상이 필요하다. Caddy는 컨테이너 안에서만0.0.0.0:8443을 수신한다. 서버 개인 키/인증서만 읽기 전용으로 넣고 root.key는 컨테이너에도 넣지 않는다. 온라인 DNS/ACME 요청은 하지 않는다.

```powershell
Set-Location 'D:\projects\donghaeng-gil'
.\scripts\start-local-https.ps1 -CertificatesOnly
$composeArgs = @('-p','donghaeng-relay-check','--env-file','relay/deploy/local.env.example',
  '-f','relay/deploy/compose.yaml','-f','relay/deploy/compose.local.yaml')
docker compose @composeArgs config --quiet
docker compose @composeArgs up -d --build --wait
& D:\node.exe relay/deploy/smoke.mjs --url https://127.0.0.1:8443 --ca .gradle/local-https/root.crt
docker compose @composeArgs exec -T relay node healthcheck.mjs
docker compose @composeArgs ps
docker compose @composeArgs down
```

호스트의 네이티브 HTTPS 프로세스가8443을 사용 중이면 먼저 stop-local-https.ps1로 종료한다. `donghaeng-relay-check`라는 별도 프로젝트만 생성/종료하며 다른 컨테이너를 정리하거나 전체 Docker prune을 하지 않는다. 실제 업데이트/컨테이너 확인 결과는 work-log를 따른다.

### 두 Android 앱으로 Docker HTTPS를 검증할 때

2026-11-06에 두 Pleos 에뮬레이터에서 위 Docker 중계로 참여 요청·승인·각자 동의·양방향 SDK ETA·양방향 메시지 확인·그룹 종료를 확인했다. 내비를 먼저 한 번 실행하고 국내 시험 GPS가 반영된 뒤 경로 안내를 시작한다. 경로 요청 직후의 빈 목적지/SAFE_GUIDING 응답만으로 실패나 성공을 판정하지 않고, 양쪽 내비 안내 화면과 동행길의 유효한 목적지·ETA를 확인한 뒤 공유한다. 내비의 경로 재개 확인창이 있으면 먼저 처리한다. 실제 결과·증거·한계는 work-log의 해당 날짜 기록을 따른다.

실시간 갱신 화면에서는 uiautomator idle 수집이 실패할 수 있다. 이전 XML을 성공 증거로 재사용하지 않고 최신 스크린샷·공유 FGS 상태·종료 상태의 새 XML로 확인한다. 시험 후 양쪽 경로 종료, GPS 복원, 일반 Debug 재설치와 시험 Compose 프로젝트 종료를 수행한다.
