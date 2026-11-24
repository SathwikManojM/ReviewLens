package com.reviewlens;

import com.jayway.jsonpath.JsonPath;
import com.reviewlens.model.AspectResult;
import com.reviewlens.model.AuditLog;
import com.reviewlens.model.Dataset;
import com.reviewlens.model.KeywordEntry;
import com.reviewlens.model.Product;
import com.reviewlens.model.Review;
import com.reviewlens.model.Role;
import com.reviewlens.model.Sentiment;
import com.reviewlens.model.User;
import com.reviewlens.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security / authorisation integration tests against a real MongoDB (database "reviewlens_test", dropped
 * before every test). Skipped automatically when no MongoDB is listening on localhost:27017.
 */
@SpringBootTest(properties = {
        "spring.mongodb.database=reviewlens_test",
        "spring.data.mongodb.database=reviewlens_test",
        "reviewlens.jwt.secret=test-secret-0123456789-0123456789-0123456789",
        "reviewlens.admin.email=",
        "reviewlens.admin.password=",
        "reviewlens.upload-dir=target/test-uploads",
        "reviewlens.reports-dir=target/test-reports"})
@EnabledIf("mongoAvailable")
class SecurityIntegrationTest {
    private static final String ADMIN_PW = "Admin-pass-12345";
    private static final String USER_PW = "User-pass-123";

    @Autowired WebApplicationContext ctx;
    @Autowired MongoTemplate mongo;
    @Autowired PasswordEncoder encoder;
    @Autowired SettingsService settings;

    MockMvc mvc;
    String adminId;
    String userId;

