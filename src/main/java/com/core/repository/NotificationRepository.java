package com.core.repository;

import com.core.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Slice<Notification> findByRecipientUsername(String recipientUsername, Pageable pageable);

    long countByRecipientUsernameAndReadAtIsNull(String recipientUsername);

    Optional<Notification> findByNotificationIdAndRecipientUsername(Long notificationId, String recipientUsername);

    @Modifying
    @Query("update Notification n set n.readAt = :readAt where n.recipientUsername = :username and n.readAt is null")
    int markAllRead(@Param("username") String username, @Param("readAt") LocalDateTime readAt);

    /**
     * Inserts one notification unless the recipient already has one with the same dedupe key.
     * Returns 1 when a row was created, 0 when it already existed.
     */
    @Modifying
    @Query(value = "insert into notifications (notification_id, recipient_username, notification_type, title, message, "
            + "job_id, target_date, dedupe_key, created_at, line_status, line_attempts) "
            + "values (nextval('notifications_notification_id_seq'), :recipient, :type, :title, :message, :jobId, "
            + ":targetDate, :dedupeKey, :createdAt, :lineStatus, 0) "
            + "on conflict (recipient_username, dedupe_key) do nothing", nativeQuery = true)
    int insertIgnore(@Param("recipient") String recipient,
                     @Param("type") String type,
                     @Param("title") String title,
                     @Param("message") String message,
                     @Param("jobId") Long jobId,
                     @Param("targetDate") LocalDate targetDate,
                     @Param("dedupeKey") String dedupeKey,
                     @Param("createdAt") LocalDateTime createdAt,
                     @Param("lineStatus") String lineStatus);

    /**
     * สร้างแจ้งเตือนเดียวกันให้ผู้ใช้ทุกคนในกลุ่ม (ALL / OWNER = ทุก role ยกเว้น DRIVER / DRIVER) ที่ยังใช้งานอยู่
     * ด้วย SQL เดียว · คนที่มี dedupe key นี้แล้วถูกข้าม · คืนจำนวนแถวที่สร้าง
     */
    @Modifying
    @Query(value = "insert into notifications (notification_id, recipient_username, notification_type, title, message, "
            + "dedupe_key, created_at, line_status, line_attempts) "
            + "select nextval('notifications_notification_id_seq'), u.username, :type, :title, :message, :dedupeKey, "
            + ":createdAt, :lineStatus, 0 "
            + "from users u join roles r on r.role_id = u.role_id "
            + "where u.is_active is not false "
            + "and (:audience = 'ALL' "
            + "  or (:audience = 'DRIVER' and r.role_name = :driverRole) "
            + "  or (:audience = 'OWNER' and r.role_name <> :driverRole)) "
            + "on conflict (recipient_username, dedupe_key) do nothing", nativeQuery = true)
    int insertForAudience(@Param("audience") String audience,
                          @Param("driverRole") String driverRole,
                          @Param("type") String type,
                          @Param("title") String title,
                          @Param("message") String message,
                          @Param("dedupeKey") String dedupeKey,
                          @Param("createdAt") LocalDateTime createdAt,
                          @Param("lineStatus") String lineStatus);

    /**
     * ถอนแจ้งเตือนประกาศระบบที่ยังไม่อ่าน ของประกาศที่ส่งไปแล้วแต่ถูกยกเลิก (status 'I') หรือหมดอายุแล้ว
     * ส่วนที่อ่านแล้วเก็บไว้เป็นประวัติ
     */
    @Modifying
    @Query(value = "delete from notifications n using system_announcements a "
            + "where n.notification_type = :type and n.read_at is null "
            + "and n.dedupe_key = 'SYSTEM_ANNOUNCEMENT:' || a.announcement_id "
            + "and a.published_at is not null and (a.status = 'I' or a.expires_at <= :now)",
            nativeQuery = true)
    int deleteWithdrawnAnnouncements(@Param("type") String type, @Param("now") LocalDateTime now);

    /**
     * ถอนแจ้งเตือน "นัดส่งพรุ่งนี้" ที่ยังไม่อ่าน ซึ่งไม่ตรงกับงานแล้ว: งานถูกลบ, ออกรถไปแล้ว (ไม่ใช่ NEW)
     * หรือวันนัดไม่ใช่ target_date ที่แจ้งไว้ ส่วนที่อ่านแล้วเก็บไว้เป็นประวัติ
     */
    @Modifying
    @Query(value = "delete from notifications n "
            + "where n.notification_type = :type and n.read_at is null "
            + "and not exists (select 1 from delivery_jobs j "
            + "  where j.job_id = n.job_id and j.status = 'A' and j.delivery_status = 'NEW' "
            + "  and j.scheduled_date >= n.target_date and j.scheduled_date < n.target_date + 1)",
            nativeQuery = true)
    int deleteStaleJobReminders(@Param("type") String type);

    /**
     * ถอนแจ้งเตือน "นัดส่งพรุ่งนี้" ที่ยังไม่อ่าน ของผู้รับที่ไม่ใช่เจ้าของงานและไม่ใช่คนส่งปัจจุบันของงานแล้ว
     * (คนส่งที่ถูกเปลี่ยน / ปลด) — แจ้งของเจ้าของงานไม่ถูกแตะ
     */
    @Modifying
    @Query(value = "delete from notifications n "
            + "where n.notification_type = :type and n.read_at is null and n.job_id is not null "
            + "and not exists (select 1 from delivery_jobs j where j.job_id = n.job_id "
            + "  and j.created_by = n.recipient_username) "
            + "and not exists (select 1 from delivery_jobs j join users d on d.user_id = j.assigned_driver_id "
            + "  where j.job_id = n.job_id and d.username = n.recipient_username)",
            nativeQuery = true)
    int deleteRemindersOfFormerDrivers(@Param("type") String type);

    /** แจ้งเตือนที่รอส่ง LINE — เก่าก่อน (design lineNotify §6.3) */
    @Query("select n from Notification n where n.lineStatus = 'PENDING' order by n.createdAt, n.notificationId")
    List<Notification> findLinePending(Pageable pageable);

    /** บันทึกผลการส่ง LINE ของแถวที่ยังรอส่ง — แก้เฉพาะคอลัมน์ line_* ไม่ทับ read_at ที่ผู้ใช้อาจเพิ่งกดอ่าน */
    @Transactional
    @Modifying
    @Query("update Notification n set n.lineStatus = :status, n.lineAttempts = :attempts, n.lineSentAt = :sentAt, "
            + "n.lineError = :error where n.notificationId = :id and n.lineStatus = 'PENDING'")
    int markLine(@Param("id") Long id,
                 @Param("status") String status,
                 @Param("attempts") int attempts,
                 @Param("sentAt") LocalDateTime sentAt,
                 @Param("error") String error);

    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") LocalDateTime cutoff);
}
