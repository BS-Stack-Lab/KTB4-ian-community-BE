# 북마크 API 명세서

- 작성일: 2026-08-05
- Base URL: `https://pulse.gleeze.com`
- API Prefix: `/api/posts`
- 인증 방식: JWT `accessToken` HttpOnly Cookie
- 응답 형식: JSON, 단 삭제 성공은 Body가 없습니다.

## 1. Endpoint 요약입니다

| 기능           | Method   | Path                                  | 인증 | CSRF   | 성공 상태        |
| -------------- | -------- | ------------------------------------- | ---- | ------ | ---------------- |
| 북마크 저장    | `POST`   | `/api/posts/{postId}/bookmarks`       | 필수 | 필수   | `200 OK`         |
| 북마크 삭제    | `DELETE` | `/api/posts/{postId}/bookmarks`       | 필수 | 필수   | `204 No Content` |
| 내 북마크 목록 | `GET`    | `/api/posts/bookmarks?page=0&size=10` | 필수 | 불필요 | `200 OK`         |

세 API 모두 로그인 사용자를 `accessToken` Cookie에서 식별합니다. `userId`를
Path, Query 또는 Body로 받지 않으므로 다른 사용자의 북마크를 직접 지정할 수
없습니다.

## 2. 인증과 CSRF 규칙입니다

### 2.1 인증 Cookie입니다

로그인 성공 후 발급된 다음 Cookie를 모든 북마크 요청에 포함합니다.

```http
Cookie: accessToken=<jwt-access-token>
```

Browser 요청은 `credentials: "include"`를 사용합니다. Cookie는 HttpOnly이므로
JavaScript에서 Token 원문을 읽지 않습니다.

### 2.2 POST와 DELETE의 CSRF Header입니다

상태를 변경하는 `POST`, `DELETE` 요청은 CSRF Token이 필요합니다. 먼저
`GET /api/csrf`를 호출해 `XSRF-TOKEN` Cookie를 받은 뒤 Cookie 값을 URL
Decode해 다음 Header로 전송합니다.

```http
X-XSRF-TOKEN: <decoded-xsrf-token>
```

Frontend의 실제 요청 흐름은 다음과 같습니다.

```js
const token = decodeURIComponent(readCookie("XSRF-TOKEN"));

await fetch(`/api/posts/${postId}/bookmarks`, {
  method: "POST",
  credentials: "include",
  headers: {
    "X-XSRF-TOKEN": token,
  },
});
```

CSRF Token이 없거나 일치하지 않으면 `403 FORBIDDEN`을 반환합니다.

## 3. 공통 응답 형식입니다

북마크 저장과 목록 조회는 다음 Envelope를 사용합니다.

```json
{
  "code": "SUCCESS_CODE",
  "message": "처리 결과 메시지입니다.",
  "data": {}
}
```

| Field     | Type     | Null | 설명                                          |
| --------- | -------- | ---- | --------------------------------------------- |
| `code`    | `string` | 불가 | 성공 또는 오류를 구분하는 코드입니다.         |
| `message` | `string` | 불가 | 사용자에게 표시할 수 있는 결과 메시지입니다.  |
| `data`    | `object` | 가능 | API별 응답 데이터이며 오류일 때 `null`입니다. |

북마크 삭제 성공은 `204 No Content`이므로 Envelope를 반환하지 않습니다.

## 4. 북마크 저장 API입니다

### 4.1 요청입니다

```http
POST /api/posts/{postId}/bookmarks HTTP/1.1
Host: pulse.gleeze.com
Cookie: accessToken=<jwt-access-token>
X-XSRF-TOKEN: <decoded-xsrf-token>
Accept: application/json
```

| 구분 | 이름     | Type             | 필수 | 제약                                           |
| ---- | -------- | ---------------- | ---- | ---------------------------------------------- |
| Path | `postId` | `integer(int64)` | 필수 | 존재하며 Soft Delete되지 않은 게시글 ID입니다. |
| Body | 없음     | -                | -    | 요청 Body를 사용하지 않습니다.                 |

### 4.2 최초 저장 성공 응답입니다

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

```json
{
  "code": "BOOKMARK_CREATED",
  "message": "북마크를 저장했습니다.",
  "data": {
    "postId": 42,
    "bookmarked": true
  }
}
```

| Field             | Type             | 설명                                |
| ----------------- | ---------------- | ----------------------------------- |
| `data.postId`     | `integer(int64)` | 저장한 게시글 ID입니다.             |
| `data.bookmarked` | `boolean`        | 저장 후 상태이며 항상 `true`입니다. |

### 4.3 이미 저장된 북마크의 재요청 응답입니다

동일한 사용자가 같은 게시글을 다시 저장해도 오류로 처리하지 않습니다. 새
Database 행을 만들지 않고 다음 성공 응답을 반환합니다.

