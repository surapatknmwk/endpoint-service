package com.authen.event;

import com.authen.entity.User;

/**
 * Published inside the login transaction once a session is issued (password or LINE). {@code deviceId} is the
 * browser-generated id from header {@code X-Device-Id} — null for clients that do not send it.
 */
public record UserLoggedInEvent(User user, String deviceId, String userAgent, String ipAddress) {
}
