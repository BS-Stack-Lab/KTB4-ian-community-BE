# 인증만으로 개인정보를 지킬 수 없었습니다: 회원 조회 API에 소유권 검사를 넣기

회원 조회 API는 로그인한 사용자만 호출할 수 있었습니다. 표면적으로는 보호된 API처럼 보이지만, 응답에 이메일이 포함되는 순간 `authenticated()`만으로는 충분하지 않았습니다. 로그인 여부와 특정 사용자 정보를 읽을 권한이 있는지는 서로 다른 문제이기 때문입니다.

이번 개선에서는 URL의 사용자 ID를 신뢰하던 조회 흐름을 인증 주체 중심으로 바꾸고, 기존 경로를 유지해야 하는 경우에는 명시적인 소유권 검사를 추가했습니다.

## 문제는 인증 이후에 발생했습니다

기존 `SecurityConfig`는 공개 API를 제외한 모든 요청에 인증을 요구합니다.

```java
authorize.anyRequest().authenticated();
```

이 설정은 비로그인 사용자의 접근은 막지만, 로그인한 사용자 A가 사용자 B의 자원을 요청하는 것은 막지 못합니다. 조회 API가 다음처럼 경로의 `userId`만 사용하면 공격자는 숫자를 바꾸어 다른 사용자를 계속 조회할 수 있습니다.

```http
GET /api/users/1
GET /api/users/2
GET /api/users/3
```

`UserResponse`에는 이메일이 포함되어 있으므로 이는 단순한 식별자 노출이 아니라 개인정보 열람 문제입니다. 필요한 검사는 “로그인했는가”에서 끝나지 않고 “로그인한 주체와 조회 대상이 같은가”까지 이어져야 합니다.

## 현재 사용자 조회를 `/me`로 분리했습니다

가장 먼저 클라이언트가 자신의 ID를 URL에 실어 보내지 않아도 되는 경로를 만들었습니다.

```java
@GetMapping("/me")
public ResponseEntity<UserResponse> getUser(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
) {
    return ResponseEntity.ok(
            userService.getCurrentUser(authenticatedUser.getUserId())
    );
}
```

`/api/users/me`의 조회 대상은 경로 변수나 요청 본문이 아니라 검증된 인증 주체에서 결정됩니다. 사용자가 개발자 도구나 별도 HTTP 클라이언트로 요청을 조작하더라도 다른 사용자 ID를 주입할 입력 지점이 없습니다.

프론트엔드의 `userApi.me()`도 더 이상 전달받은 사용자 ID로 URL을 만들지 않고 항상 `/api/users/me`를 호출합니다.

```js
me: (userIdOrOptions, options) =>
  httpClient(
    "/api/users/me",
    options ??
      (typeof userIdOrOptions === "object" ? userIdOrOptions : undefined),
  ),
```

기존 호출부가 넘기던 인자를 한 번에 제거하지 못하더라도 실제 네트워크 경로에는 사용자 ID가 반영되지 않습니다.

## 기존 ID 경로에는 소유권 검사를 추가했습니다

이전 클라이언트와의 호환을 위해 `GET /api/users/{userId}`는 남겨두었습니다. 대신 인증 주체의 ID와 경로의 ID를 함께 서비스로 전달합니다.

```java
public UserResponse getUserForAuthenticatedUser(
        Long authenticatedUserId,
        Long requestedUserId
) {
    if (!Objects.equals(authenticatedUserId, requestedUserId)) {
        throw new CustomException(ErrorCode.FORBIDDEN);
    }

    return getCurrentUser(authenticatedUserId);
}
```

두 값이 다르면 데이터베이스에서 상대 사용자를 조회하기 전에 `FORBIDDEN` 예외가 발생합니다. 일치하는 경우에도 최종 조회에는 요청 값이 아니라 인증 주체의 ID를 사용합니다. 비교 이후의 데이터 흐름까지 인증 주체를 기준으로 고정한 것입니다.

이 검사를 서비스 계층에 둔 이유는 컨트롤러 이외의 호출 경로에서도 같은 규칙을 재사용하기 위해서입니다. 개인정보 조회라는 유스케이스 자체가 소유권 조건을 가지므로 HTTP 계층에만 규칙을 두면 다른 진입점이 생겼을 때 다시 누락될 수 있습니다.

## 401과 403을 구분했습니다

인증 정보가 없으면 Spring Security가 `401 UNAUTHORIZED`를 반환합니다. 로그인했지만 다른 사용자를 조회하면 애플리케이션이 `403 FORBIDDEN`을 반환합니다.

```json
{
  "code": "FORBIDDEN",
  "message": "요청을 수행할 권한이 없습니다.",
  "data": null
}
```

상태 코드를 구분하면 클라이언트도 재로그인이 필요한 상황과 권한이 없는 상황을 다르게 처리할 수 있습니다. 동시에 서버 로그와 모니터링에서도 인증 실패와 인가 실패를 분리해 관찰할 수 있습니다.

## 통합 테스트로 열거 공격 경계를 고정했습니다

`UserApiIntegrationTest`는 두 사용자를 저장한 뒤 A의 Access Token으로 B의 ID를 조회합니다.

```java
mockMvc.perform(
        get("/api/users/{userId}", other.getUserId())
                .cookie(accessCookie(mine))
)
.andExpect(status().isForbidden())
.andExpect(jsonPath("$.code").value("FORBIDDEN"))
.andExpect(jsonPath("$.data").doesNotExist());
```

본인 조회에서는 이메일이 정상 반환되고, 타인 조회에서는 응답 데이터가 노출되지 않는 것을 함께 검증합니다. 비인증 요청의 `401`, 존재하지 않는 인증 사용자의 `404`도 별도 시나리오로 확인합니다.

## 회고

`anyRequest().authenticated()`는 출입문을 잠그지만, 건물 안의 모든 서랍에 대한 권한까지 결정하지는 않습니다. 사용자 ID처럼 클라이언트가 바꿀 수 있는 값으로 개인정보를 조회한다면 인증 여부와 별도로 객체 소유권을 검사해야 합니다.

현재 사용자처럼 대상이 항상 인증 주체인 API는 `/me` 형태로 설계하는 편이 가장 단순하고 안전합니다. 기존 ID 기반 경로를 유지해야 한다면 인증 ID와 요청 ID를 비교하고, 실제 조회도 인증 ID로 수행해야 경계가 끝까지 유지됩니다.
