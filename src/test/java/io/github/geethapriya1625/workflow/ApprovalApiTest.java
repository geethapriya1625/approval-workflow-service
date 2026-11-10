package io.github.geethapriya1625.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.geethapriya1625.workflow.domain.Role;
import io.github.geethapriya1625.workflow.outbox.OutboxEvent;
import io.github.geethapriya1625.workflow.outbox.OutboxEventRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ApprovalApiTest extends IntegrationTestBase {

    @Autowired
    OutboxEventRepository outbox;

    @Test
    void largeRequestWalksManagerFinanceDirectorAndLeavesAFullTrail() throws Exception {
        String erin = user("erin");
        String id = submitOk(erin, "15000.00");

        JsonNode created = read(getAs("/api/v1/requests/" + id, erin, Role.EMPLOYEE));
        assertThat(created.get("steps")).hasSize(3);
        assertThat(created.get("status").asText()).isEqualTo("PENDING");

        assertThat(decide("approve", id, user("mia"), Role.MANAGER).getResponse().getStatus()).isEqualTo(200);
        assertThat(decide("approve", id, user("fred"), Role.FINANCE).getResponse().getStatus()).isEqualTo(200);
        MvcResult last = decide("approve", id, user("dana"), Role.DIRECTOR);

        JsonNode done = read(last);
        assertThat(done.get("status").asText()).isEqualTo("APPROVED");
        assertThat(done.get("currentStep").isNull()).isTrue();

        JsonNode audit = read(getAs("/api/v1/requests/" + id + "/audit", erin, Role.EMPLOYEE));
        assertThat(texts(audit, "action")).containsExactly("SUBMITTED", "APPROVED_STEP_1", "APPROVED_STEP_2", "APPROVED_STEP_3");

        assertThat(outbox.findByAggregateIdOrderByIdAsc(UUID.fromString(id)))
                .extracting(OutboxEvent::getEventType)
                .containsExactly("approval.submitted", "approval.step_approved",
                        "approval.step_approved", "approval.approved");
    }

    @Test
    void smallRequestNeedsOnlyAManager() throws Exception {
        String id = submitOk(user("erin"), "250.00");
        JsonNode done = read(decide("approve", id, user("mia"), Role.MANAGER));
        assertThat(done.get("status").asText()).isEqualTo("APPROVED");
        assertThat(done.get("steps")).hasSize(1);
    }

    @Test
    void segregationOfDutiesIsEnforcedOverHttp() throws Exception {
        String max = user("max");
        String id = submitOk(max, "500.00");

        MvcResult own = decide("approve", id, max, Role.EMPLOYEE, Role.MANAGER);
        assertThat(own.getResponse().getStatus()).isEqualTo(403);
        assertThat(read(own).get("detail").asText()).contains("segregation of duties");

        assertThat(decide("approve", id, user("fred"), Role.FINANCE).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void rejectedRequestCannotBeApprovedLater() throws Exception {
        String id = submitOk(user("erin"), "5000.00");
        assertThat(read(decide("reject", id, user("mia"), Role.MANAGER)).get("status").asText()).isEqualTo("REJECTED");

        MvcResult late = decide("approve", id, user("mia2"), Role.MANAGER);
        assertThat(late.getResponse().getStatus()).isEqualTo(409);
        assertThat(late.getResponse().getContentType()).contains("application/problem+json");
    }

    @Test
    void onlyTheRequesterCanCancel() throws Exception {
        String erin = user("erin");
        String id = submitOk(erin, "80.00");
        assertThat(decide("cancel", id, user("mia"), Role.MANAGER).getResponse().getStatus()).isEqualTo(403);
        assertThat(read(decide("cancel", id, erin, Role.EMPLOYEE)).get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void inboxShowsWorkForMyRoleAtTheCurrentStepOnly() throws Exception {
        String id = submitOk(user("erin"), "3000.00");
        String fred = user("fred");

        assertThat(inboxIds(fred, Role.FINANCE)).doesNotContain(id);
        decide("approve", id, user("mia"), Role.MANAGER);
        assertThat(inboxIds(fred, Role.FINANCE)).contains(id);
        decide("approve", id, fred, Role.FINANCE);
        assertThat(inboxIds(fred, Role.FINANCE)).doesNotContain(id);
    }

    @Test
    void strangersGet404NotTheRequest() throws Exception {
        String id = submitOk(user("erin"), "80.00");
        assertThat(getAs("/api/v1/requests/" + id, user("eve"), Role.DIRECTOR).getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    void invalidBodyReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/requests")
                        .header("Authorization", bearer(user("erin"), Role.EMPLOYEE))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"\", \"category\": \"x\", \"amount\": -5, \"currency\": \"usd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    void requestsWithoutATokenAreUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/inbox")).andExpect(status().isUnauthorized());
    }

    @Test
    void demoTokenEndpointIssuesUsableTokens() throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"mia\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = read(res).get("accessToken").asText();
        mvc.perform(get("/api/v1/inbox").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private List<String> inboxIds(String user, Role role) throws Exception {
        return texts(read(getAs("/api/v1/inbox?size=100", user, role)).get("items"), "id");
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.get(field).asText()));
        return out;
    }
}
