# GitHub Actions 워크플로우 (.github)

이 디렉토리는 서비스의 CI/CD (지속적 통합 및 배포) 파이프라인 설정을 담고 있습니다. 
주요 파일은 `.github/workflows/deploy.yml` 이며, 백엔드 코드의 변경사항이 메인 브랜치에 푸시되었을 때 **Blue/Green 방식의 무중단 배포**를 수행합니다.

---

## 🔄 전체 배포 파이프라인 개요

이 배포 파이프라인은 크게 4가지 단계(Job)로 자동 실행됩니다:

1. **Tag Calculation (`calculateTag`)**
   - 현재 커밋 기록과 릴리스 내역을 바탕으로 다음 Semantic 버전 태그명(예: `v1.2.3`)을 계산합니다. (실제 태그 생성은 이 단계에서 하지 않고 드라이 런으로 진행)

2. **Docker Build & Push (`buildImageAndPush`)**
   - 백엔드의 핫 소스코드가 담긴 `back/` 디렉토리를 기준으로 Docker 이미지를 빌드합니다.
   - 빌드 속도 향상을 위해 GitHub Actions 캐시(`cache-from`, `cache-to`)를 최대한 활용합니다.
   - 빌드된 이미지는 **GitHub Container Registry (GHCR)** 에 푸시되며, 버전 태그 및 `latest` 태그가 동시에 붙습니다.

3. **Blue/Green 무중단 배포 (`deploy`)**
   - 가장 복잡하고 핵심적인 단계로, **SSM (AWS Systems Manager)** 을 이용해 타겟 EC2의 셸에 직접 원격으로 배포 스크립트를 밀어넣어 격리 실행합니다.
   - 다운타임을 없애기 위해 EC2 내부의 **NPMplus (Nginx Proxy Manager)** 와 연동하여 현재 구동중이지 않은 "Green" 컨테이너 슬롯을 띄운 후, `actuator/health` 헬스체크가 통과(200 OK)하면 NPMplus의 Proxy Host 라우팅 주소를 스위칭합니다.
   - 모든 검증이 정상적으로 통과하여 스위칭이 완료되면 이전의 백엔드 컨테이너("Blue")를 내리고 더미/오래된 이미지를 청소합니다.

4. **Tag & Release 생성 (`makeTagAndRelease`)**
   - 위 3번 배포 프로세스까지 완벽하게 스크립트가 리턴 0 으로 성공하면 비로소 GitHub 릴리즈 노트를 생성하고 공식적으로 버전 태그를 레포지토리에 발행합니다.

---

## 🔐 필요 GitHub Secrets

이 배포를 정상적으로 작동시키기 위해서는 GitHub 리포지토리의 `Settings > Secrets and variables > Actions` 메뉴에서 아래의 비밀키들이 설정되어야 합니다.

- **`AWS_REGION`**: SSM을 구동할 타겟 인프라의 AWS 리전 (예: `ap-northeast-2`)
- **`AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`**: 대상 EC2를 관리하고 SSM 명령을 하달할 수 있는 권한을 가진 AWS IAM 사용자의 자격 증명 (인프라 생성 시 할당됨)
- **`DOT_ENV`**: 실제 운영 DB(`PostgreSQL`) 접근 URI나, JWT 서명 키 등이 포함된 백엔드의 Base64 변환 전 텍스트 원본 `.env` 내용

---

## ⚙️ 상세: Blue / Green 메커니즘 

`deploy` 잡에서 동작하는 블루그린 배포 스크립트(`cat << 'SCRIPT_EOF'`)는 다음 로직을 거칩니다.

- **포트 충돌 회피**: 컨테이너 자체의 로컬 포트를 EC2 호스트에 매핑(`-p 8080:8080`)하지 않고, **도커 커스텀 네트워크(`common`) 내부망**을 활용하므로 하나의 EC2 내에서 동일한 포트 넘버 설정값의 컨테이너를 여러 개 가동할 수 있습니다.
- **NPMplus API 제어**: 스크립트가 시작되면 Nginx Proxy Manager의 관리용 Admin API로 CURL 로그인을 시도한 후 대상 도메인의 프록시 ID를 추출합니다.
- **Health Check 전략**: 새로운 백엔드 엔진(Green) 스핀업 후 루프를 돌며 내부망 IP 기반의 스프링 부트 `http://{컨테이너 IP}:8080/actuator/health` 엔드포인트를 호출하며 정상 기동 완료를 확인하고서야 Nginx의 트래픽을 Green으로 넘겨버립니다.
- **안전 장치**: 실패 시 기존 서비스 중인 도커("Blue")를 건드리지 않은 채로 스크립트는 뻗어버리고 파이프라인은 실패 종료되므로 운영 서비스의 단절을 막습니다.