```json
{
  "code": "BOOKMARK_ALREADY_SAVED",
  "message": "이미 저장된 북마크입니다.",
  "data": {
    "postId": 42,
    "bookmarked": true
  }
}
```

최초 요청과 재요청이 모두 `200 OK`이므로 Client는 HTTP 상태가 아니라 `code`로
두 경우를 구분할 수 있습니다.

## 5. 북마크 삭제 API입니다

### 5.1 요청입니다

```http
DELETE /api/posts/{postId}/bookmarks HTTP/1.1
Host: pulse.gleeze.com
Cookie: accessToken=<jwt-access-token>
X-XSRF-TOKEN: <decoded-xsrf-token>
```

| 구분 | 이름     | Type             | 필수 | 제약                                           |
| ---- | -------- | ---------------- | ---- | ---------------------------------------------- |
| Path | `postId` | `integer(int64)` | 필수 | 존재하며 Soft Delete되지 않은 게시글 ID입니다. |
| Body | 없음     | -                | -    | 요청 Body를 사용하지 않습니다.                 |

### 5.2 성공 응답입니다

```http
HTTP/1.1 204 No Content
```

북마크 행이 존재하면 삭제합니다. 해당 사용자의 북마크가 이미 없어도 같은
`204 No Content`를 반환하므로 삭제 요청도 멱등입니다.

`BookmarkSuccessCode.BOOKMARK_DELETED`가 Enum에 정의되어 있지만 현재
Controller는 삭제 성공 시 ApiResponse를 사용하지 않고 204를 반환합니다.

## 6. 내 북마크 목록 조회 API입니다

### 6.1 요청입니다

```http
GET /api/posts/bookmarks?page=0&size=10 HTTP/1.1
Host: pulse.gleeze.com
Cookie: accessToken=<jwt-access-token>
Accept: application/json
```

| Query  | Type      | 필수 | Default | 제약                                           |
| ------ | --------- | ---- | ------- | ---------------------------------------------- |
| `page` | `integer` | 선택 | `0`     | 0부터 시작하는 Page 번호입니다.                |
| `size` | `integer` | 선택 | `10`    | Controller에서 최소 1, 최대 10으로 제한합니다. |

Client가 별도 정렬 조건을 전달해도 북마크 목록은 다음 고정 정렬을 사용합니다.

```text
created_at DESC, bookmark_id DESC
```

Soft Delete된 게시글은 목록에서 제외합니다. 응답의 각 게시글은 현재 로그인
사용자의 북마크 목록에서 조회되므로 `bookmarked`가 항상 `true`입니다.
`liked`는 같은 로그인 사용자의 좋아요 상태를 별도로 조회해 설정합니다.

### 6.2 다음 Page가 있는 응답입니다

```json
{
  "code": "BOOKMARK_LIST_FOUND",
  "message": "북마크 목록을 조회했습니다.",
  "data": {
    "content": [
      {
        "post_id": 42,
        "content": "저장한 게시글 내용입니다.",
        "user_id": 7,
        "author_name": "작성자",
        "profile_image": "/images/profile-default.svg",
        "like_count": 3,
        "comment_count": 1,
        "view_count": 15,
        "created_at": "2026-08-05T10:00:00",
        "updated_at": "2026-08-05T10:00:00",
        "post_deleted": false,
        "image_url": null,
        "bookmarked": true,
        "liked": false
      }
    ],
    "page": 0,
    "size": 10,
    "hasNext": true,
    "message": null
  }
}
```

### 6.3 마지막 Page 응답입니다

마지막 Page는 내용이 남아 있어도 `hasNext=false`이면 다음 Code와 Message를
사용합니다.

```json
{
  "code": "NO_MORE_BOOKMARKS",
  "message": "더 이상 조회할 북마크가 없습니다.",
  "data": {
    "content": [],
    "page": 1,
    "size": 10,
    "hasNext": false,
    "message": "더 이상 조회할 북마크가 없습니다."
  }
}
```

따라서 `NO_MORE_BOOKMARKS`는 요청 실패가 아니라 정상적으로 마지막 Slice에
도달했다는 의미입니다.

### 6.4 목록의 게시글 Field입니다

