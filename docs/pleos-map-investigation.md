# Pleos 기본 내비 배경 지도 미표시 조사

2026-11-04. 상태: **공개 스타일·해안선 타일의 실제 수신 정상 확인, 도로 지도 표시 복구 미완료**. 동행길 앱 소스는 변경하지 않았다.

## 확인된 사실

| 항목 | 확인 결과 |
| --- | --- |
| 호스트 GPU | NVIDIA GeForce RTX 3060, 드라이버 610.88 |
| AVD | Pleos Connect v2.1.0 / API34 / x86_64 / 차량 사용자10 |
| 기본 내비 | ai.umos.maps.android.navigation.app, 3.3.2-rc5-hotfix.3 |
| 지도 라이브러리 로그 | Mapbox Maps 11.21.7 / Common 24.21.7 |
| 화면 | 위치 아이콘·내비 UI는 표시되지만 도로·건물·지명 배경이 비어 있음 |
| 내비 준비 상태 | gateway probe passed, 인증 요청 200 OK, AuthComplete·MapComplete 기록 |
| 그래픽 비교 | 원래 auto→SwiftShader, 실행 옵션 -gpu host→NVIDIA 실제 사용. 둘 다 배경 미표시 |
| 새 AVD | 동행길 미설치·새 사용자 데이터에서도 동일 증상 |
| 최신 배포 비교 | 공식 목록 revision2 다운로드·체크섬 검증. 설치본과 system/userdata/vendor/ramdisk 이미지가 동일 |

## 수행한 조치

1. 원래 AVD를 Cold Boot하고 기본 내비를 실행해 증상을 재현했다. 내비만 강제 종료·재실행해도 해결되지 않았다.
2. 내비 프로세스에 한정해 네트워크·지도 초기화 로그를 조사했다. Mapbox의 레이어 미존재, runtime style 갱신 무시 경고가 보인다. 이 경고만으로 배경 지도의 직접 원인을 확정할 수 없다. 경로·POI 등 동적 레이어를 아직 추가하기 전에도 유사 경고가 날 수 있다.
3. HTTPExporter 403은 다른 프로세스의 로그였으며 지도 실패의 증거로 사용하지 않았다. Mapbox telemetry missing token도 지도 타일 인증 실패로 단정하지 않았다. POI 상세 조회의 Not Found 역시 배경 지도 문제와 분리했다.
4. connect-gw-coral.umos.ai와 a.tile.static.maps-coral.umos.ai의 호스트에 HTTPS 연결은 가능했다. 루트 경로의 404 응답은 해당 호스트의 실제 스타일/타일 URL 정상 여부를 증명하지 않는다. 내비 준비 로그의 성공도 모든 지도 리소스 로딩 성공을 뜻하지 않는다.
5. CLI의 -gpu host 옵션으로 NVIDIA 렌더러를 실제 사용했지만 해결되지 않았다. 기존 AVD config.ini의 hw.gpu.mode=auto는 수정하지 않았다. 지도 보기 버튼 조작 후에도 복구되지 않았으며 최종 원래 AVD 화면의 표시는 2D였다.
6. D:\projects\.tmp-donghaeng\map-check.avd에 별도 DonghaengMapCheck AVD를 만들고 새 데이터·Cold Boot·NVIDIA GPU로 비교했다. 동행길을 설치하지 않은 환경에서도 배경이 없었다. 원래 AVD의 데이터·목적지·계정은 초기화하지 않았다. 기본 내비의 APK/사적 파일을 읽으려는 ADB 요청은 권한 거부되어 중단했으며 root나 파일 접근 우회는 하지 않았다.
7. 공식 Windows/Linux SDK 배포 목록의 revision2를 발견해 별도 경로로 내려받았다. ZIP은 4,087,751,964바이트, 공식 SHA-1 b4f858d5bcc6c71506d7edfc872bcb9e5a11acaa와 일치했다. ZIP 내부 source.properties는 여전히 Revision1이었다. system.img SHA-256은 설치본과 새 배포본 모두 CDB8F0D23043EB5A42CFE0EC7CD2105D5A92A19C8D2BEEE3FAD15C0992E19769이며 userdata.img/vendor.img/ramdisk.img/encryptionkey.img/advancedFeatures.ini도 동일했다. 새 배포본을 -sysdir로 지정한 별도 AVD에서도 동일 내비 버전·미표시가 확인되어 기존 C: SDK를 덮어쓰지 않았다.
8. 확인 후 두 에뮬레이터를 종료했다. 이번에 만든 테스트 AVD와 약 4GB ZIP·압축 해제본은 정확한 테스트 경로를 검증한 뒤 제거했다. 원래 AVD와 SDK 파일, 동행길 설치 앱·코드는 유지했다.

