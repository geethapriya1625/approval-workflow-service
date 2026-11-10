package io.github.geethapriya1625.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.geethapriya1625.workflow.domain.ApprovalRequestRepository;
import io.github.geethapriya1625.workflow.domain.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class IdempotencyTest extends IntegrationTestBase {

    @Autowired
    ApprovalRequestRepository requests;

    @Test
    void retryWithSameKeyReplaysTheOriginalResponseWithoutADuplicate() throws Exception {
        String erin = user("erin");
        String key = UUID.randomUUID().toString();

        MvcResult first = submit(erin, key, body("Monitors", "1200.00"));
        MvcResult retry = submit(erin, key, body("Monitors", "1200"));  // same amount, different scale

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
        assertThat(first.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("false");
        assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(read(retry).get("id")).isEqualTo(read(first).get("id"));
        assertThat(requests.findByRequesterId(erin, Pageable.unpaged()).getTotalElements()).isEqualTo(1);
    }

    @Test
    void sameKeyWithADifferentBodyIsRejected() throws Exception {
        String erin = user("erin");
        String key = UUID.randomUUID().toString();
        submit(erin, key, body("Monitors", "1200.00"));

        MvcResult misuse = submit(erin, key, body("Monitors", "9999.00"));
        assertThat(misuse.getResponse().getStatus()).isEqualTo(422);
        assertThat(requests.findByRequesterId(erin, Pageable.unpaged()).getTotalElements()).isEqualTo(1);
    }

    @Test
    void keysAreScopedPerUser() throws Exception {
        String key = UUID.randomUUID().toString();
        MvcResult a = submit(user("erin"), key, body("Chairs", "300.00"));
        MvcResult b = submit(user("omar"), key, body("Chairs", "300.00"));
        assertThat(read(a).get("id")).isNotEqualTo(read(b).get("id"));
        assertThat(b.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("false");
    }

    @Test
    void missingKeyIsABadRequest() throws Exception {
        mvc.perform(post("/api/v1/requests")
                        .header("Authorization", bearer(user("erin"), Role.EMPLOYEE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Desk", "100.00")))
                .andExpect(status().isBadRequest());
    }
}
