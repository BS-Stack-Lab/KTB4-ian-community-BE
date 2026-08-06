# 사용자 API와 오류 처리 개선 기술 회고

코드 리뷰에서 확인된 4~7번 문제를 현재 백엔드 `week4/community`와 프론트엔드 `community-FE` 구현을 기준으로 다시 검증하고 정리했습니다. 각 글은 하나의 문제만 다루며, 문제 상황, 원인, 구현 과정, 검증, 회고 순서로 독립적으로 읽을 수 있도록 작성했습니다.

## 확인 결과

| 항목 | 확인 결과 | 핵심 근거 |
| --- | --- | --- |
| 4. 회원 개인정보 조회 인가 | 반영 완료 | `/api/users/me`가 인증 주체의 ID를 사용하며, 호환용 `/{userId}`는 인증 ID와 요청 ID가 다르면 `403 FORBIDDEN`을 반환합니다. |
| 5. 회원가입 서버 검증 | 반영 완료 | 회원가입 요청이 `@Valid @RequestBody SignupRequest`를 사용하며, 잘못된 요청은 저장 전에 `400`으로 종료됩니다. |
| 6. 오류 코드의 사용자 문구 매핑 | 반영 완료(혼합 계약) | 서버가 `code`와 `message`를 응답하고, 프론트엔드가 알려진 코드를 한국어 문구로 변환해 `ApiError.message`에 저장합니다. |
| 7. 검증 메시지 보존 | 핵심 반영 완료 | 공통 핸들러가 `BindingResult`의 DTO 메시지 중 우선순위가 가장 높은 한 건을 꺼내 `INVALID_REQUEST`와 함께 반환합니다. 전체 `fieldErrors` 맵은 아직 제공하지 않습니다. |

## 글 목록

1. [인증만으로 개인정보를 지킬 수 없었습니다: 회원 조회 API에 소유권 검사를 넣기](./04-user-privacy-authorization.md)
2. [프론트 검증은 보안 경계가 아닙니다: 회원가입 Bean Validation 복구하기](./05-signup-server-validation.md)
3. [오류 코드는 번역문이 아닙니다: 서버 코드와 사용자 문구 분리하기](./06-error-code-message-mapping.md)
4. [모든 검증 실패가 회원가입 오류였습니다: DTO 메시지를 응답까지 보존하기](./07-validation-message-preservation.md)

## 실행한 검증

```bash
./gradlew test \
  --tests com.ian.community.user.UserApiIntegrationTest \
  --tests com.ian.community.common.exception.GlobalExceptionHandlerTest
```

백엔드에서는 총 14개 테스트가 통과했습니다. 회원 본인 조회, 타인 ID 조회 거부, 회원가입 검증 메시지, 검증 실패 시 저장 방지, 오류 코드 응답 계약을 확인했습니다.

```bash
npm run test:unit -- \
  --run tests/unit/error-messages.test.js \
  tests/unit/login-form.test.js \
  tests/unit/signup-validation.test.js
```

현재 npm 스크립트의 실행 범위에 따라 프론트엔드 단위 테스트 전체가 실행되었으며, 34개 테스트 파일의 127개 테스트가 모두 통과했습니다.

> 항목 7의 공통 핸들러는 비밀번호 변경 API에도 적용되지만, 현재 통합 테스트는 회원가입 요청을 중심으로 메시지 보존을 검증합니다. 비밀번호 변경 전용 `MockMvc` 회귀 테스트를 추가하면 해당 경계를 더 직접적으로 보호할 수 있습니다.