## 결론과 남은 확인

이 증상은 동행길이 설치되지 않은 기본 내비에서도 발생하므로 동행길 코드 영향이나 기존 앱 데이터 손상만으로 설명되지 않는다. GPU 선택만으로도 해결되지 않았다. 현재 PC/네트워크와 제공 이미지 조합에서 기본 내비의 배경 지도 리소스·스타일 처리 경로를 추가로 확인해야 한다.

**정확한 타일/스타일 요청의 실패 원인(서비스 제공 제한·인증/설정·리소스 응답·기본 내비 내부 오류 중 무엇인지)은 아직 확정하지 못했다.** 다른 네트워크/PC에서의 교차 검증과 공급 측의 지도 리소스·기본 내비 로그 분석이 다음 단계다. 플랫폼 관리 인증 정보를 바꾸거나 기본 내비를 임의로 패치하지 않았다. 동행길에 새 지도 SDK를 넣는 방식으로 이 문제를 가리지 않는다.

위치/ETA SDK 수신과 공유 검증을 배경 지도의 정상 동작 검증으로 해석하지 않는다. 제출 영상에서 지도까지 정상이라고 설명하지 않는다.

## 로컬 증거

아래 파일은 .gradle에 있고 Git 제외 대상이다. 화면은 AVD 시험 데이터이며 공유 전에 내용을 확인한다. 원본 전체 logcat은 앱 내부 인증 정보를 포함할 수 있으므로 그대로 외부에 보내지 않는다.

- map-loaded-before.png: 원래 설정, 초기화 뒤 배경 없음
- map-host-gpu-result.png / map-3d-result.png: NVIDIA GPU 사용 뒤 배경 없음
- map-clean-first.png / map-clean-result.png: 동행길 미설치 새 AVD
- map-r2-result.png / map-r2-status.log: 공식 최신 ZIP 비교 실행
- map-emulator.log / map-host-gpu.log: 실제 렌더러 선택 로그
- map-navigation-sanitized.log: URL 경로와 알려진 인증 필드를 가린 조사 로그. 외부 제공 전 재검토 필요

## 운영 측에 전달할 문의 초안 (아직 전송하지 않음)

제목: Pleos Connect v2.1.0 기본 내비의 배경 지도 미표시 — 새 AVD에서도 재현

Windows, NVIDIA RTX3060 환경의 Pleos Connect v2.1.0/API34 기본 내비(3.3.2-rc5-hotfix.3)에서 현재 위치 아이콘과 경로 안내/ETA UI는 표시되지만 도로·건물·지명 배경이 비어 있습니다. 서드파티 앱을 설치하지 않은 새 AVD, Cold Boot, GPU host에서도 재현됩니다. 네트워크 준비·인증 200 및 AuthComplete/MapComplete 로그는 확인했습니다. 공식 목록 revision2 ZIP도 체크섬을 검증했으나 설치본과 핵심 이미지가 같고 증상도 같습니다.

확인 부탁드릴 사항:

1. 현재 에뮬레이터 기본 내비의 배경 지도 제공 범위와 알려진 제한/장애가 있나요?
2. 배경 지도 스타일·타일 접근에 별도 승인, 네트워크 허용 목록 또는 설정이 필요한가요?
3. 위 기본 내비 버전에 지도 초기화/스타일 로딩 관련 알려진 문제가 있나요? 수정 이미지나 권장 설정이 있나요?
4. 원인 분석에 필요한 로그 태그·재현 절차와 인증 정보를 제외하는 방법을 안내해 주세요.

