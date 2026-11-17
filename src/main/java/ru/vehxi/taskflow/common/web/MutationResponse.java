package ru.vehxi.taskflow.common.web;

/** The saved result and notification outcome.
     * @param data saved DTO
     * @param notificationStatus notification result
     * @param <T> public DTO type */
public record MutationResponse<T>(T data, String notificationStatus) {
    /** Returns response without a notification.
     * @param data saved data
     * @param <T> public DTO type
     * @return response without a notification */
    public static <T> MutationResponse<T> withoutNotification(T data) {
        return new MutationResponse<>(data, "NOT_REQUIRED");
    }
}
