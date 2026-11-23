package com.tripweaver.common;

import java.util.Map;

/** Uniform API error body (plan section 10.6). */
public record ApiError(String errorCode, String message, Map<String, Object> details) {}
