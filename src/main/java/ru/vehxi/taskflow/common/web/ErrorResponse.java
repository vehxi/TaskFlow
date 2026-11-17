package ru.vehxi.taskflow.common.web;

import java.util.List;
import java.util.Map;

/** A safe API failure without rejected values.
     * @param code stable error code
     * @param message safe description
 *
     * @param fieldErrors validation messages without rejected values
     * @param requestId correlation key */
public record ErrorResponse(String code, String message, Map<String, List<String>> fieldErrors, String requestId) { }
