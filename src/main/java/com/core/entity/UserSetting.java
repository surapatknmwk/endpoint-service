package com.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Key/value setting of one user (keys in {@link com.core.constant.UserSettingKey}). A missing row means the
 * default in code.
 */
@Entity
@Table(name = "user_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSetting {

    @EmbeddedId
    private Key id;

    @Column(name = "setting_value", nullable = false, length = 255)
    private String settingValue;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {

        @Column(name = "user_id")
        private Integer userId;

        @Column(name = "setting_key", length = 50)
        private String settingKey;
    }
}