## 공식 참고 자료

- [설치 가이드](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [AVD 설정·그래픽 요구 조건·Cold Boot](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)
- [Windows/Linux 공식 SDK 목록](https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml)

## 추가 조사 — 실제 리소스 HTTP 응답 확인

같은 날 후속 사용자 요청으로 호스트 루트 접속이 아닌 실제 스타일·타일 리소스를 확인했다. 앞 절의 네트워크/인증 미확정 결론은 아래 범위에서 갱신한다.

### 실제 응답

내비 로그의 readySSLCertificationFlow에 다음 스타일 주소가 있었다. URL에 쿼리·사용자 정보가 없고 공개 정적 리소스임을 확인한 뒤, 인증 헤더/쿠키를 보내지 않고 조회했다.

`https://a.tile.static.maps-coral.umos.ai/styles/42dot/connect_empty`

이 주소는 기본 내비의 로그에 나온 주소이지, 실시간 최종 렌더러의 전체 스타일을 추출한 결과는 아니다. 별도 런타임 도로 레이어가 추가되는지는 직접 읽지 못했다.

Android 기본 HTTPS 스택(HttpURLConnection, 기본 인증서 검증, 타임아웃 10초)을 사용하는 임시 Java CLI를 DEX로 만들어 /data/local/tmp에서 app_process로 실행했다. 앱 설치·동행길 코드 변경·root·인증서 우회 없이 에뮬레이터 내부에서 공개 리소스를 요청했다. 실행 주체는 ADB shell이며 기본 내비 프로세스 자체가 아니므로 내비의 모든 내부 인증·요청 상태까지 증명하지 않는다.

| 에뮬레이터 내부 요청 | HTTP | 수신 본문 | 검사 결과 |
| --- | --- | --- | --- |
| 위 connect_empty 스타일 | 200 | 6,238바이트 JSON | source 1개, layer 4개 |
| 스타일이 참조한 coastline tileset 메타데이터 | 200 | 540바이트 JSON | z5~14, 한국 포함 영역, tile URL 제공 |
| 해당 타일 /5/27/12 | 200 | 60,021바이트 application/x-protobuf | 벡터 타일 파싱 성공, coastline 레이어 |
| 해당 타일 /8/218/99 | 200 | 3,190바이트 application/x-protobuf | 벡터 타일 파싱 성공, coastline 레이어 |
| 현재 위치 부근 /14/13976/6353 | 204 | 0바이트 | 이 해안선 타일 주소에는 본문이 없음. 401/403이나 TLS 실패가 아님 |

타일셋 공개 주소:

`https://a.tile.static.maps-coral.umos.ai/tilesets/002cb125-253b-473e-b630-3315637329d0`

타일 데이터는 protobuf의 layer 메시지를 파싱해 실제 coastline 레이어가 있음을 확인했다. 단순 HTTP 연결/Content-Length만 보고 타일 성공으로 처리하지 않았다. 호스트에서도 공개 스타일과 tileset JSON은 200이었다.

### 지도 스타일 내용

connect_empty는 아래 구성이다.

- 바탕: background
- coastline: fill, source/source-layer 모두 coastline
- driving_carpet: slot
- driving_carvatar: slot
- source는 coastline 벡터 타일셋 하나뿐

도로·건물·지명에 해당하는 source/layer와 style imports는 이 JSON에 없다. 따라서 **이 스타일만으로는 해당 요소를 그릴 수 없다.** Mapbox의 [스타일 설명](https://docs.mapbox.com/map-styles/guides/)과 [Android 런타임 레이어 API](https://docs.mapbox.com/android/maps/guides/styles/work-with-layers/)도 데이터 source와 그리는 layer를 구분하며 실행 중 추가할 수 있다고 설명한다.

### 네트워크·인증 판단과 한계

- Android는 가상 LTE/eth0 및 Wi-Fi/AndroidWifi 모두 VALIDATED/INTERNET 상태였다. 안테나 아이콘 부재를 통신 단절의 근거로 볼 수 없다.
- 에뮬레이터에서 실제 스타일·tileset·해안선 타일을 인증정보 없이 받아 본문 파싱까지 성공했다. **확인한 공개 리소스의 DNS/TCP/TLS 연결 및 접근 인증 거부 문제는 관측되지 않았다.** 요금제 가입이나 임의 토큰 교체가 이 리소스 수신을 위해 필요하지 않았다.
- 이것으로 별도의 도로 데이터 서비스·내비의 사용자/앱별 인증까지 정상이라고 단정하지 않는다. 특정 리소스 수신 성공과 모든 지도 수신 성공을 구분한다.
- -tcpdump로 패킷도 수집했다. 게이트웨이/API 통신은 보였지만 위 CLI에서 확실히 성공한 지도 요청은 분석에 잡히지 않았다. 현재 캡처가 모든 가상 인터페이스의 트래픽을 포괄한다고 검증되지 않았으므로, 지도 DNS/연결이 안 보인다는 이유로 내비가 타일 요청을 안 한다고 결론내리지 않았다. TLS 패킷을 HTTP 상태/타일 본문으로 오해하지 않았다.
- 로그 URL을 무조건 재요청하는 초기 도구 호출은 쿼리 토큰·인증정보 재전송 위험으로 자동 승인 검토에서 거절됐다. 해당 호출은 실행되지 않았다. 이후 URL 메타데이터로 인증정보가 없음을 먼저 확인하고, 공개 정적 URL을 상수로 지정해 인증정보 없는 GET만 승인받아 실행했다. 내비의 client secret/토큰은 재사용하거나 문서에 기록하지 않았다.

### 현재 해결 상태

네트워크 수신 자체의 실패라는 가설은 확인한 해안선 지도 경로에 한해 해소했다. 반면 기본 내비의 도로·건물·지명 표시 복구는 아직 미완료다. **유력한 조사 대상은 connect_empty 선택과 이후 도로 지도 레이어의 구성/제공 여부**다. 이 스타일이 에뮬레이터의 의도된 기본 구성인지, 추가 런타임 구성 실패의 결과인지는 공급 측 확인이 필요하다. 확인되지 않은 다른 스타일 URL을 기본 내비에 강제로 주입하지 않았다.

운영 측 문의에는 다음 사실을 추가한다:

> 기본 내비 로그의 스타일이 /styles/42dot/connect_empty이며, 에뮬레이터 내부에서도 해당 JSON과 coastline 벡터 타일을 HTTP200으로 수신·파싱했습니다. 스타일 JSON에는 background/coastline/slot만 있고 도로·건물·지명 source/layer는 없습니다. 이 이미지가 원래 해안선만 제공하는 구성인지, 추가 도로 렌더러/스타일을 활성화하는 공식 설정 또는 권한이 있는지 확인 부탁드립니다.

### 추가 증거·정리

- .gradle/map-empty-style-probe.json: 호스트의 상태·크기·레이어 유형
- .gradle/map-empty-style-structure.json: 스타일 구조
- .gradle/map-coastline-tilejson.json: 타일셋 범위·주소 템플릿
- .gradle/map-guest-resource-results.jsonl, map-guest-coastline-results.jsonl: 에뮬레이터 HTTP 응답·본문 파싱
- .gradle/map-probe/TileProbe.java: 임시 공개 리소스 진단 코드 (앱 소스가 아니며 Git 제외)
- .gradle/map-network-connectivity.txt, map-network-final.png: 연결 상태와 여전히 빈 기본 내비 화면
- .gradle/map-network.pcap: 로컬 패킷 증거. 전체 네트워크 데이터이므로 그대로 외부에 공유하지 않음

임시 DEX는 에뮬레이터에서 제거했고 에뮬레이터를 종료했다. 위치·목적지·계정·동행길 코드·SDK 설정은 바꾸지 않았다. 문의는 아직 전송하지 않았다.

## 2026-11-05 — Vulkan 무효화 비교 시험

사용자 요청에 따라 기존 AVD 설정은 유지하고 실행 인자 `-feature -Vulkan`만으로 비교했다. 추가로 GPU host 조합도 일회성 실행 인자로 확인했다. 앱 코드·SDK·사용자 데이터를 변경하지 않았다.

| 실행 조합 | 실제 확인 | 지도/화면 결과 |
| --- | --- | --- |
| 기존 gpu=auto + Vulkan 비활성화 | 로그 Feature 'Vulkan' overridden to 'disabled', SwiftShader 렌더러 | AuthComplete·MapComplete까지 진행. 내비 Activity가 RESUMED여도 내비 창이 화면에 제대로 합성되지 않는 부작용. 지도 복구 확인 안 됨 |
| gpu=host + Vulkan 비활성화 | 같은 disabled 로그, NVIDIA RTX3060 렌더러 | 내비 UI·위치 아이콘은 보이지만 도로·건물·지명 없음. 앱 창이 운전 화면 영역까지 덮는 배치 이상도 관찰 |

판단: **현재 Windows/Pleos 이미지/드라이버 조합에서 Vulkan을 끄는 방식으로 지도 배경이 복구되지 않았다.** 자동 렌더링에서는 UI 자체의 합성 이상이 있어 단순히 동일한 흰 배경만 비교한 시험이라고 설명하지 않는다. 이 결과만으로 모든 그래픽 문제를 배제하거나 connect_empty가 최종 원인이라고 확정하지 않는다.

기존 config.ini는 두 시험 전후 SHA-256이 모두 39D54F870FBEE96B87B27055132CAC6D3025E717CCEDCD3ECB152218975A5942로 같았다. hw.gpu.mode=auto, Cold Boot 설정, RAM4GB를 유지했다. -no-snapshot으로 시험 실행의 스냅샷은 저장하지 않았다. hardware-qemu.ini 등 에뮬레이터가 생성하는 런타임 파일과 로그는 실행 과정에서 재생성될 수 있다.

증거: .gradle/map-no-vulkan-boot.log, map-no-vulkan-status.log, map-no-vulkan-result-3.png, map-no-vulkan.xml, map-no-vulkan-host-boot.log, map-no-vulkan-host-status.log, map-no-vulkan-host-result.png, map-no-vulkan-host.xml, map-no-vulkan-config-check.json. 특히 부팅 로그에는 vulkan_mode_selected 값도 남지만 실제 기능 override disabled 행과 실행 인자를 함께 확인했다.

복원 확인: Vulkan/GPU 추가 옵션 없이 다시 Cold Boot했다. 기본 옵션에서도 창 배치 이상이 남아 있어 배치 이상 자체를 Vulkan 비활성화만의 영향으로 확정하지 않는다. 기본 내비 재실행·홈 화면 경유로도 이번 세션에서 원래 배치를 확인하지 못했다. DrivingView의 직접 실행 요청은 플랫폼의 LAUNCH_CLUSTER 권한으로 거절돼 중단했고 권한 우회는 하지 않았다. 기존 config.ini 해시는 마지막까지 동일하며, 임시 옵션을 유지하지 않고 에뮬레이터를 종료했다. 사용자 데이터 초기화나 앱 소스 변경은 하지 않았다. 복원 비교 화면: .gradle/map-vulkan-restored.png, map-vulkan-restored-2.png.

## 문의 진행 상태 (2026-11-05)

사용자가 공식 개발사 지원 이메일 partnership@pleos.ai로 문의를 보냈다고 알려 회신 대기 중이다. 공식 지원 안내: https://github.com/Pleos-Playground/pleos-playground-api . 앞 절의 미전송 표기는 각 조사 시점의 상태이며 현재 상태는 이 절을 따른다. 원본 인증 로그·패킷·화면 ZIP은 Git에 포함하지 않는다.
