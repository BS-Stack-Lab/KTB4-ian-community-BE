# Repository migration record

Migration date: 2026-08-06

## Repository and image mapping

| Component | Archived source | Primary repository | Public image |
| --- | --- | --- | --- |
| Backend | [100-hours-a-week/KTB4-ian-week4](https://github.com/100-hours-a-week/KTB4-ian-week4) | [BS-Stack-Lab/KTB4-ian-community-BE](https://github.com/BS-Stack-Lab/KTB4-ian-community-BE) | `ghcr.io/bs-stack-lab/ktb4-ian-community-be` |
| Frontend | [100-hours-a-week/KTB4-ian-community-FE](https://github.com/100-hours-a-week/KTB4-ian-community-FE) | [BS-Stack-Lab/KTB4-ian-community-FE](https://github.com/BS-Stack-Lab/KTB4-ian-community-FE) | `ghcr.io/bs-stack-lab/ktb4-ian-community-fe` |

The organization repositories were mirrored with all advertised branches and
tags. Local-only work was preserved under `archive/local/*`, and uncommitted
workspace changes were committed to `archive/worktree-20260806`. Pull requests
were intentionally not recreated because the personal repositories use direct
`main` pushes with post-push CI.

## Previous pull requests

### Backend

- [#7 fix(deploy): preserve legacy stack during bootstrap](https://github.com/100-hours-a-week/KTB4-ian-week4/pull/7) — closed
- [#6 test: BE required-gate Ruleset 검증](https://github.com/100-hours-a-week/KTB4-ian-week4/pull/6) — closed
- [#5 fix: GHCR provenance 발행 권한 보강](https://github.com/100-hours-a-week/KTB4-ian-week4/pull/5) — closed
- [#4 feat: PULSE 중앙 CI/CD와 4서비스 배포 구성](https://github.com/100-hours-a-week/KTB4-ian-week4/pull/4) — closed
- [#3 feat: add Method A EC2 deployment support](https://github.com/100-hours-a-week/KTB4-ian-week4/pull/3) — closed
- [#2 fix: Access Token 폐기 및 Refresh Token 회전 강화](https://github.com/100-hours-a-week/KTB4-ian-week4/pull/2) — open at migration
- [#1 feat: 사용자별 피드 북마크 기능 추가](https://github.com/100-hours-a-week/KTB4-ian-week4/pull/1) — open at migration

### Frontend

- [#5 test: FE required-gate Ruleset 검증](https://github.com/100-hours-a-week/KTB4-ian-community-FE/pull/5) — closed
- [#4 feat: PULSE Frontend CI/CD와 CSRF 경합 수정](https://github.com/100-hours-a-week/KTB4-ian-community-FE/pull/4) — closed
- [#3 feat: prepare frontend for Method A deployment](https://github.com/100-hours-a-week/KTB4-ian-community-FE/pull/3) — closed
- [#2 fix: Access Token 만료 전 선제 갱신](https://github.com/100-hours-a-week/KTB4-ian-community-FE/pull/2) — open at migration
- [#1 feat: 피드 저장 및 북마크 페이지 연동](https://github.com/100-hours-a-week/KTB4-ian-community-FE/pull/1) — open at migration

## Preserved Actions evidence

- Backend main CI: [31096630424](https://github.com/100-hours-a-week/KTB4-ian-week4/actions/runs/31096630424)
- Backend image publication: [31096859747](https://github.com/100-hours-a-week/KTB4-ian-week4/actions/runs/31096859747)
- Backend required-gate failure/recovery: [31084283724](https://github.com/100-hours-a-week/KTB4-ian-week4/actions/runs/31084283724), [31084695345](https://github.com/100-hours-a-week/KTB4-ian-week4/actions/runs/31084695345)
- Frontend main CI: [31083228492](https://github.com/100-hours-a-week/KTB4-ian-community-FE/actions/runs/31083228492)
- Frontend image publication: [31083509813](https://github.com/100-hours-a-week/KTB4-ian-community-FE/actions/runs/31083509813)
- Frontend required-gate failure/recovery: [31084277826](https://github.com/100-hours-a-week/KTB4-ian-community-FE/actions/runs/31084277826), [31084691583](https://github.com/100-hours-a-week/KTB4-ian-community-FE/actions/runs/31084691583)

The non-expired Docker build records and the logs above are attached to the
[`migration-2026-08-06` Backend release](https://github.com/BS-Stack-Lab/KTB4-ian-community-BE/releases/tag/migration-2026-08-06)
and
[`migration-2026-08-06` Frontend release](https://github.com/BS-Stack-Lab/KTB4-ian-community-FE/releases/tag/migration-2026-08-06).
Each release includes `SHA256SUMS`.

## Last organization-owned production candidates

- Backend commit: `486cb29d7b3e076f235cb2ba6c73e22a91261a15`
- Backend digest: `sha256:266c33182bbbd9db1aeec78244dfe2fe5d05af0804026a60228eb3c2e993b8e5`
- Frontend commit: `789191773df3235b3f2a7f22d24bb63c5daa3141`
- Frontend digest: `sha256:e09fc7d8f37c28c74ae65f0d30d120007093a5d5e1dcbae5d3324a6bd4641392`

These organization-owned images are retained for evidence only. Production
uses only the new personal-account image digests after Gate 2 and Gate 3 pass.
