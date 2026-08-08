package com.ian.community.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // 회원
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    USER_ALREADY_DELETED(HttpStatus.CONFLICT, "탈퇴한 사용자입니다."),

    // 회원가입
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    INVALID_SIGNUP_REQUEST(HttpStatus.BAD_REQUEST, "회원가입 정보를 확인해주세요."),

    // 로그인
    INVALID_LOGIN_REQUEST(HttpStatus.BAD_REQUEST, "이메일 또는 비밀번호를 확인해주세요."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호를 확인해주세요."),

    // 비밀번호 변경
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    NEW_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "새 비밀번호가 일치하지 않습니다."),
    PASSWORD_SAME_AS_CURRENT(HttpStatus.CONFLICT, "현재 비밀번호와 다른 비밀번호를 입력해주세요."),

    // 게시물 작성/수정
    INVALID_POST_REQUEST(HttpStatus.BAD_REQUEST, "게시글 정보를 확인해주세요."),

    // 게시글 & 댓글 수정, 댓글 삭제
    NO_CHANGES_DETECTED(HttpStatus.CONFLICT, "변경된 내용이 없습니다."),

    // 게시글 삭제
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    POST_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 게시글입니다."),

    // 댓글
    INVALID_COMMENT_REQUEST(HttpStatus.BAD_REQUEST, "댓글 정보를 확인해주세요."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    COMMENT_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 댓글입니다."),

    // 북마크
    BOOKMARK_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 북마크한 게시글입니다."),
    BOOKMARK_OPERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "북마크 처리에 실패했습니다."),

    // 인증 인가
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."), // 로그인 안 했을 때
    FORBIDDEN(HttpStatus.FORBIDDEN, "요청을 수행할 권한이 없습니다."), // 로그인은 했지만 권한 없을 때

    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Access Token입니다."),
    EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "Access Token이 만료되었습니다."),

    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh Token이 만료되었습니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "Refresh Token이 없습니다."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "이미 사용된 Refresh Token입니다."),
    REFRESH_TOKEN_USER_MISMATCH(HttpStatus.UNAUTHORIZED, "Refresh Token 사용자 정보가 일치하지 않습니다."),
    REFRESH_TOKEN_FAMILY_MISMATCH(HttpStatus.UNAUTHORIZED, "Refresh Token 계열 정보가 일치하지 않습니다."),

    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값을 확인해주세요."),
    IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "이미지 크기가 너무 큽니다."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 이미지 형식입니다."),
    MEDIA_V2_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "이미지 처리 서비스를 사용할 수 없습니다."),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."),
    MEDIA_NOT_READY(HttpStatus.CONFLICT, "이미지 처리가 완료되지 않았습니다."),
    MEDIA_PURPOSE_MISMATCH(HttpStatus.BAD_REQUEST, "이미지 용도가 올바르지 않습니다."),
    INVALID_CROP_RECT(HttpStatus.BAD_REQUEST, "이미지 편집 영역이 올바르지 않습니다."),
    IMAGE_DIMENSION_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "이미지 해상도가 너무 큽니다."),
    IMAGE_TOO_SMALL(HttpStatus.BAD_REQUEST, "이미지 해상도가 너무 작습니다."),
    CORRUPTED_IMAGE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "손상된 이미지입니다."),
    ANIMATED_IMAGE_NOT_ALLOWED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "애니메이션 이미지는 지원하지 않습니다."),
    PROCESSING_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "이미지 처리 시간이 초과되었습니다."),
    PROCESSING_RETRY_EXHAUSTED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 처리 재시도 횟수를 초과했습니다."),
    UPLOAD_NOT_OWNED(HttpStatus.FORBIDDEN, "이미지에 접근할 권한이 없습니다."),
    MEDIA_IN_USE(HttpStatus.CONFLICT, "사용 중인 이미지는 삭제할 수 없습니다."),
    MEDIA_REVISION_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지 편집 Revision을 찾을 수 없습니다."),
    MEDIA_REVISION_NOT_READY(HttpStatus.CONFLICT, "이미지 편집 처리가 완료되지 않았습니다."),
    MEDIA_REVISION_ACTIVE(HttpStatus.CONFLICT, "활성 이미지 Revision은 취소할 수 없습니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");


    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return name();
    }

    public String getMessage() {
        return message;
    }
}
