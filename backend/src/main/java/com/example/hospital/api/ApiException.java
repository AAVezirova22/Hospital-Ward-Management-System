package com.example.hospital.api;

public class ApiException extends RuntimeException {
  public final int status;
  public final String code;
  private final java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();

  public ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  /** Response headers to send with the error, e.g. rate-limit metadata. */
  public ApiException withHeader(String name, String value) {
    headers.put(name, value);
    return this;
  }

  public java.util.Map<String, String> headers() {
    return java.util.Collections.unmodifiableMap(headers);
  }

  public static ApiException missing() {
    return new ApiException(404, "NOT_FOUND", "Record not found.");
  }

  public static ApiException conflict(String code, String message) {
    return new ApiException(409, code, message);
  }
}
