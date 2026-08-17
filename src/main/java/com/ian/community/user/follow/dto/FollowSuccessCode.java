package com.ian.community.user.follow.dto;

import com.ian.community.common.SuccessCode;
import org.springframework.http.HttpStatus;

public enum FollowSuccessCode implements SuccessCode {
    FOLLOW_CREATED("사용자를 팔로우했습니다."),
    ALREADY_FOLLOWING("이미 팔로우 중인 사용자입니다."),
    FOLLOW_DELETED("사용자 팔로우를 취소했습니다."),
    NOT_FOLLOWING("팔로우 관계가 없습니다.");

    private final String message;

    FollowSuccessCode(String message) {
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.OK;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getMessage() {
        return message;
    }
}
