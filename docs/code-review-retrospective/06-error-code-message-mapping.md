# 오류 코드는 번역문이 아닙니다: 서버 코드와 사용자 문구 분리하기

로그인에 실패했을 때 입력창 아래에 `invalid_login_request`가 표시되고, 존재하지 않는 게시글에 접근하면 본문에 `post_not_found`가 나타나는 문제가 있었습니다. 서버가 전달한 식별자를 프론트엔드가 그대로 `Error.message`로 사용했기 때문입니다.

오류 코드는 프로그램이 분기하기 위한 안정적인 계약이고, 사용자 문구는 화면과 언어 정책에 따라 바뀌는 표현입니다. 이번 개선에서는 두 역할을 분리하고 모든 HTTP 오류가 하나의 변환 경로를 통과하도록 정리했습니다.

## 한 문자열에 두 책임이 섞여 있었습니다

오류 식별자는 다음과 같은 장점이 있습니다.

- 언어와 무관하게 같은 원인을 표현합니다.
- 클라이언트가 상태 코드보다 세밀하게 분기할 수 있습니다.
- 문구가 바뀌어도 API 소비자의 로직이 깨지지 않습니다.

반면 `INVALID_LOGIN_REQUEST`는 사용자에게 보여줄 문장이 아닙니다. 프론트엔드가 서버의 `message`를 무조건 화면에 표시하거나, 코드 문자열을 `ApiError.message`에 넣으면 내부 계약이 UI에 노출됩니다.

먼저 백엔드 공통 응답에 `code`를 명시했습니다.

```java
public class ApiResponse<T> {
    private final String code;
    private final String message;
    private final T data;
}
```

`GlobalExceptionHandler`는 `CustomException`이 가진 `ErrorCode`에서 HTTP 상태, 코드, 메시지를 각각 꺼내 응답합니다.

```json
{
  "code": "POST_NOT_FOUND",
  "message": "게시글을 찾을 수 없습니다.",
  "data": null
}
```

현재 서버는 호환성과 검증 상세 전달을 위해 `message`도 유지합니다. 그러나 알려진 도메인 오류에서 화면 문구를 결정하는 기준은 `code`입니다.

## 프론트엔드에 코드-문구 사전을 만들었습니다

`errorMessages.js`는 서버 코드와 사용자가 읽을 한국어 문구를 한곳에서 관리합니다.

```js
const ERROR_MESSAGES = {
  INVALID_LOGIN_REQUEST: "이메일 또는 비밀번호를 확인해주세요.",
  POST_NOT_FOUND: "게시글을 찾을 수 없습니다.",
  UNAUTHORIZED: "로그인이 필요합니다.",
  FORBIDDEN: "요청을 수행할 권한이 없습니다.",
  INTERNAL_SERVER_ERROR: "서버 오류가 발생했습니다.",
};
```

`errorMessageFor()`는 알려진 코드를 사전에서 찾습니다. 알 수 없는 코드는 서버의 내부 메시지를 그대로 노출하지 않고 공통 문구로 대체합니다.

```js
export function errorMessageFor(code, serverMessage) {
  if (code === "INVALID_REQUEST" && serverMessage) return serverMessage;
  return ERROR_MESSAGES[code] ?? UNKNOWN_ERROR_MESSAGE;
}
```

`INVALID_REQUEST`만 서버 메시지를 허용하는 이유는 Bean Validation에서 DTO에 선언한 구체적인 입력 안내를 전달하기 위해서입니다. 도메인 오류는 코드로 번역하고, 입력 필드 검증은 서버가 확인한 상세 문구를 보존하는 혼합 전략입니다.

## 모든 HTTP 오류를 `toApiError()`로 모았습니다

오류 처리 경로가 API마다 흩어지면 일부 화면에서 다시 원시 코드가 노출될 수 있습니다. `httpClient`의 `toApiError()`가 응답 해석과 사용자 문구 선택을 한 번에 담당하도록 만들었습니다.

```js
function toApiError(response, body) {
  const code = body?.code ?? body?.errorCode;
  const serverMessage =
    typeof body === "string" ? body : (body?.message ?? undefined);

  return new ApiError(errorMessageFor(code, serverMessage), {
    status: response.status,
    code,
    fieldErrors: body?.fieldErrors,
    response,
    serverMessage,
  });
}
```

`ApiError.message`에는 화면에 표시해도 되는 문구가 들어갑니다. 동시에 `code`, `status`, 원본 `serverMessage`, `response`를 별도 속성으로 보존하므로 호출부는 상황에 따라 정교하게 분기할 수 있습니다.

로그인 폼과 게시글 상세 화면은 기존처럼 `cause.message`를 렌더링하지만, 이제 그 값은 원시 서버 문자열이 아니라 중앙 매퍼를 통과한 사용자 문구입니다. 각 컴포넌트가 코드 사전을 중복해서 알 필요도 없습니다.

## 알 수 없는 오류는 안전하게 실패하도록 했습니다

서버에 새 오류 코드가 추가되고 프론트엔드 배포가 늦을 수 있습니다. 이때 원본 메시지를 그대로 보여주면 내부 구현 정보나 번역되지 않은 문자열이 노출될 수 있습니다.

현재 구현은 알 수 없는 코드에 다음 공통 문구를 사용합니다.

```text
요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.
```

이 정책은 새 코드가 조용히 누락되는 것을 막지는 못하므로, 코드 사전과 테스트를 함께 갱신해야 합니다. 다만 누락이 발생해도 사용자 화면과 내부 정보 노출 측면에서 안전한 기본값을 제공합니다.

## 코드와 표시 문구를 단위 테스트로 고정했습니다

프론트엔드 단위 테스트는 로그인 실패와 게시글 없음 코드가 정확한 한국어로 변환되는지 확인합니다.

```js
expect(errorMessageFor("INVALID_LOGIN_REQUEST")).toBe(
  "이메일 또는 비밀번호를 확인해주세요.",
);

expect(errorMessageFor("POST_NOT_FOUND")).toBe(
  "게시글을 찾을 수 없습니다.",
);
```

알 수 없는 코드가 서버 내부 메시지를 무시하는지, `INVALID_REQUEST`가 검증 메시지를 보존하는지, `ApiError`가 코드와 HTTP 응답 정보를 잃지 않는지도 함께 검증합니다.

백엔드 테스트에서는 `POST_NOT_FOUND` 예외가 HTTP 상태와 코드, 안전한 메시지를 보존하고 예상하지 못한 예외의 내부 상세를 노출하지 않는지 확인합니다.

## 회고

오류 응답에는 기계가 읽는 계약과 사람이 읽는 표현이 함께 존재합니다. 두 값을 구분하지 않으면 코드가 화면에 노출되거나 문구 변경이 분기 로직을 깨뜨립니다.

서버는 안정적인 오류 코드를 제공하고, 프론트엔드는 그 코드를 사용자 경험에 맞는 문구로 해석해야 합니다. 검증 상세처럼 서버가 가장 정확히 아는 정보는 제한적으로 보존하되, 알 수 없는 서버 메시지는 그대로 노출하지 않는 정책까지 있어야 오류 처리 경계가 완성됩니다.
