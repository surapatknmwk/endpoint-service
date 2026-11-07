package com.core.service.notification.reminder;

import com.core.entity.DeliveryJob;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link JobDueMessageBuilder} — pure text building, tested with real values (no mocks).
 */
class JobDueMessageBuilderTest {

    // พฤหัส 22 ต.ค. 2026
    private static final LocalDate DATE = LocalDate.of(2026, 10, 22);

    private DeliveryJob job(String name) {
        return DeliveryJob.builder().jobId(1L).jobCode("JOB-20261022-AB12CD34").jobName(name).build();
    }

    @Test
    void should_listFirstTwoCustomersAndRemainder_when_jobHasFiveStops() {
        //GIVEN
        List<String> names = List.of("สมชาย", "สมหญิง", "สมศักดิ์", "สมปอง", "สมใจ");

        //WHEN
        String message = JobDueMessageBuilder.message(job("ส่งสกลนคร รอบเช้า"), names, DATE);

        //THEN
        assertThat(message).isEqualTo(
                "พรุ่งนี้ (พฤหัส 22 ต.ค.) มีนัดส่งงาน “ส่งสกลนคร รอบเช้า” · 5 จุด: สมชาย, สมหญิง และอีก 3 ราย");
    }

    @Test
    void should_listAllCustomersWithoutRemainder_when_jobHasTwoStops() {
        //GIVEN — boundary: เท่ากับจำนวนชื่อที่แสดงพอดี
        List<String> names = List.of("สมชาย", "สมหญิง");

        //WHEN
        String message = JobDueMessageBuilder.message(job("รอบบ่าย"), names, DATE);

        //THEN
        assertThat(message).endsWith("“รอบบ่าย” · 2 จุด: สมชาย, สมหญิง");
    }

    @Test
    void should_omitStopPart_when_jobHasNoActiveStops() {
        //GIVEN — zero: ทุกจุดถูกยกเลิก
        List<String> names = List.of();

        //WHEN
        String message = JobDueMessageBuilder.message(job("รอบบ่าย"), names, DATE);

        //THEN
        assertThat(message).isEqualTo("พรุ่งนี้ (พฤหัส 22 ต.ค.) มีนัดส่งงาน “รอบบ่าย”");
    }

    @Test
    void should_useJobCode_when_jobNameIsBlank() {
        //GIVEN
        DeliveryJob unnamed = job("   ");

        //WHEN
        String message = JobDueMessageBuilder.message(unnamed, List.of("สมชาย"), DATE);

        //THEN
        assertThat(message).contains("“JOB-20261022-AB12CD34” · 1 จุด: สมชาย");
    }
}
