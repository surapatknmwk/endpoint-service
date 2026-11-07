package com.core.service.notification.reminder;

import com.core.entity.DeliveryJob;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Builds the text of a "job due tomorrow" notification, e.g.
 * {@code พรุ่งนี้ (พฤหัส 22 ต.ค.) มีนัดส่งงาน “ส่งสกลนคร รอบเช้า” · 5 จุด: สมชาย, สมหญิง และอีก 3 ราย}
 */
public final class JobDueMessageBuilder {

    public static final String TITLE = "พรุ่งนี้มีนัดส่งงาน";

    private static final int NAMES_SHOWN = 2;
    private static final DateTimeFormatter THAI_SHORT_DATE =
            DateTimeFormatter.ofPattern("EEE d MMM", Locale.of("th", "TH"));

    private JobDueMessageBuilder() {
    }

    /**
     * @param customerNames customer name of each stop that is not cancelled, in stop order
     */
    public static String message(DeliveryJob job, List<String> customerNames, LocalDate scheduledDate) {
        StringBuilder text = new StringBuilder("พรุ่งนี้ (")
                .append(THAI_SHORT_DATE.format(scheduledDate))
                .append(") มีนัดส่งงาน “")
                .append(jobLabel(job))
                .append("”");

        if (!customerNames.isEmpty()) {
            List<String> shown = customerNames.subList(0, Math.min(NAMES_SHOWN, customerNames.size()));
            text.append(" · ").append(customerNames.size()).append(" จุด: ").append(String.join(", ", shown));
            int remaining = customerNames.size() - shown.size();
            if (remaining > 0) {
                text.append(" และอีก ").append(remaining).append(" ราย");
            }
        }
        return text.toString();
    }

    /** Same rule as the job card: the job name, or the job code when the job has no name. */
    public static String jobLabel(DeliveryJob job) {
        String name = job.getJobName();
        return name == null || name.isBlank() ? job.getJobCode() : name.trim();
    }
}