    static boolean mongoAvailable() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("localhost", 27017), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        assertThat(mongo.getDb().getName()).isEqualTo("reviewlens_test");   // never drop any other database
        mongo.getDb().drop();
        settings.evictCache();
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        adminId = createUser("Admin", "admin@test.local", ADMIN_PW, Role.ADMIN);
        userId = createUser("User", "user@test.local", USER_PW, Role.USER);
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/datasets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/datasets").header("Authorization", "Bearer forged.token.value")).andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotAccessAdministrativeResources() throws Exception {
        String user = login("user@test.local", USER_PW, false);
        mvc.perform(auth(get("/api/admin/users"), user)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/admin/settings"), user)).andExpect(status().isForbidden());
        mvc.perform(auth(patch("/api/admin/users/" + userId + "/role"), user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}")).andExpect(status().isForbidden());
        assertThat(mongo.findById(userId, User.class).getRole()).isEqualTo(Role.USER);
        mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials("user@test.local", USER_PW))).andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAdminApiAndNoSecretsLeak() throws Exception {
        String admin = login("admin@test.local", ADMIN_PW, true);
        String body = mvc.perform(auth(get("/api/admin/users"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("passwordHash").doesNotContain("$2a$").doesNotContain("tokenVersion");
        mvc.perform(auth(get("/api/admin/dashboard"), admin)).andExpect(status().isOk()).andExpect(jsonPath("$.users.total").value(2));
        String health = mvc.perform(auth(get("/api/admin/system/health"), admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(health).doesNotContain("mongodb://").doesNotContain("test-secret");
        assertThat(mongo.count(query(where("action").is("ADMIN_LOGIN")), AuditLog.class)).isEqualTo(1);
    }

    @Test
    void deactivatedUserCannotContinueUsingTheService() throws Exception {
        String user = login("user@test.local", USER_PW, false);
        mvc.perform(auth(get("/api/auth/me"), user)).andExpect(status().isOk());
        String admin = login("admin@test.local", ADMIN_PW, true);
        mvc.perform(auth(patch("/api/admin/users/" + userId + "/status"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false,\"reason\":\"test\"}")).andExpect(status().isOk());
        mvc.perform(auth(get("/api/auth/me"), user)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials("user@test.local", USER_PW))).andExpect(status().isForbidden());
        assertThat(mongo.count(query(where("action").is("USER_DEACTIVATED")), AuditLog.class)).isEqualTo(1);
    }

    @Test
    void lastAdministratorIsProtected() throws Exception {
        String admin = login("admin@test.local", ADMIN_PW, true);
        mvc.perform(auth(patch("/api/admin/users/" + adminId + "/role"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"USER\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        mvc.perform(auth(patch("/api/admin/users/" + adminId + "/status"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isBadRequest());
        assertThat(mongo.findById(adminId, User.class).getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void roleChangesAreAuthorisedAuditedAndRevokeOldSessions() throws Exception {
        String user = login("user@test.local", USER_PW, false);
        String admin = login("admin@test.local", ADMIN_PW, true);
        mvc.perform(auth(patch("/api/admin/users/" + userId + "/role"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\",\"reason\":\"promotion\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        assertThat(mongo.count(query(where("action").is("ROLE_CHANGED").and("resourceId").is(userId)), AuditLog.class)).isEqualTo(1);
        mvc.perform(auth(get("/api/auth/me"), user)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        String promoted = login("user@test.local", USER_PW, true);
        mvc.perform(auth(get("/api/admin/users"), promoted)).andExpect(status().isOk());
    }

    @Test
    void datasetDeletionCascadesAndIsAudited() throws Exception {
        Dataset d = new Dataset();
        d.setOwnerId(userId);
        d.setName("Cascade test");
        d.setFileName("x.csv");
        d.setStoredPath("target/test-uploads/does-not-exist.csv");
        d.setStatus(Dataset.Status.COMPLETED);
        mongo.insert(d);
        Product p = new Product();
        p.setDatasetId(d.getId());
        p.setOwnerId(userId);
        p.setName("Phone");
        mongo.insert(p);
        Review r = new Review();
        r.setDatasetId(d.getId());
        r.setProductId(p.getId());
        r.setText("Battery drains fast");
        mongo.insert(r);
        AspectResult a = new AspectResult();
        a.setDatasetId(d.getId());
        a.setProductId(p.getId());
        a.setReviewId(r.getId());
        a.setAspect("Battery");
        a.setSentiment(Sentiment.NEGATIVE);
        mongo.insert(a);
        mongo.insert(new KeywordEntry(d.getId(), null, "battery", 3, 1.0, KeywordEntry.Type.CONCERN));

        String other = login(createAndReturnEmail("Other", "other@test.local"), USER_PW, false);
        mvc.perform(auth(get("/api/datasets/" + d.getId()), other)).andExpect(status().isNotFound());   // ownership

        String admin = login("admin@test.local", ADMIN_PW, true);
        mvc.perform(auth(delete("/api/admin/datasets/" + d.getId()).param("reason", "test"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews").value(1));
        for (Class<?> c : new Class<?>[]{Product.class, Review.class, AspectResult.class, KeywordEntry.class}) {
            assertThat(mongo.count(query(where("datasetId").is(d.getId())), c)).as(c.getSimpleName()).isZero();
        }
        assertThat(mongo.findById(d.getId(), Dataset.class)).isNull();
        assertThat(mongo.count(query(where("action").is("DATASET_DELETED")), AuditLog.class)).isEqualTo(1);
    }

    @Test
    void maintenanceModeNeedsConfirmationAndBlocksOnlyUsers() throws Exception {
        String admin = login("admin@test.local", ADMIN_PW, true);
        String user = login("user@test.local", USER_PW, false);
        mvc.perform(auth(put("/api/admin/settings"), admin).contentType(MediaType.APPLICATION_JSON).content(settingsJson(true, true, 2, false)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFIRMATION_REQUIRED"));
        mvc.perform(auth(put("/api/admin/settings"), admin).contentType(MediaType.APPLICATION_JSON).content(settingsJson(true, true, 2, true)))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/datasets"), user)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("MAINTENANCE"));
        mvc.perform(auth(get("/api/admin/settings"), admin)).andExpect(status().isOk()).andExpect(jsonPath("$.maintenanceMode").value(true));
        assertThat(mongo.count(query(where("action").is("SETTINGS_CHANGED")), AuditLog.class)).isEqualTo(1);
    }

    @Test
    void settingsAreValidatedAndRegistrationCanBeClosed() throws Exception {
        String admin = login("admin@test.local", ADMIN_PW, true);
        mvc.perform(auth(put("/api/admin/settings"), admin).contentType(MediaType.APPLICATION_JSON).content(settingsJson(false, true, 99, true)))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(put("/api/admin/settings"), admin).contentType(MediaType.APPLICATION_JSON).content(settingsJson(false, false, 2, true)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New\",\"email\":\"new@test.local\",\"password\":\"Newpass-123\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
    }

    @Test
    void auditLogsCannotBeModifiedAndLogoutRevokesToken() throws Exception {
        String admin = login("admin@test.local", ADMIN_PW, true);
        mvc.perform(auth(delete("/api/admin/audit-logs"), admin)).andExpect(status().isMethodNotAllowed());
        mvc.perform(auth(put("/api/admin/audit-logs"), admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(auth(post("/api/auth/logout"), admin)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/admin/dashboard"), admin)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers
    private String createUser(String name, String email, String pw, Role role) {
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(pw));
        u.setRole(role);
        u.setCreatedAt(Instant.now());
        mongo.insert(u);
        return u.getId();
    }

    private String createAndReturnEmail(String name, String email) {
        createUser(name, email, USER_PW, Role.USER);
        return email;
    }

    private String login(String email, String pw, boolean adminPortal) throws Exception {
        String body = mvc.perform(post(adminPortal ? "/api/auth/admin/login" : "/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email, pw)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token);
    }

    private static String credentials(String email, String pw) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + pw + "\"}";
    }

    private static String settingsJson(boolean maintenance, boolean registrationOpen, int jobs, boolean confirm) {
        return "{\"maxUploadSizeMb\":100,\"maxReviewsPerDataset\":50000,\"maxDatasetsPerUser\":50,\"maxAnalysesPerUserPerDay\":30,"
                + "\"allowedFormats\":[\"csv\",\"tsv\"],\"maxConcurrentJobs\":" + jobs + ",\"maintenanceMode\":" + maintenance
                + ",\"maintenanceMessage\":\"Back soon\",\"featureFlags\":{\"registrationOpen\":" + registrationOpen
                + "},\"confirm\":" + confirm + "}";
    }
}
