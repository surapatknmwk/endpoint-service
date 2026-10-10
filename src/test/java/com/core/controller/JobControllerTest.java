package com.core.controller;

import com.core.dto.CandidateOrderResponse;
import com.core.dto.JobRequest;
import com.core.dto.JobResponse;
import com.core.dto.RoutePreviewRequest;
import com.core.dto.RoutePreviewResponse;
import com.core.service.JobService;
import com.core.service.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link JobController}: the username is always extracted from the
 * {@code Authorization} header before calling {@link JobService} (per-user data isolation).
 */
@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    private static final String AUTH_HEADER = "Bearer jwt-token";

    @Mock
    private JobService jobService;
    @Mock
    private SecurityService securityService;

    private JobController controller;

    @BeforeEach
    void setUp() {
        controller = new JobController(jobService, securityService);
        when(securityService.extractUsernameFromToken(AUTH_HEADER)).thenReturn("admin");
    }

    @Test
    void should_listOnlyExtractedUsersJobs_when_listing() {
        //GIVEN
        List<JobResponse> jobs = List.of(JobResponse.builder().jobId(1L).build());
        when(jobService.listJobs("admin")).thenReturn(jobs);

        //WHEN
        var response = controller.listJobs(AUTH_HEADER);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isSameAs(jobs);
    }

    @Test
    void should_passParamsAndExtractedUser_when_gettingCandidateOrders() {
        //GIVEN
        List<CandidateOrderResponse> candidates = List.of(CandidateOrderResponse.builder().build());
        when(jobService.getCandidateOrders("S-ORIGIN", "S-DEST", "4701", "470101", "admin")).thenReturn(candidates);

        //WHEN
        var response = controller.getCandidateOrders(AUTH_HEADER, "S-ORIGIN", "S-DEST", "4701", "470101");

        //THEN
        assertThat(response.getBody().getData()).isSameAs(candidates);
    }

    @Test
    void should_passExtractedUser_when_previewingRoute() {
        //GIVEN
        RoutePreviewRequest request = RoutePreviewRequest.builder().originSubdistrictCode("S-ORIGIN").build();
        RoutePreviewResponse preview = RoutePreviewResponse.builder().build();
        when(jobService.previewRoute(request, "admin")).thenReturn(preview);

        //WHEN
        var response = controller.previewRoute(AUTH_HEADER, request);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(preview);
    }

    @Test
    void should_createJobAsExtractedUserAndReturn201_when_creating() {
        //GIVEN
        JobRequest request = JobRequest.builder().orderIds(List.of(10L)).build();
        JobResponse created = JobResponse.builder().jobId(1L).build();
        when(jobService.createJob(request, "admin")).thenReturn(created);

        //WHEN
        var response = controller.createJob(AUTH_HEADER, request);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData()).isSameAs(created);
        assertThat(response.getBody().getMessage()).isEqualTo("Job created successfully");
    }

    @Test
    void should_updateJobAsExtractedUser_when_updating() {
        //GIVEN
        JobRequest request = JobRequest.builder().orderIds(List.of(10L)).build();
        JobResponse updated = JobResponse.builder().jobId(1L).build();
        when(jobService.updateJob(1L, request, "admin")).thenReturn(updated);

        //WHEN
        var response = controller.updateJob(AUTH_HEADER, 1L, request);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(updated);
        assertThat(response.getBody().getMessage()).isEqualTo("Job updated successfully");
    }

    @Test
    void should_deleteJobAsExtractedUser_when_deleting() {
        //GIVEN no stubbing needed for a void call

        //WHEN
        var response = controller.deleteJob(AUTH_HEADER, 1L);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getMessage()).isEqualTo("Job deleted successfully");
        verify(jobService).deleteJob(1L, "admin");
    }

    @Test
    void should_completeStopAsExtractedUser_when_completing() {
        //GIVEN
        JobResponse updated = JobResponse.builder().jobId(1L).build();
        when(jobService.completeStop(1L, 10L, "admin")).thenReturn(updated);

        //WHEN
        var response = controller.completeStop(AUTH_HEADER, 1L, 10L);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(updated);
        assertThat(response.getBody().getMessage()).isEqualTo("Stop marked as completed");
    }

    @Test
    void should_skipStopAsExtractedUser_when_skipping() {
        //GIVEN
        JobResponse updated = JobResponse.builder().jobId(1L).build();
        when(jobService.skipStop(1L, 10L, "admin")).thenReturn(updated);

        //WHEN
        var response = controller.skipStop(AUTH_HEADER, 1L, 10L);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(updated);
        assertThat(response.getBody().getMessage()).isEqualTo("Stop skipped");
    }

    @Test
    void should_startJobAsExtractedUser_when_starting() {
        //GIVEN
        JobResponse started = JobResponse.builder().jobId(1L).build();
        when(jobService.startJob(1L, "admin")).thenReturn(started);

        //WHEN
        var response = controller.startJob(AUTH_HEADER, 1L);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(started);
        assertThat(response.getBody().getMessage()).isEqualTo("Job started");
    }

    @Test
    void should_reopenStopAsExtractedUser_when_reopening() {
        //GIVEN
        JobResponse updated = JobResponse.builder().jobId(1L).build();
        when(jobService.reopenStop(1L, 10L, "admin")).thenReturn(updated);

        //WHEN
        var response = controller.reopenStop(AUTH_HEADER, 1L, 10L);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(updated);
        assertThat(response.getBody().getMessage()).isEqualTo("Stop reopened");
    }

    @Test
    void should_cancelStopAsExtractedUser_when_cancelling() {
        //GIVEN
        JobResponse updated = JobResponse.builder().jobId(1L).build();
        when(jobService.cancelStop(1L, 10L, "admin")).thenReturn(updated);

        //WHEN
        var response = controller.cancelStop(AUTH_HEADER, 1L, 10L);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(updated);
        assertThat(response.getBody().getMessage()).isEqualTo("Stop cancelled");
    }

    @Test
    void should_closeJobAsExtractedUser_when_closing() {
        //GIVEN
        JobResponse closed = JobResponse.builder().jobId(1L).build();
        when(jobService.closeJob(1L, "admin")).thenReturn(closed);

        //WHEN
        var response = controller.closeJob(AUTH_HEADER, 1L);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(closed);
        assertThat(response.getBody().getMessage()).isEqualTo("Job closed");
    }
}
