package com.patchme.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 任务 6 集成测试：Testcontainers 起一次性真实 MySQL，走完整 HTTP 栈（Security 过滤链 + JWT Cookie + Flyway），
 * 用"双账号 + 管理员"跑完 04 上线清单的核心矩阵：匿名零泄露、公开主页、单向闸、越权拒绝、
 * 举报不自动处罚、确认下架、状态写闸、软删除、通知仅本人、发帖限流。
 * 本机没有 Docker 时整类自动跳过（disabledWithoutDocker），不阻塞 mvn test。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DualAccountJourneyIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.4").asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("patchme")
            .withUsername("patchme")
            .withPassword("patchme-tc-only");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String base = MYSQL.getJdbcUrl();
            // 会话时区钉到东八区：与生产 compose 的 --default-time-zone=+08:00 同语义，
            // 保证 CURRENT_TIMESTAMP 与 LocalDateTime.now() 同钟（限流窗口依赖这一点）。
            return base + (base.contains("?") ? "&" : "?")
                    + "connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true"
                    + "&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8";
        });
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // 测试资源 application.yml 全局排除了 DataSource 自动配置，这里清空排除项让容器数据源生效
        registry.add("spring.autoconfigure.exclude", () -> "");
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    /** 带登录态的轻量 HTTP 客户端：手工携带 pm_access Cookie（不共享，保证双账号隔离语义）。 */
    private final class Api {
        private String cookie;

        Api login(String email, String password) {
            ResponseEntity<String> resp = raw(HttpMethod.POST, "/api/auth/login",
                    Map.of("email", email, "password", password), null);
            assertThat(resp.getStatusCode().value()).as("登录成功: " + email).isEqualTo(200);
            for (String c : resp.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of())) {
                if (c.startsWith("pm_access=")) {
                    cookie = c.substring(0, c.indexOf(';'));
                }
            }
            assertThat(cookie).as("登录后应拿到 pm_access Cookie").isNotNull();
            return this;
        }

        JsonNode get(String path) {
            return data(HttpMethod.GET, path, null);
        }

        JsonNode post(String path, Object body) {
            return data(HttpMethod.POST, path, body);
        }

        JsonNode data(HttpMethod method, String path, Object body) {
            ResponseEntity<String> resp = raw(method, path, body, this);
            assertThat(resp.getStatusCode().value())
                    .as("%s %s 应成功, body=%s", method, path, resp.getBody()).isEqualTo(200);
            JsonNode node = parse(resp.getBody());
            assertThat(node.get("code").asText()).as("统一响应码").isEqualTo("0");
            return node.get("data");
        }

        ResponseEntity<String> tryRequest(HttpMethod method, String path, Object body) {
            return raw(method, path, body, this);
        }
    }

    private ResponseEntity<String> raw(HttpMethod method, String path, Object body, Api api) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (api != null && api.cookie != null) {
            headers.set(HttpHeaders.COOKIE, api.cookie);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + body, e);
        }
    }

    private long userIdOf(String email) {
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        return id == null ? -1 : id;
    }

    private static JsonNode findByIdNode(JsonNode list, long id) {
        for (JsonNode node : list) {
            if (node.path("id").asLong() == id) {
                return node;
            }
        }
        return null;
    }

    private void assertNoLeak(String body) {
        assertThat(body)
                .as("面向普通前端的响应不得泄露内部字段/邮箱/密码哈希")
                .doesNotContain("authorId", "schoolId", "majorId", "\"email\"", "@t6.test",
                        "passwordHash", "\"password\"", "deletedAt", "\"status\"");
    }

    @Test
    @DisplayName("双账号全链路：匿名零泄露 → 公开主页 → 单向闸 → 越权 → 举报审核 → 状态写闸 → 删除 → 通知")
    void dualAccountJourney() {
        Api guest = new Api();
        Api alice = new Api();
        Api bob = new Api();
        Api admin = new Api();

        // ---------- 阶段 0：注册三个账号；管理员角色模拟"开发者一次性初始化" ----------
        for (Object[] u : new Object[][]{
                {"t6it_alice@t6.test", "t6_it_alice", "小艾IT"},
                {"t6it_bob@t6.test", "t6_it_bob", "小布IT"},
                {"t6it_carol@t6.test", "t6_it_carol", "审核员IT"}}) {
            ResponseEntity<String> r = guest.tryRequest(HttpMethod.POST, "/api/auth/register",
                    Map.of("email", u[0], "password", "tc-pass-1234", "username", u[1], "nickname", u[2]));
            assertThat(r.getStatusCode().value()).as("注册 %s: %s", u[1], r.getBody()).isEqualTo(200);
        }
        jdbc.update("UPDATE users SET role='ADMIN' WHERE email=?", "t6it_carol@t6.test");
        alice.login("t6it_alice@t6.test", "tc-pass-1234");
        bob.login("t6it_bob@t6.test", "tc-pass-1234");
        admin.login("t6it_carol@t6.test", "tc-pass-1234");
        assertThat(admin.get("/api/me").get("role").asText()).isEqualTo("ADMIN");

        // ---------- 阶段 1：匿名发帖零泄露 + 发帖限流 ----------
        long anonPostId = alice.post("/api/posts",
                        Map.of("intent", "VENT", "title", "匿名投递", "body", "这是一条匿名内容", "identity", "ANONYMOUS"))
                .asLong();
        ResponseEntity<String> second = alice.tryRequest(HttpMethod.POST, "/api/posts",
                Map.of("intent", "VENT", "title", "第二条", "body", "60 秒内的第二条发帖"));
        assertThat(second.getStatusCode().value()).as("同一用户 60 秒内第二条发帖应被限流").isEqualTo(429);
        // 清空风控流水，后续阶段不再受发帖窗口干扰（一次性测试库，安全）
        jdbc.update("DELETE FROM moderation_log");

        ResponseEntity<String> feedRaw = guest.tryRequest(HttpMethod.GET, "/api/posts?limit=100", null);
        assertThat(feedRaw.getStatusCode().value()).isEqualTo(200);
        assertNoLeak(feedRaw.getBody());
        JsonNode anonInFeed = findByIdNode(parse(feedRaw.getBody()).get("data"), anonPostId);
        assertThat(anonInFeed).as("匿名帖应出现在游客首页").isNotNull();
        JsonNode anonAuthor = anonInFeed.get("author");
        assertThat(anonAuthor.size()).as("匿名作者对象只允许 mode 一个字段").isEqualTo(1);
        assertThat(anonAuthor.get("mode").asText()).isEqualTo("anonymous");

        // ---------- 阶段 2：公开发帖进公开主页；匿名帖不进；单向闸 ----------
        long publicPostId = alice.post("/api/posts",
                        Map.of("intent", "ADVICE", "title", "公开求助", "body", "这是一条公开内容", "identity", "PUBLIC"))
                .asLong();
        JsonNode profilePosts = alice.get("/api/users/t6_it_alice").get("posts");
        assertThat(findByIdNode(profilePosts, publicPostId)).as("公开帖应出现在公开主页").isNotNull();
        assertThat(findByIdNode(profilePosts, anonPostId)).as("匿名帖不得出现在公开主页").isNull();

        ResponseEntity<String> flip = alice.tryRequest(HttpMethod.PATCH,
                "/api/posts/" + anonPostId + "/identity", Map.of("mode", "PUBLIC"));
        assertThat(flip.getStatusCode().value()).as("匿名转公开应被永久拒绝").isEqualTo(400);
        assertThat(parse(flip.getBody()).get("message").asText()).contains("不可改为公开");
        JsonNode anonDetail = parse(guest.tryRequest(HttpMethod.GET, "/api/posts/" + anonPostId, null).getBody())
                .get("data").get("post");
        assertThat(anonDetail.get("author").get("mode").asText()).as("拒绝后内容保持匿名").isEqualTo("anonymous");

        // ---------- 阶段 3：互动、通知仅本人、越权拒绝 ----------
        JsonNode reply = bob.post("/api/posts/" + anonPostId + "/replies",
                Map.of("body", "抱抱你", "identity", "PUBLIC"));
        assertThat(reply.get("author").get("username").asText()).isEqualTo("t6_it_bob");
        alice.post("/api/posts/" + anonPostId + "/support", null);
        JsonNode detail = alice.get("/api/posts/" + anonPostId);
        assertThat(findByIdNode(detail.get("replies"), reply.get("id").asLong())).isNotNull();
        assertThat(detail.get("post").get("supportCount").asLong()).isEqualTo(1);

        assertThat(alice.get("/api/notifications").toString()).as("楼主应收到回复通知").contains("REPLY");
        assertThat(bob.get("/api/notifications").toString()).as("通知仅本人可见").doesNotContain("REPLY");

        assertThat(bob.tryRequest(HttpMethod.DELETE, "/api/posts/" + publicPostId, null).getStatusCode().value())
                .as("非楼主删帖应 403").isEqualTo(403);
        assertThat(bob.tryRequest(HttpMethod.PATCH, "/api/posts/" + anonPostId + "/identity",
                Map.of("mode", "ANONYMOUS")).getStatusCode().value())
                .as("非楼主改身份应 403").isEqualTo(403);
        assertThat(guest.tryRequest(HttpMethod.POST, "/api/posts",
                Map.of("intent", "VENT", "body", "游客发帖")).getStatusCode().value())
                .as("未登录写接口应 401").isEqualTo(401);

        // ---------- 阶段 4：举报不自动处罚 → 管理员确认下架（举报人零暴露） ----------
        long reportId = bob.post("/api/reports",
                        Map.of("targetType", "POST", "targetId", anonPostId, "reason", "SPAM", "note", "疑似广告"))
                .get("id").asLong();
        assertThat(guest.tryRequest(HttpMethod.GET, "/api/posts/" + anonPostId, null).getStatusCode().value())
                .as("仅举报不会自动下架").isEqualTo(200);

        assertThat(guest.tryRequest(HttpMethod.GET, "/api/admin/reports", null).getStatusCode().value())
                .isEqualTo(401);
        assertThat(alice.tryRequest(HttpMethod.GET, "/api/admin/reports", null).getStatusCode().value())
                .as("普通用户访问审核队列应 403").isEqualTo(403);

        ResponseEntity<String> queueRaw = admin.tryRequest(HttpMethod.GET, "/api/admin/reports", null);
        assertThat(queueRaw.getStatusCode().value()).isEqualTo(200);
        assertThat(queueRaw.getBody()).as("审核队列不得出现举报人身份")
                .doesNotContain("reporter", "t6it_bob@t6.test");
        JsonNode queued = findByIdNode(parse(queueRaw.getBody()).get("data"), reportId);
        assertThat(queued).as("队列应包含刚提交的举报").isNotNull();
        assertThat(queued.get("source").asText()).isEqualTo("USER");

        admin.post("/api/admin/reports/" + reportId + "/review",
                Map.of("confirm", true, "reason", "确认违规", "takedown", true));
        assertThat(guest.tryRequest(HttpMethod.GET, "/api/posts/" + anonPostId, null).getStatusCode().value())
                .as("确认下架后详情应 404").isEqualTo(404);
        assertThat(bob.get("/api/reports/mine").toString()).contains("CONFIRMED");
        assertThat(bob.get("/api/notifications").toString()).as("举报人收到处理结果通知").contains("REPORT");
        assertNoLeak(admin.tryRequest(HttpMethod.GET, "/api/admin/audit?limit=50", null).getBody());

        // ---------- 阶段 5：RESTRICT 写闸与 UNBAN 恢复 ----------
        long bobId = userIdOf("t6it_bob@t6.test");
        admin.post("/api/admin/actions",
                Map.of("action", "RESTRICT", "targetUserId", bobId, "reason", "连续违规"));
        assertThat(bob.tryRequest(HttpMethod.POST, "/api/posts",
                Map.of("intent", "VENT", "body", "受限期间发帖")).getStatusCode().value())
                .as("RESTRICTED 状态写接口应 403").isEqualTo(403);
        assertThat(bob.get("/api/me").get("status").asText()).isEqualTo("RESTRICTED");
        assertThat(bob.get("/api/notifications").toString()).contains("MODERATION");

        admin.post("/api/admin/actions", Map.of("action", "UNBAN", "targetUserId", bobId, "reason", "恢复"));
        assertThat(bob.post("/api/posts",
                Map.of("intent", "COMPANION", "title", "恢复首发", "body", "解除限制后的发帖", "identity", "PUBLIC"))
                .asLong()).isPositive();

        // ---------- 阶段 6：拉黑静默 → 公开转匿名 → 软删除 ----------
        bob.post("/api/blocks", Map.of("username", "t6_it_alice"));
        JsonNode bobFeed = parse(bob.tryRequest(HttpMethod.GET, "/api/posts?limit=100", null).getBody()).get("data");
        assertThat(findByIdNode(bobFeed, publicPostId)).as("拉黑后对方公开帖不可见").isNull();
        raw(HttpMethod.DELETE, "/api/blocks/t6_it_alice", null, bob);
        JsonNode bobFeed2 = parse(bob.tryRequest(HttpMethod.GET, "/api/posts?limit=100", null).getBody()).get("data");
        assertThat(findByIdNode(bobFeed2, publicPostId)).as("取消拉黑后恢复可见").isNotNull();

        alice.data(HttpMethod.PATCH, "/api/posts/" + publicPostId + "/identity", Map.of("mode", "ANONYMOUS"));
        assertThat(findByIdNode(alice.get("/api/users/t6_it_alice").get("posts"), publicPostId))
                .as("公开转匿名后立即从公开主页消失").isNull();

        alice.data(HttpMethod.DELETE, "/api/posts/" + publicPostId, null);
        assertThat(guest.tryRequest(HttpMethod.GET, "/api/posts/" + publicPostId, null).getStatusCode().value())
                .as("软删除后详情应 404").isEqualTo(404);
        assertThat(guest.tryRequest(HttpMethod.GET, "/api/posts?limit=100", null).getBody())
                .doesNotContain("\"id\":" + publicPostId + ",");

        // ---------- 阶段 7：所有公开出口零泄露总扫 + 健康检查 ----------
        for (String path : new String[]{"/api/posts?limit=100", "/api/dicts"}) {
            assertNoLeak(guest.tryRequest(HttpMethod.GET, path, null).getBody());
        }
        assertNoLeak(alice.tryRequest(HttpMethod.GET, "/api/me/posts", null).getBody());
        assertNoLeak(alice.tryRequest(HttpMethod.GET, "/api/notifications", null).getBody());
        assertNoLeak(bob.tryRequest(HttpMethod.GET, "/api/users/t6_it_alice", null).getBody());
        assertThat(guest.tryRequest(HttpMethod.GET, "/api/health", null).getStatusCode().value()).isEqualTo(200);
    }
}