| Field           | Type                | Null | 설명                                                |
| --------------- | ------------------- | ---- | --------------------------------------------------- |
| `post_id`       | `integer(int64)`    | 불가 | 게시글 ID입니다.                                    |
| `content`       | `string`            | 불가 | 게시글 본문입니다.                                  |
| `user_id`       | `integer(int64)`    | 불가 | 작성자 사용자 ID입니다.                             |
| `author_name`   | `string`            | 불가 | 작성자 닉네임입니다.                                |
| `profile_image` | `string`            | 불가 | 작성자 프로필 이미지 URL입니다.                     |
| `like_count`    | `integer`           | 불가 | 게시글의 좋아요 수입니다.                           |
| `comment_count` | `integer`           | 불가 | 게시글의 댓글 수입니다.                             |
| `view_count`    | `integer`           | 불가 | 게시글 조회 수입니다.                               |
| `created_at`    | `string(date-time)` | 불가 | 게시글 생성 시각입니다.                             |
| `updated_at`    | `string(date-time)` | 불가 | 게시글 수정 시각입니다.                             |
| `post_deleted`  | `boolean`           | 불가 | 목록에서는 항상 `false`입니다.                      |
| `image_url`     | `string`            | 가능 | 게시글 이미지 URL이며 이미지가 없으면 `null`입니다. |
| `bookmarked`    | `boolean`           | 불가 | 북마크 목록에서는 항상 `true`입니다.                |
| `liked`         | `boolean`           | 불가 | 현재 로그인 사용자의 좋아요 여부입니다.             |

## 7. 오류 응답입니다

모든 오류는 다음 형식을 사용합니다.

```json
{
  "code": "ERROR_CODE",
  "message": "오류 메시지입니다.",
  "data": null
}
```

| HTTP Status | Code                    | 발생 조건                                            | Message                             |
| ----------- | ----------------------- | ---------------------------------------------------- | ----------------------------------- |
| `401`       | `UNAUTHORIZED`          | Access Token 없이 요청합니다.                        | `로그인이 필요합니다.`              |
| `401`       | `INVALID_ACCESS_TOKEN`  | Access Token이 유효하지 않습니다.                    | `유효하지 않은 Access Token입니다.` |
| `401`       | `EXPIRED_ACCESS_TOKEN`  | Access Token이 만료되었습니다.                       | `Access Token이 만료되었습니다.`    |
| `403`       | `FORBIDDEN`             | POST·DELETE의 CSRF Token이 없거나 일치하지 않습니다. | `요청을 수행할 권한이 없습니다.`    |
| `404`       | `USER_NOT_FOUND`        | 인증 사용자 ID에 해당하는 사용자가 없습니다.         | `사용자를 찾을 수 없습니다.`        |
| `404`       | `POST_NOT_FOUND`        | 게시글이 없거나 Soft Delete 상태입니다.              | `게시글을 찾을 수 없습니다.`        |
| `409`       | `USER_ALREADY_DELETED`  | 탈퇴 처리된 사용자가 요청합니다.                     | `탈퇴한 사용자입니다.`              |
| `500`       | `INTERNAL_SERVER_ERROR` | 처리하지 못한 예외가 발생합니다.                     | `서버 오류가 발생했습니다.`         |

현재 저장 API는 중복 북마크를 `409 BOOKMARK_ALREADY_EXISTS`로 반환하지
않습니다. 중복 요청을 멱등 성공으로 처리하고 `200 BOOKMARK_ALREADY_SAVED`를
반환하는 것이 현재 Controller 계약입니다.

## 8. 북마크 상태가 포함되는 관련 API입니다

북마크 관리 API 외에도 다음 게시글 조회 응답에 현재 로그인 사용자의
`bookmarked` 상태가 포함됩니다.

| Method | Path                  | 북마크 관련 응답                                              |
| ------ | --------------------- | ------------------------------------------------------------- |
| `GET`  | `/api/posts`          | 각 `PostResponse.bookmarked`에 사용자별 상태를 반환합니다.    |
| `GET`  | `/api/posts/{postId}` | `PostDetailResponse.bookmarked`에 사용자별 상태를 반환합니다. |

Feed 목록은 Page의 게시글 ID를 모아 북마크 ID를 한 번에 조회하므로 게시글마다
개별 북마크 Query를 실행하지 않습니다.

## 9. 구현 근거 파일입니다

- Controller:
  [`PostController.java`](../src/main/java/com/ian/community/post/controller/PostController.java)
- Service:
  [`BookmarkService.java`](../src/main/java/com/ian/community/post/service/BookmarkService.java)
- Response DTO:
  [`BookmarkResponse.java`](../src/main/java/com/ian/community/post/dto/response/BookmarkResponse.java)
- Slice DTO:
  [`SliceResponse.java`](../src/main/java/com/ian/community/post/dto/response/SliceResponse.java)
- API 통합 테스트:
  [`PostSliceApiIntegrationTest.java`](../src/test/java/com/ian/community/post/PostSliceApiIntegrationTest.java)
- Service 통합 테스트:
  [`BookmarkIntegrationTest.java`](../src/test/java/com/ian/community/post/BookmarkIntegrationTest.java)
- ERD:
  [`bookmark-erd.md`](./bookmark-erd.md)
