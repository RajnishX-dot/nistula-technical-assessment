package com.nistula.messaging.api.dto;

public class ErrorBodyDto {

    private String error;
    private String detail;

    public ErrorBodyDto() {}

    public ErrorBodyDto(String error, String detail) {
        this.error = error;
        this.detail = detail;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
