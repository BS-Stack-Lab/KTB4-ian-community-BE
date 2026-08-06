# 프론트 검증은 보안 경계가 아닙니다: 회원가입 Bean Validation 복구하기

회원가입 화면에는 이메일 형식과 비밀번호 규칙을 확인하는 자바스크립트 검증이 있었습니다. 하지만 서버의 회원가입 컨트롤러에는 `@Valid`가 없었기 때문에 `SignupRequest`에 선언한 제약 조건이 실행되지 않았습니다.

브라우저 화면에서 버튼이 비활성화되는 것과 API가 안전한 것은 별개의 문제입니다. 이번 개선에서는 회원가입 요청 계약을 JSON으로 단순화하고, DTO 검증을 서버의 필수 진입 조건으로 복구했습니다.

## DTO에 규칙이 있어도 `@Valid`가 없으면 실행되지 않습니다

`SignupRequest`에는 이미 다음과 같은 제약이 선언되어 있었습니다.

```java
@NotBlank(message = "이메일을 입력해주세요.")
@Email(message = "올바른 이메일 형식이 아닙니다.")
private String email;

@NotBlank(message = "비밀번호를 입력해주세요.")
@Size(min = 8, max = 20,
      message = "비밀번호는 8자 이상 20자 이하입니다.")
@Pattern(
    regexp = "...",
    message = "대문자,소문자,숫자,특수문자를 각각 최소 1개 이상 포함"
)
private String password;
```

문제는 기존 컨트롤러가 `@RequestPart("request") SignupRequest`만 받았다는 점입니다. Spring이 요청 값을 DTO로 변환하더라도 `@Valid`가 없다면 Bean Validation은 시작되지 않습니다. 따라서 길이가 짧거나 조합 규칙을 만족하지 않는 비밀번호도 서비스까지 도달할 수 있었습니다.

프론트엔드 검증은 정상 사용자의 빠른 피드백을 위한 UX 장치입니다. `curl`, Postman, 모바일 앱, 변조된 자바스크립트처럼 화면을 거치지 않는 요청에는 아무런 강제력이 없습니다.

## 이미지가 없는 회원가입 요청을 JSON으로 정리했습니다

현재 프로필 이미지는 별도의 수정 API에서 처리하므로 회원가입 자체에 multipart 요청이 필요하지 않습니다. 컨트롤러를 로그인 API와 같은 JSON 계약으로 맞췄습니다.

```java
@PostMapping(
        value = "/signup",
        consumes = MediaType.APPLICATION_JSON_VALUE
)
public ResponseEntity<SignupResponse> signup(
        @Valid @RequestBody SignupRequest request
) {
    User user = userService.signup(request);
    // 토큰 발급과 응답 생략
}
```

핵심은 `@Valid`와 `@RequestBody`의 조합입니다. JSON 역직렬화가 끝난 직후 DTO 제약 조건이 평가되며, 하나라도 실패하면 컨트롤러 본문과 `UserService.signup()`은 실행되지 않습니다.

프론트엔드도 `FormData`가 아닌 JSON 문자열을 전송합니다.

```js
signup: (payload) =>
  httpClient("/api/users/signup", {
    method: "POST",
    body: JSON.stringify(payload),
  }),
```

이로써 브라우저와 서버가 동일한 Content-Type 계약을 사용합니다. 파일이 없는 요청을 multipart로 감쌀 이유가 사라지고 테스트 입력도 단순해졌습니다.

## 검증 실패는 저장 로직보다 먼저 종료됩니다

검증 실패 시 Spring은 `MethodArgumentNotValidException`을 발생시킵니다. 공통 예외 처리기가 이를 `400 Bad Request`와 `INVALID_REQUEST` 코드로 변환하므로 서비스의 중복 이메일 확인, 비밀번호 암호화, 사용자 저장은 실행되지 않습니다.

흐름은 다음과 같습니다.

```text
JSON 요청
  → SignupRequest 역직렬화
  → @Valid Bean Validation
  → 실패: 400 오류 응답
  → 성공: UserService.signup()
  → 비밀번호 암호화 및 사용자 저장
```

서버 검증을 서비스 로직 앞에 배치하면 잘못된 데이터가 저장 단계에 도달하지 않습니다. 프론트엔드 규칙에 버그가 생기거나 새로운 클라이언트가 추가되어도 서버가 최종 규칙을 보장합니다.

## 정상 요청과 실패 요청을 함께 테스트했습니다

통합 테스트는 형식이 잘못된 이메일, 빈 비밀번호, 비밀번호 조합 규칙 위반을 각각 JSON으로 직접 전송합니다. 이는 브라우저 검증을 우회한 요청과 같은 조건입니다.

```java
signup("""
    {
      "email": "valid2@example.com",
      "password": "password",
      "password_confirm": "password",
      "nickname": "사용자"
    }
    """)
    .andExpect(status().isBadRequest())
    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
```

또한 잘못된 회원가입 요청 전후의 사용자 수를 비교해 저장 부작용이 없음을 확인합니다.

```java
long before = userRepository.count();

signup(invalidRequest)
        .andExpect(status().isBadRequest());

assertThat(userRepository.count()).isEqualTo(before);
```

정상 요청은 `200 OK`와 Access Token·Refresh Token 쿠키, 사용자 ID, Access Token 만료 시각을 반환하는지 별도로 검증합니다. 실패만 확인하는 테스트로는 정상 경로가 함께 깨진 회귀를 발견할 수 없기 때문입니다.

## 회고

DTO의 검증 애너테이션은 선언만으로 보호 장치가 되지 않습니다. 요청 진입점에서 `@Valid`가 연결되어야 실제 정책으로 작동합니다. 특히 프론트엔드 검증이 잘 되어 있을수록 서버 검증 누락이 눈에 띄지 않으므로 API 통합 테스트가 중요합니다.

프론트엔드는 즉각적인 안내를 담당하고 서버는 데이터 무결성을 책임집니다. 두 계층이 같은 규칙을 가지더라도 서버 검증은 중복이 아니라 신뢰 경계입니다.
