# 모든 검증 실패가 회원가입 오류였습니다: DTO 메시지를 응답까지 보존하기

기존 `GlobalExceptionHandler`는 `MethodArgumentNotValidException`이 어느 API에서 발생했는지 확인하지 않고 항상 `INVALID_SIGNUP_REQUEST` 메시지를 반환했습니다. 회원가입뿐 아니라 닉네임 변경, 비밀번호 변경, 게시글·댓글 요청의 검증 실패도 모두 “회원가입 정보를 확인해주세요.”로 바뀌었습니다.

DTO에는 이미 필드별 안내 문구가 선언되어 있었습니다. 문제는 검증 규칙이 아니라 예외 처리기가 그 결과를 버리고 있었다는 점입니다. 이번 개선에서는 `BindingResult`의 메시지를 공통 응답까지 보존하고, 여러 제약이 동시에 실패해도 같은 메시지를 선택하도록 순서를 정의했습니다.

## 공통 핸들러가 API의 문맥을 지워버렸습니다

Bean Validation은 실패한 필드, 제약 조건, DTO에 선언한 메시지를 `BindingResult`에 담습니다. 그러나 기존 핸들러는 이 정보를 읽지 않고 고정된 회원가입 오류를 반환했습니다.

```java
new ApiResponse<>(
        ErrorCode.INVALID_SIGNUP_REQUEST.getMessage(),
        null
)
```

이 구조에서는 비밀번호 변경의 새 비밀번호가 8자 미만이어도 회원가입 오류가 됩니다. DTO에 작성한 다음 문구도 사용자에게 도달하지 못합니다.

```java
@Size(
    min = 8,
    max = 20,
    message = "비밀번호는 8자 이상 20자 이하입니다."
)
```

공통 예외 처리기는 특정 기능의 메시지를 하드코딩해서는 안 됩니다. 입력 DTO가 가진 검증 결과를 공통 응답 형식으로 옮기는 역할만 담당해야 합니다.

## `BindingResult`에서 실제 필드 메시지를 꺼냈습니다

현재 핸들러는 `getFieldErrors()`를 순회하고 각 오류의 `defaultMessage`를 가져옵니다.

```java
String message = exception
        .getBindingResult()
        .getFieldErrors()
        .stream()
        .sorted(/* 검증 우선순위와 필드명 */)
        .map(ObjectError::getDefaultMessage)
        .filter(value -> value != null && !value.isBlank())
        .findFirst()
        .orElse("입력값을 확인해주세요.");
```

응답 코드는 특정 화면에 종속된 `INVALID_SIGNUP_REQUEST` 대신 공통 `INVALID_REQUEST`를 사용합니다.

```java
return ResponseEntity
        .badRequest()
        .body(new ApiResponse<>(
                ErrorCode.INVALID_REQUEST.getCode(),
                message,
                null
        ));
```

이제 어느 API에서 검증이 실패해도 해당 DTO에 선언한 문구가 응답의 `message`에 들어갑니다. 프론트엔드는 `INVALID_REQUEST`일 때 이 상세 메시지를 보존하므로 서버의 필드 안내가 실제 화면까지 이어집니다.

## 여러 오류 중 선택 순서를 결정적으로 만들었습니다

하나의 값은 여러 제약을 동시에 위반할 수 있습니다. 빈 이메일은 `@NotBlank`와 `@Email`에 모두 걸릴 수 있고, 빈 비밀번호는 `@NotBlank`, `@Size`, `@Pattern`을 동시에 위반할 수 있습니다.

단순히 `findFirst()`만 사용하면 검증기 구현이나 컬렉션 순서에 따라 사용자에게 보이는 메시지가 달라질 수 있습니다. 이를 막기 위해 제약 조건의 우선순위를 정의했습니다.

```java
private static final Map<String, Integer> VALIDATION_PRIORITY = Map.of(
        "NotBlank", 0,
        "NotNull", 1,
        "Email", 2,
        "Size", 3,
        "Pattern", 4
);
```

먼저 제약 우선순위로 정렬하고, 같은 우선순위에서는 필드명, 제약 코드, 메시지를 차례로 비교합니다. 사용자가 아무 값도 입력하지 않았다면 형식이나 길이보다 “입력해주세요.”를 먼저 보여주는 정책입니다.

이 정렬은 단순한 기술적 안정화가 아니라 사용자 경험 규칙입니다. 어떤 오류를 우선 안내할지 명시해야 테스트와 실제 응답이 같은 결과를 반복해서 냅니다.

## DTO 메시지가 실제 응답에 남는지 검증했습니다

회원가입 통합 테스트는 세 종류의 메시지를 확인합니다.

- 이메일 형식 실패는 `올바른 이메일 형식이 아닙니다.`를 반환합니다.
- 빈 비밀번호는 `비밀번호를 입력해주세요.`를 반환합니다.
- 비밀번호 조합 실패는 대문자·소문자·숫자·특수문자 안내를 반환합니다.

모든 필드를 비운 요청을 10회 반복해도 같은 첫 메시지가 선택되는지도 검증합니다.

```java
for (int attempt = 0; attempt < 10; attempt++) {
    signup(allBlankRequest)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message")
                    .value("이메일을 입력해주세요."));
}
```

이 테스트는 DTO 메시지 보존과 정렬의 결정성을 함께 보호합니다.

## 현재 구현의 범위와 보완점

현재 응답은 여러 필드 오류 전체가 아니라 우선순위가 가장 높은 한 개의 메시지를 전달합니다. 한 번에 하나의 오류를 보여주는 현재 UI에는 맞지만, 각 입력창 아래에 여러 서버 오류를 동시에 표시하려면 다음과 같은 `fieldErrors` 구조가 추가로 필요합니다.

```json
{
  "code": "INVALID_REQUEST",
  "message": "입력값을 확인해주세요.",
  "fieldErrors": {
    "email": "올바른 이메일 형식이 아닙니다.",
    "newPassword": "비밀번호는 8자 이상 20자 이하입니다."
  }
}
```

또한 공통 핸들러는 비밀번호 변경 API에도 동일하게 적용되지만, 현재 메시지 보존 통합 테스트는 회원가입 요청을 중심으로 작성되어 있습니다. `PATCH /api/users/{userId}/password`에 8자 미만의 새 비밀번호를 직접 보내 `INVALID_REQUEST`와 DTO 메시지를 확인하는 테스트를 추가하면 리뷰에서 지적된 사례를 더 직접적으로 고정할 수 있습니다.

마지막으로 `UserPasswordUpdateRequest.newPasswordConfirm`의 `@Size`에는 별도 메시지가 없습니다. 해당 필드까지 동일한 한국어 정책을 적용하려면 명시적인 `message`를 추가해야 합니다.

## 회고

검증 메시지는 DTO에 작성하는 것으로 끝나지 않습니다. 검증기에서 예외 처리기, 공통 응답, HTTP 클라이언트, 화면까지 이어지는 전체 전달 경로가 보존되어야 사용자가 실제로 읽을 수 있습니다.

공통 예외 처리기는 특정 기능의 문맥을 덮어쓰지 않아야 합니다. DTO가 가장 구체적인 입력 규칙을 알고 있으므로 핸들러는 그 결과를 안정적으로 전달하고, 여러 오류가 있을 때의 우선순위만 공통 정책으로 관리하는 편이 확장에 유리합니다.
