package com.codeorbit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
public class CodeOrbitApplication {
    private static final Map<String, UserView> SESSIONS = new ConcurrentHashMap<>();
    private static JdbcTemplate DATABASE;
    public static void main(String[] args) {
        SpringApplication.run(CodeOrbitApplication.class, args);
    }

    @RestController
    @RequestMapping("/api/system")
    static class SystemController {
        @GetMapping("/health")
        public HealthResponse health() {
            return new HealthResponse("UP", "星码空间后端已启动", "第一阶段骨架");
        }
    }

    @RestController
    @RequestMapping("/api/auth")
    @CrossOrigin(origins = "*")
    static class AuthController {
        private final JdbcTemplate jdbc;
        AuthController(JdbcTemplate jdbc) { this.jdbc = jdbc; DATABASE = jdbc; }

        @PostMapping("/register")
        public synchronized Map<String, Object> register(@RequestBody RegisterRequest input) {
            String name = input.name() == null ? "" : input.name().trim();
            String email = input.email() == null ? "" : input.email().trim().toLowerCase();
            if (name.isBlank() || email.isBlank() || input.password() == null || input.password().length() < 6) return error("请填写昵称、邮箱和至少 6 位密码");
            if (!jdbc.queryForList("SELECT id FROM users WHERE email = ?", String.class, email).isEmpty()) return error("该邮箱已经注册");
            StoredUser user = new StoredUser(UUID.randomUUID().toString(), name, email, hash(input.password()));
            jdbc.update("INSERT INTO users (id, name, email, password_hash) VALUES (?, ?, ?, ?)", user.id(), user.name(), user.email(), user.passwordHash());
            createDefaultWorkspace(user);
            return session(user);
        }

        @PostMapping("/login")
        public Map<String, Object> login(@RequestBody LoginRequest input) {
            String email = input.email() == null ? "" : input.email().trim().toLowerCase();
            List<StoredUser> matches = jdbc.query("SELECT id, name, email, password_hash FROM users WHERE email = ?", (result, row) -> new StoredUser(result.getString("id"), result.getString("name"), result.getString("email"), result.getString("password_hash")), email);
            StoredUser user = matches.isEmpty() ? null : matches.getFirst();
            if (user == null || input.password() == null || !user.passwordHash().equals(hash(input.password()))) return error("邮箱或密码不正确");
            return session(user);
        }

        @GetMapping("/me")
        public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
            UserView user = findUser(token(authorization));
            if (user == null) return error("登录已过期");
            return Map.of("user", user);
        }

        private Map<String, Object> session(StoredUser user) {
            String token = UUID.randomUUID().toString();
            UserView view = new UserView(user.id(), user.name(), user.email());
            SESSIONS.put(token, view);
            jdbc.update("INSERT INTO auth_sessions (token, user_id, expires_at) VALUES (?, ?, ?)", token, user.id(), Timestamp.from(Instant.now().plus(Duration.ofDays(30))));
            return Map.of("token", token, "user", view);
        }
        private void createDefaultWorkspace(StoredUser user) {
            String workspaceId = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO workspaces (id, name, description, owner_id) VALUES (?, ?, ?, ?)", workspaceId, "个人开发空间", "个人工作区", user.id());
            jdbc.update("INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?, ?, ?)", workspaceId, user.id(), "OWNER");
        }
        private static Map<String, Object> error(String message) { return Map.of("message", message); }
        private static String token(String header) { return header != null && header.startsWith("Bearer ") ? header.substring(7) : ""; }
        private static String hash(String value) {
            try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        }
    }

    @RestController
    @RequestMapping("/api/workspaces")
    @CrossOrigin(origins = "*")
    static class WorkspaceController {
        private final JdbcTemplate jdbc;

        WorkspaceController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @GetMapping
        public Map<String, Object> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
            UserView user = findUser(token(authorization));
            if (user == null) return Map.of("message", "登录已过期");
            List<WorkspaceView> workspaces = jdbc.query("SELECT w.id, w.name, w.description, wm.role FROM workspaces w JOIN workspace_members wm ON wm.workspace_id = w.id WHERE wm.user_id = ? ORDER BY w.created_at", (result, row) -> new WorkspaceView(result.getString("id"), result.getString("name"), result.getString("description"), result.getString("role")), user.id());
            if (workspaces.isEmpty()) {
                String id = UUID.randomUUID().toString();
                jdbc.update("INSERT INTO workspaces (id, name, description, owner_id) VALUES (?, ?, ?, ?)", id, "个人开发空间", "个人工作区", user.id());
                jdbc.update("INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?, ?, ?)", id, user.id(), "OWNER");
                workspaces = List.of(new WorkspaceView(id, "个人开发空间", "个人工作区", "OWNER"));
            }
            return Map.of("active", workspaces.getFirst(), "workspaces", workspaces);
        }

        @PostMapping
        public Map<String, Object> create(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody WorkspaceRequest input) {
            UserView user = findUser(token(authorization));
            if (user == null) return Map.of("message", "登录已过期");
            String name = input.name() == null ? "" : input.name().trim();
            if (name.isBlank()) return Map.of("message", "请输入工作空间名称");
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO workspaces (id, name, description, owner_id) VALUES (?, ?, ?, ?)", id, name, input.description() == null ? "" : input.description().trim(), user.id());
            jdbc.update("INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?, ?, ?)", id, user.id(), "OWNER");
            return Map.of("workspace", new WorkspaceView(id, name, input.description() == null ? "" : input.description().trim(), "OWNER"));
        }

        @GetMapping("/{workspaceId}/members")
        public Map<String, Object> members(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String workspaceId) {
            UserView user = requireUser(authorization);
            requireMembership(user.id(), workspaceId);
            List<MemberView> members = jdbc.query("SELECT u.id, u.name, u.email, wm.role, wm.joined_at FROM workspace_members wm JOIN users u ON u.id = wm.user_id WHERE wm.workspace_id = ? ORDER BY FIELD(wm.role, 'OWNER', 'ADMIN', 'EDITOR', 'VIEWER'), wm.joined_at", (result, row) -> new MemberView(result.getString("id"), result.getString("name"), result.getString("email"), result.getString("role"), result.getTimestamp("joined_at").toInstant().toString()), workspaceId);
            return Map.of("members", members);
        }

        @PostMapping("/{workspaceId}/members")
        public Map<String, Object> invite(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String workspaceId, @RequestBody MemberRequest input) {
            UserView owner = requireOwner(authorization, workspaceId);
            String email = input.email() == null ? "" : input.email().trim().toLowerCase();
            if (email.isBlank()) return Map.of("message", "请输入成员邮箱");
            List<UserView> matches = jdbc.query("SELECT id, name, email FROM users WHERE email = ?", (result, row) -> new UserView(result.getString("id"), result.getString("name"), result.getString("email")), email);
            if (matches.isEmpty()) return Map.of("message", "该邮箱尚未注册，请先让对方创建账号");
            UserView target = matches.getFirst();
            if (jdbc.queryForObject("SELECT COUNT(*) FROM workspace_members WHERE workspace_id = ? AND user_id = ?", Integer.class, workspaceId, target.id()) > 0) return Map.of("message", "该用户已经是工作空间成员");
            String role = normalizeRole(input.role());
            jdbc.update("INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?, ?, ?)", workspaceId, target.id(), role);
            jdbc.update("INSERT INTO notifications (id, user_id, workspace_id, type, title, content) VALUES (?, ?, ?, 'TEAM', ?, ?)", UUID.randomUUID().toString(), target.id(), workspaceId, "加入工作空间", owner.name() + " 邀请你加入「" + workspaceName(workspaceId) + "」");
            return Map.of("message", "成员已加入", "member", new MemberView(target.id(), target.name(), target.email(), role, Instant.now().toString()), "operator", owner.email());
        }

        @PutMapping("/{workspaceId}/members/{userId}")
        public Map<String, Object> updateMember(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String workspaceId, @PathVariable String userId, @RequestBody MemberRequest input) {
            requireOwner(authorization, workspaceId);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM workspace_members WHERE workspace_id = ? AND user_id = ?", Integer.class, workspaceId, userId) == 0) return Map.of("message", "成员不存在");
            if (jdbc.queryForObject("SELECT role FROM workspace_members WHERE workspace_id = ? AND user_id = ?", String.class, workspaceId, userId).equals("OWNER")) return Map.of("message", "不能修改所有者角色");
            String role = normalizeRole(input.role());
            jdbc.update("UPDATE workspace_members SET role = ? WHERE workspace_id = ? AND user_id = ?", role, workspaceId, userId);
            return Map.of("message", "成员角色已更新");
        }

        @DeleteMapping("/{workspaceId}/members/{userId}")
        public Map<String, Object> removeMember(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String workspaceId, @PathVariable String userId) {
            requireOwner(authorization, workspaceId);
            if (jdbc.queryForObject("SELECT role FROM workspace_members WHERE workspace_id = ? AND user_id = ?", String.class, workspaceId, userId).equals("OWNER")) return Map.of("message", "不能移除工作空间所有者");
            jdbc.update("DELETE FROM workspace_members WHERE workspace_id = ? AND user_id = ?", workspaceId, userId);
            return Map.of("message", "成员已移除");
        }

        private UserView requireOwner(String authorization, String workspaceId) {
            UserView user = requireUser(authorization);
            String role = jdbc.queryForObject("SELECT role FROM workspace_members WHERE workspace_id = ? AND user_id = ?", String.class, workspaceId, user.id());
            if (!"OWNER".equals(role)) throw new IllegalArgumentException("只有工作空间所有者可以管理成员");
            return user;
        }

        private void requireMembership(String userId, String workspaceId) {
            if (workspaceId == null || jdbc.queryForObject("SELECT COUNT(*) FROM workspace_members WHERE workspace_id = ? AND user_id = ?", Integer.class, workspaceId, userId) == 0) throw new IllegalArgumentException("工作空间不存在或无权访问");
        }

        private String workspaceName(String workspaceId) {
            return jdbc.queryForObject("SELECT name FROM workspaces WHERE id = ?", String.class, workspaceId);
        }

        private static String normalizeRole(String role) {
            return switch (role == null ? "" : role.toUpperCase()) {
                case "ADMIN", "EDITOR", "VIEWER" -> role.toUpperCase();
                default -> "VIEWER";
            };
        }

        private static String token(String header) { return header != null && header.startsWith("Bearer ") ? header.substring(7) : ""; }
    }

    @RestController
    @RequestMapping("/api/projects")
    @CrossOrigin(origins = "*")
    static class ProjectController {
        private final JdbcTemplate jdbc;
        ProjectController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @GetMapping
        public Map<String, Object> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @RequestParam(value = "workspaceId", required = false) String workspaceId) {
            UserView user = requireUser(authorization);
            String selectedWorkspace = workspaceId;
            if (selectedWorkspace == null || !isMember(user.id(), selectedWorkspace)) {
                selectedWorkspace = jdbc.queryForObject("SELECT workspace_id FROM workspace_members WHERE user_id = ? ORDER BY joined_at LIMIT 1", String.class, user.id());
            }
            if (selectedWorkspace == null) return Map.of("projects", List.of());
            String finalWorkspace = selectedWorkspace;
            List<ProjectView> projects = jdbc.query("SELECT id, workspace_id, name, description, tech_stack, status, progress, created_at, (SELECT COUNT(*) FROM tasks t WHERE t.project_id = projects.id) AS task_count FROM projects WHERE workspace_id = ? ORDER BY updated_at DESC", (result, row) -> new ProjectView(result.getString("id"), result.getString("workspace_id"), result.getString("name"), result.getString("description"), result.getString("tech_stack"), result.getString("status"), result.getInt("progress"), result.getInt("task_count"), result.getTimestamp("created_at").toInstant().toString()), finalWorkspace);
            return Map.of("workspaceId", finalWorkspace, "projects", projects);
        }

        @PostMapping
        public Map<String, Object> create(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody ProjectRequest input) {
            UserView user = requireUser(authorization);
            String workspaceId = input.workspaceId();
            if (workspaceId == null || !isMember(user.id(), workspaceId)) return Map.of("message", "无权访问该工作空间");
            String name = input.name() == null ? "" : input.name().trim();
            if (name.isBlank()) return Map.of("message", "请输入项目名称");
            String id = UUID.randomUUID().toString();
            String description = input.description() == null ? "" : input.description().trim();
            String techStack = input.techStack() == null || input.techStack().isBlank() ? "Vue 3 · HTML/CSS/JavaScript" : input.techStack().trim();
            jdbc.update("INSERT INTO projects (id, workspace_id, owner_id, name, description, tech_stack) VALUES (?, ?, ?, ?, ?, ?)", id, workspaceId, user.id(), name, description, techStack);
            seedReviewIssues(id);
            ProjectView project = new ProjectView(id, workspaceId, name, description, techStack, "IN_PROGRESS", 0, 0, Instant.now().toString());
            return Map.of("project", project);
        }

        @GetMapping("/{projectId}")
        public Map<String, Object> detail(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            ProjectView project = jdbc.queryForObject("SELECT id, workspace_id, name, description, tech_stack, status, progress, (SELECT COUNT(*) FROM tasks t WHERE t.project_id = projects.id) AS task_count, created_at FROM projects WHERE id = ?", (result, row) -> new ProjectView(result.getString("id"), result.getString("workspace_id"), result.getString("name"), result.getString("description"), result.getString("tech_stack"), result.getString("status"), result.getInt("progress"), result.getInt("task_count"), result.getTimestamp("created_at").toInstant().toString()), projectId);
            return Map.of("project", project);
        }

        @GetMapping("/{projectId}/files")
        public Map<String, Object> files(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            List<ProjectFileView> files = jdbc.query("SELECT path, content, updated_at FROM project_files WHERE project_id = ? ORDER BY path", (result, row) -> new ProjectFileView(result.getString("path"), result.getString("content"), result.getTimestamp("updated_at").toInstant().toString()), projectId);
            return Map.of("files", files);
        }

        @PostMapping("/{projectId}/files")
        public Map<String, Object> saveFile(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId, @RequestBody ProjectFileRequest input) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            String path = input.path() == null ? "" : input.path().trim();
            if (path.isBlank() || path.length() > 180) return Map.of("message", "文件路径无效");
            jdbc.update("INSERT INTO project_files (id, project_id, path, content) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE content = VALUES(content)", UUID.randomUUID().toString(), projectId, path, input.content() == null ? "" : input.content());
            return Map.of("message", "文件已保存");
        }

        @DeleteMapping("/{projectId}/files/{path:.+}")
        public Map<String, Object> deleteFile(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId, @PathVariable String path) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            jdbc.update("DELETE FROM project_files WHERE project_id = ? AND path = ?", projectId, path);
            return Map.of("message", "文件已删除");
        }

        @PutMapping("/{projectId}")
        public Map<String, Object> updateProject(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId, @RequestBody ProjectUpdateRequest input) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            String name = input.name() == null ? "" : input.name().trim();
            if (name.isBlank()) return Map.of("message", "请输入项目名称");
            jdbc.update("UPDATE projects SET name = ?, description = ?, tech_stack = ?, status = ? WHERE id = ?", name, input.description() == null ? "" : input.description().trim(), input.techStack() == null ? "Vue 3 · HTML/CSS/JavaScript" : input.techStack().trim(), input.status() == null ? "IN_PROGRESS" : input.status().trim().toUpperCase(), projectId);
            return detail(authorization, projectId);
        }

        @DeleteMapping("/{projectId}")
        public Map<String, Object> deleteProject(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            jdbc.update("DELETE FROM projects WHERE id = ?", projectId);
            return Map.of("message", "项目已删除");
        }

        @GetMapping("/{projectId}/reviews")
        public Map<String, Object> reviews(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            List<ReviewIssueView> issues = jdbc.query("SELECT id, level, title, file_path, status, created_at FROM review_issues WHERE project_id = ? ORDER BY FIELD(status, 'OPEN', 'RESOLVED'), created_at DESC", (result, row) -> new ReviewIssueView(result.getString("id"), result.getString("level"), result.getString("title"), result.getString("file_path"), result.getString("status"), result.getTimestamp("created_at").toInstant().toString()), projectId);
            return Map.of("issues", issues);
        }

        @PostMapping("/{projectId}/reviews/{issueId}/resolve")
        public Map<String, Object> resolveReview(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId, @PathVariable String issueId) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            jdbc.update("UPDATE review_issues SET status = 'RESOLVED', resolved_at = CURRENT_TIMESTAMP WHERE id = ? AND project_id = ?", issueId, projectId);
            return Map.of("message", "问题已标记为已处理");
        }

        private void seedReviewIssues(String projectId) {
            jdbc.update("INSERT INTO review_issues (id, project_id, level, title, file_path) VALUES (?, ?, 'HIGH', '按钮事件缺少异常处理', 'script.js:1')", UUID.randomUUID().toString(), projectId);
            jdbc.update("INSERT INTO review_issues (id, project_id, level, title, file_path) VALUES (?, ?, 'STYLE', '建议抽离重复颜色变量', 'style.css:1')", UUID.randomUUID().toString(), projectId);
            jdbc.update("INSERT INTO review_issues (id, project_id, level, title, file_path) VALUES (?, ?, 'OPTIMIZE', '可以增加移动端断点', 'style.css:8')", UUID.randomUUID().toString(), projectId);
        }

        @GetMapping("/{projectId}/tasks")
        public Map<String, Object> tasks(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            List<TaskView> tasks = jdbc.query("SELECT id, project_id, title, status, priority, created_at, completed_at FROM tasks WHERE project_id = ? ORDER BY created_at DESC", (result, row) -> new TaskView(result.getString("id"), result.getString("project_id"), result.getString("title"), result.getString("status"), result.getString("priority"), result.getTimestamp("created_at").toInstant().toString(), result.getTimestamp("completed_at") == null ? null : result.getTimestamp("completed_at").toInstant().toString()), projectId);
            return Map.of("tasks", tasks);
        }

        @PostMapping("/{projectId}/tasks")
        public Map<String, Object> createTask(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId, @RequestBody TaskRequest input) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            String title = input.title() == null ? "" : input.title().trim();
            if (title.isBlank()) return Map.of("message", "请输入任务名称");
            String id = UUID.randomUUID().toString();
            String priority = input.priority() == null || input.priority().isBlank() ? "MEDIUM" : input.priority().trim().toUpperCase();
            jdbc.update("INSERT INTO tasks (id, project_id, title, priority) VALUES (?, ?, ?, ?)", id, projectId, title, priority);
            return Map.of("task", new TaskView(id, projectId, title, "TODO", priority, Instant.now().toString(), null));
        }

        @PostMapping("/{projectId}/tasks/{taskId}/complete")
        public Map<String, Object> completeTask(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId, @PathVariable String taskId) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            jdbc.update("UPDATE tasks SET status = 'DONE', completed_at = CURRENT_TIMESTAMP WHERE id = ? AND project_id = ?", taskId, projectId);
            jdbc.update("UPDATE projects p SET progress = LEAST(100, (SELECT COALESCE(SUM(status = 'DONE') * 100 / NULLIF(COUNT(*), 0), 0) FROM tasks WHERE project_id = p.id)) WHERE id = ?", projectId);
            return Map.of("message", "任务已完成");
        }

        @PostMapping("/{projectId}/tasks/{taskId}/status")
        public Map<String, Object> updateTaskStatus(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId, @PathVariable String taskId, @RequestBody TaskStatusRequest input) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            String status = input.status() == null ? "TODO" : input.status().trim().toUpperCase();
            if (!List.of("TODO", "IN_PROGRESS", "DONE").contains(status)) return Map.of("message", "任务状态无效");
            jdbc.update("UPDATE tasks SET status = ?, completed_at = ? WHERE id = ? AND project_id = ?", status, "DONE".equals(status) ? Timestamp.from(Instant.now()) : null, taskId, projectId);
            jdbc.update("UPDATE projects p SET progress = LEAST(100, (SELECT COALESCE(SUM(status = 'DONE') * 100 / NULLIF(COUNT(*), 0), 0) FROM tasks WHERE project_id = p.id)) WHERE id = ?", projectId);
            return Map.of("message", "任务状态已更新");
        }

        @PutMapping("/{projectId}/tasks/{taskId}")
        public Map<String, Object> updateTask(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId, @PathVariable String taskId, @RequestBody TaskRequest input) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            String title = input.title() == null ? "" : input.title().trim();
            if (title.isBlank()) return Map.of("message", "请输入任务名称");
            jdbc.update("UPDATE tasks SET title = ?, priority = ? WHERE id = ? AND project_id = ?", title, input.priority() == null ? "MEDIUM" : input.priority().trim().toUpperCase(), taskId, projectId);
            return Map.of("message", "任务已更新");
        }

        @DeleteMapping("/{projectId}/tasks/{taskId}")
        public Map<String, Object> deleteTask(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId, @PathVariable String taskId) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            jdbc.update("DELETE FROM tasks WHERE id = ? AND project_id = ?", taskId, projectId);
            jdbc.update("UPDATE projects p SET progress = LEAST(100, (SELECT COALESCE(SUM(status = 'DONE') * 100 / NULLIF(COUNT(*), 0), 0) FROM tasks WHERE project_id = p.id)) WHERE id = ?", projectId);
            return Map.of("message", "任务已删除");
        }

        @GetMapping("/{projectId}/deployments")
        public Map<String, Object> deployments(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            List<DeploymentView> deployments = jdbc.query("SELECT id, project_id, environment, version, status, preview_url, created_at FROM deployments WHERE project_id = ? ORDER BY created_at DESC", (result, row) -> new DeploymentView(result.getString("id"), result.getString("project_id"), result.getString("environment"), result.getString("version"), result.getString("status"), result.getString("preview_url"), result.getTimestamp("created_at").toInstant().toString()), projectId);
            return Map.of("deployments", deployments);
        }

        @PostMapping("/{projectId}/deployments")
        public Map<String, Object> publish(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId, @RequestBody DeploymentRequest input) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            String environment = normalizeEnvironment(input.environment());
            int next = jdbc.queryForObject("SELECT COUNT(*) FROM deployments WHERE project_id = ?", Integer.class, projectId) + 1;
            String version = "v1." + next;
            String id = UUID.randomUUID().toString();
            String previewUrl = "http://127.0.0.1:4173/workspace?projectId=" + projectId + "&deploymentId=" + id;
            jdbc.update("UPDATE deployments SET status = 'ARCHIVED' WHERE project_id = ? AND environment = ? AND status = 'PUBLISHED'", projectId, environment);
            jdbc.update("INSERT INTO deployments (id, project_id, user_id, environment, version, status, preview_url) VALUES (?, ?, ?, ?, ?, 'PUBLISHED', ?)", id, projectId, user.id(), environment, version, previewUrl);
            jdbc.update("INSERT INTO notifications (id, user_id, type, title, content) VALUES (?, ?, 'RELEASE', ?, ?)", UUID.randomUUID().toString(), user.id(), "发布完成", "项目已发布 " + version + "（" + ("PRODUCTION".equals(environment) ? "生产" : "预览") + "环境）");
            DeploymentView deployment = jdbc.queryForObject("SELECT id, project_id, environment, version, status, preview_url, created_at FROM deployments WHERE id = ?", (result, row) -> new DeploymentView(result.getString("id"), result.getString("project_id"), result.getString("environment"), result.getString("version"), result.getString("status"), result.getString("preview_url"), result.getTimestamp("created_at").toInstant().toString()), id);
            return Map.of("message", "发布成功", "deployment", deployment);
        }

        @PostMapping("/{projectId}/deployments/{deploymentId}/rollback")
        public Map<String, Object> rollback(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String projectId, @PathVariable String deploymentId) {
            UserView user = requireUser(authorization);
            requireProjectMember(user.id(), projectId);
            String environment = jdbc.queryForObject("SELECT environment FROM deployments WHERE id = ? AND project_id = ?", String.class, deploymentId, projectId);
            jdbc.update("UPDATE deployments SET status = 'ARCHIVED' WHERE project_id = ? AND environment = ? AND status = 'PUBLISHED'", projectId, environment);
            jdbc.update("UPDATE deployments SET status = 'PUBLISHED' WHERE id = ? AND project_id = ?", deploymentId, projectId);
            return Map.of("message", "已回滚到 " + deploymentId);
        }

        private static String normalizeEnvironment(String value) {
            return "PRODUCTION".equalsIgnoreCase(value) ? "PRODUCTION" : "PREVIEW";
        }

        private boolean isMember(String userId, String workspaceId) {
            return !jdbc.queryForList("SELECT workspace_id FROM workspace_members WHERE workspace_id = ? AND user_id = ?", String.class, workspaceId, userId).isEmpty();
        }
        private void requireProjectMember(String userId, String projectId) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM projects p JOIN workspace_members wm ON wm.workspace_id = p.workspace_id WHERE p.id = ? AND wm.user_id = ?", Integer.class, projectId, userId) == 0) throw new IllegalArgumentException("项目不存在或无权访问");
        }
    }

    @RestController
    @RequestMapping("/api/time-sessions")
    @CrossOrigin(origins = "*")
    static class TimeSessionController {
        private final JdbcTemplate jdbc;
        TimeSessionController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @GetMapping("/active")
        public Map<String, Object> active(@RequestHeader(value = "Authorization", required = false) String authorization) {
            UserView user = requireUser(authorization);
            List<TimeSessionView> sessions = jdbc.query("SELECT id, project_id, task_id, started_at, ended_at, duration_seconds, status FROM time_sessions WHERE user_id = ? AND status = 'RUNNING' ORDER BY started_at DESC LIMIT 1", (result, row) -> toSession(result), user.id());
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("session", sessions.isEmpty() ? null : sessions.getFirst());
            return response;
        }

        @PostMapping("/start")
        public Map<String, Object> start(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody TimeSessionRequest input) {
            UserView user = requireUser(authorization);
            if (input.projectId() == null) return Map.of("message", "请选择项目");
            new ProjectController(jdbc).requireProjectMember(user.id(), input.projectId());
            jdbc.update("UPDATE time_sessions SET ended_at = CURRENT_TIMESTAMP, duration_seconds = TIMESTAMPDIFF(SECOND, started_at, CURRENT_TIMESTAMP), status = 'STOPPED' WHERE user_id = ? AND status = 'RUNNING'", user.id());
            String id = UUID.randomUUID().toString();
            Timestamp started = Timestamp.from(Instant.now());
            jdbc.update("INSERT INTO time_sessions (id, user_id, project_id, task_id, started_at) VALUES (?, ?, ?, ?, ?)", id, user.id(), input.projectId(), input.taskId(), started);
            return Map.of("session", new TimeSessionView(id, input.projectId(), input.taskId(), started.toInstant().toString(), null, 0, "RUNNING"));
        }

        @PostMapping("/{sessionId}/stop")
        public Map<String, Object> stop(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String sessionId) {
            UserView user = requireUser(authorization);
            jdbc.update("UPDATE time_sessions SET ended_at = CURRENT_TIMESTAMP, duration_seconds = TIMESTAMPDIFF(SECOND, started_at, CURRENT_TIMESTAMP), status = 'STOPPED' WHERE id = ? AND user_id = ? AND status = 'RUNNING'", sessionId, user.id());
            return Map.of("message", "计时已结束");
        }

        private TimeSessionView toSession(java.sql.ResultSet result) throws java.sql.SQLException {
            return new TimeSessionView(result.getString("id"), result.getString("project_id"), result.getString("task_id"), result.getTimestamp("started_at").toInstant().toString(), result.getTimestamp("ended_at") == null ? null : result.getTimestamp("ended_at").toInstant().toString(), result.getLong("duration_seconds"), result.getString("status"));
        }
    }

    @RestController
    @RequestMapping("/api/stats")
    @CrossOrigin(origins = "*")
    static class StatsController {
        private final JdbcTemplate jdbc;
        StatsController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @GetMapping
        public Map<String, Object> summary(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @RequestParam(value = "workspaceId", required = false) String workspaceId) {
            UserView user = requireUser(authorization);
            String selectedWorkspace = workspaceId;
            if (selectedWorkspace == null || !isMember(user.id(), selectedWorkspace)) {
                selectedWorkspace = jdbc.queryForObject("SELECT workspace_id FROM workspace_members WHERE user_id = ? ORDER BY joined_at LIMIT 1", String.class, user.id());
            }
            if (selectedWorkspace == null) return Map.of("projectCount", 0, "taskCount", 0, "completedTaskCount", 0, "focusSeconds", 0L);
            Long projectCount = jdbc.queryForObject("SELECT COUNT(*) FROM projects WHERE workspace_id = ?", Long.class, selectedWorkspace);
            Long taskCount = jdbc.queryForObject("SELECT COUNT(*) FROM tasks t JOIN projects p ON p.id = t.project_id WHERE p.workspace_id = ?", Long.class, selectedWorkspace);
            Long completedTaskCount = jdbc.queryForObject("SELECT COUNT(*) FROM tasks t JOIN projects p ON p.id = t.project_id WHERE p.workspace_id = ? AND t.status = 'DONE'", Long.class, selectedWorkspace);
            Long focusSeconds = jdbc.queryForObject("SELECT COALESCE(SUM(ts.duration_seconds), 0) FROM time_sessions ts JOIN projects p ON p.id = ts.project_id WHERE ts.user_id = ? AND p.workspace_id = ?", Long.class, user.id(), selectedWorkspace);
            return Map.of("workspaceId", selectedWorkspace, "projectCount", projectCount == null ? 0 : projectCount, "taskCount", taskCount == null ? 0 : taskCount, "completedTaskCount", completedTaskCount == null ? 0 : completedTaskCount, "focusSeconds", focusSeconds == null ? 0 : focusSeconds);
        }

        private boolean isMember(String userId, String workspaceId) {
            return !jdbc.queryForList("SELECT workspace_id FROM workspace_members WHERE workspace_id = ? AND user_id = ?", String.class, workspaceId, userId).isEmpty();
        }
    }

    @RestController
    @RequestMapping("/api/checkins")
    @CrossOrigin(origins = "*")
    static class CheckinController {
        private final JdbcTemplate jdbc;
        CheckinController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @GetMapping
        public Map<String, Object> summary(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestParam String workspaceId) {
            UserView user = requireUser(authorization);
            requireMembership(user.id(), workspaceId);
            List<LocalDate> dates = jdbc.query("SELECT checkin_date FROM checkins WHERE user_id = ? AND workspace_id = ? ORDER BY checkin_date DESC LIMIT 90", (result, row) -> result.getDate("checkin_date").toLocalDate(), user.id(), workspaceId);
            LocalDate today = LocalDate.now();
            boolean checkedInToday = dates.contains(today);
            int streak = 0;
            LocalDate cursor = checkedInToday ? today : today.minusDays(1);
            for (LocalDate date : dates) {
                if (date.equals(cursor)) { streak++; cursor = cursor.minusDays(1); }
                else if (date.isBefore(cursor)) break;
            }
            return Map.of("checkedInToday", checkedInToday, "streak", streak, "total", dates.size(), "dates", dates.stream().map(LocalDate::toString).toList());
        }

        @PostMapping
        public Map<String, Object> checkin(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody CheckinRequest input) {
            UserView user = requireUser(authorization);
            requireMembership(user.id(), input.workspaceId());
            jdbc.update("INSERT IGNORE INTO checkins (id, user_id, workspace_id, checkin_date) VALUES (?, ?, ?, ?)", UUID.randomUUID().toString(), user.id(), input.workspaceId(), java.sql.Date.valueOf(LocalDate.now()));
            return summary(authorization, input.workspaceId());
        }

        private void requireMembership(String userId, String workspaceId) {
            if (workspaceId == null || jdbc.queryForObject("SELECT COUNT(*) FROM workspace_members WHERE workspace_id = ? AND user_id = ?", Integer.class, workspaceId, userId) == 0) throw new IllegalArgumentException("工作空间不存在或无权访问");
        }
    }

    @RestController
    @RequestMapping("/api/knowledge")
    @CrossOrigin(origins = "*")
    static class KnowledgeController {
        private final JdbcTemplate jdbc;
        KnowledgeController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @GetMapping
        public Map<String, Object> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @RequestParam String workspaceId) {
            UserView user = requireUser(authorization);
            requireMembership(user.id(), workspaceId);
            List<KnowledgeView> entries = jdbc.query("SELECT id, workspace_id, title, content, tags, created_at, updated_at FROM knowledge_entries WHERE workspace_id = ? ORDER BY updated_at DESC", (result, row) -> new KnowledgeView(result.getString("id"), result.getString("workspace_id"), result.getString("title"), result.getString("content"), result.getString("tags"), result.getTimestamp("created_at").toInstant().toString(), result.getTimestamp("updated_at").toInstant().toString()), workspaceId);
            return Map.of("entries", entries);
        }

        @PostMapping
        public Map<String, Object> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @RequestBody KnowledgeRequest input) {
            UserView user = requireUser(authorization);
            String workspaceId = input.workspaceId();
            requireMembership(user.id(), workspaceId);
            String title = clean(input.title());
            if (title.isBlank()) return Map.of("message", "请输入知识库标题");
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO knowledge_entries (id, workspace_id, owner_id, title, content, tags) VALUES (?, ?, ?, ?, ?, ?)", id, workspaceId, user.id(), title, input.content() == null ? "" : input.content(), input.tags() == null ? "" : input.tags().trim());
            return Map.of("entry", find(id, workspaceId));
        }

        @PutMapping("/{entryId}")
        public Map<String, Object> update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @PathVariable String entryId, @RequestBody KnowledgeRequest input) {
            UserView user = requireUser(authorization);
            requireMembership(user.id(), input.workspaceId());
            String title = clean(input.title());
            if (title.isBlank()) return Map.of("message", "请输入知识库标题");
            int changed = jdbc.update("UPDATE knowledge_entries SET title = ?, content = ?, tags = ? WHERE id = ? AND workspace_id = ?", title, input.content() == null ? "" : input.content(), input.tags() == null ? "" : input.tags().trim(), entryId, input.workspaceId());
            if (changed == 0) return Map.of("message", "知识库条目不存在");
            return Map.of("entry", find(entryId, input.workspaceId()));
        }

        @DeleteMapping("/{entryId}")
        public Map<String, Object> delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @PathVariable String entryId, @RequestParam String workspaceId) {
            UserView user = requireUser(authorization);
            requireMembership(user.id(), workspaceId);
            jdbc.update("DELETE FROM knowledge_entries WHERE id = ? AND workspace_id = ?", entryId, workspaceId);
            return Map.of("message", "知识库条目已删除");
        }

        private KnowledgeView find(String id, String workspaceId) {
            return jdbc.queryForObject("SELECT id, workspace_id, title, content, tags, created_at, updated_at FROM knowledge_entries WHERE id = ? AND workspace_id = ?", (result, row) -> new KnowledgeView(result.getString("id"), result.getString("workspace_id"), result.getString("title"), result.getString("content"), result.getString("tags"), result.getTimestamp("created_at").toInstant().toString(), result.getTimestamp("updated_at").toInstant().toString()), id, workspaceId);
        }

        private void requireMembership(String userId, String workspaceId) {
            if (workspaceId == null || jdbc.queryForObject("SELECT COUNT(*) FROM workspace_members WHERE workspace_id = ? AND user_id = ?", Integer.class, workspaceId, userId) == 0) throw new IllegalArgumentException("工作空间不存在或无权访问");
        }

        private static String clean(String value) { return value == null ? "" : value.trim(); }
    }

    @RestController
    @RequestMapping("/api/notifications")
    @CrossOrigin(origins = "*")
    static class NotificationController {
        private final JdbcTemplate jdbc;
        NotificationController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @GetMapping
        public Map<String, Object> list(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestParam(defaultValue = "30") int limit) {
            UserView user = requireUser(authorization);
            int safeLimit = Math.min(Math.max(limit, 1), 100);
            List<NotificationView> notifications = jdbc.query("SELECT id, type, title, content, read_at, created_at FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT " + safeLimit, (result, row) -> new NotificationView(result.getString("id"), result.getString("type"), result.getString("title"), result.getString("content"), result.getTimestamp("read_at") == null ? null : result.getTimestamp("read_at").toInstant().toString(), result.getTimestamp("created_at").toInstant().toString()), user.id());
            long unread = notifications.stream().filter(item -> item.readAt() == null).count();
            return Map.of("notifications", notifications, "unreadCount", unread);
        }

        @PostMapping("/{notificationId}/read")
        public Map<String, Object> read(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String notificationId) {
            UserView user = requireUser(authorization);
            jdbc.update("UPDATE notifications SET read_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?", notificationId, user.id());
            return Map.of("message", "通知已读");
        }

        @PostMapping("/read-all")
        public Map<String, Object> readAll(@RequestHeader(value = "Authorization", required = false) String authorization) {
            UserView user = requireUser(authorization);
            jdbc.update("UPDATE notifications SET read_at = CURRENT_TIMESTAMP WHERE user_id = ? AND read_at IS NULL", user.id());
            return Map.of("message", "通知已全部读");
        }
    }

    private static UserView requireUser(String authorization) {
        UserView user = findUser(token(authorization));
        if (user == null) throw new IllegalArgumentException("登录已过期");
        return user;
    }
    private static UserView findUser(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) return null;
        UserView cached = SESSIONS.get(sessionToken);
        if (cached != null) return cached;
        if (DATABASE == null) return null;
        List<UserView> users = DATABASE.query("SELECT u.id, u.name, u.email FROM auth_sessions s JOIN users u ON u.id = s.user_id WHERE s.token = ? AND s.expires_at > CURRENT_TIMESTAMP", (result, row) -> new UserView(result.getString("id"), result.getString("name"), result.getString("email")), sessionToken);
        UserView user = users.isEmpty() ? null : users.getFirst();
        if (user != null) SESSIONS.put(sessionToken, user);
        return user;
    }
    private static String token(String header) { return header != null && header.startsWith("Bearer ") ? header.substring(7) : ""; }

    @RestController
    @RequestMapping("/api/ai")
    @CrossOrigin(origins = "*")
    static class AiController {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        private final JdbcTemplate jdbc;
        private final List<AiConfig> configs = new ArrayList<>();

        AiController(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
            configs.addAll(jdbc.query("SELECT id, name, base_url, api_key, model, active FROM model_configs WHERE owner_id IS NULL ORDER BY created_at", (result, row) -> new AiConfig(result.getString("id"), result.getString("name"), result.getString("base_url"), result.getString("api_key"), result.getString("model"), result.getBoolean("active"))));
            if (configs.isEmpty()) {
                AiConfig defaultConfig = new AiConfig("local-ollama", "本地 Ollama", "http://127.0.0.1:11434/v1", "", "qwen2.5-coder:7b", true);
                configs.add(defaultConfig);
                jdbc.update("INSERT IGNORE INTO model_configs (id, owner_id, name, base_url, api_key, model, active) VALUES (?, NULL, ?, ?, ?, ?, TRUE)", defaultConfig.id(), defaultConfig.name(), defaultConfig.baseUrl(), defaultConfig.apiKey(), defaultConfig.model());
            }
        }

        @GetMapping("/config")
        public Map<String, Object> getConfig() {
            AiConfig active = activeConfig();
            return Map.of("active", publicConfig(active), "configs", configs.stream().map(this::publicConfig).toList());
        }

        @PostMapping("/config")
        public synchronized Map<String, Object> saveConfig(@RequestBody AiConfig input) {
            String id = input.id() == null || input.id().isBlank() ? UUID.randomUUID().toString() : input.id();
            String name = input.name() == null || input.name().isBlank() ? "未命名模型" : input.name();
            String baseUrl = input.baseUrl() == null || input.baseUrl().isBlank() ? "http://127.0.0.1:11434/v1" : input.baseUrl().replaceAll("/+$", "");
            String model = input.model() == null || input.model().isBlank() ? "qwen2.5-coder:7b" : input.model();
            AiConfig existing = configs.stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
            String apiKey = input.apiKey() == null ? "" : input.apiKey();
            if (apiKey.isBlank() && existing != null) apiKey = existing.apiKey();
            configs.removeIf(item -> item.id().equals(id));
            boolean active = input.active() || configs.isEmpty();
            if (active) configs.replaceAll(item -> new AiConfig(item.id(), item.name(), item.baseUrl(), item.apiKey(), item.model(), false));
            AiConfig saved = new AiConfig(id, name, baseUrl, apiKey, model, active);
            configs.add(saved);
            jdbc.update("INSERT INTO model_configs (id, owner_id, name, base_url, api_key, model, active) VALUES (?, NULL, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), base_url = VALUES(base_url), api_key = VALUES(api_key), model = VALUES(model), active = VALUES(active)", id, name, baseUrl, apiKey, model, active);
            if (active) jdbc.update("UPDATE model_configs SET active = FALSE WHERE owner_id IS NULL AND id <> ?", id);
            return Map.of("message", "模型配置已保存", "active", publicConfig(activeConfig()), "configs", configs.stream().map(this::publicConfig).toList());
        }

        @PostMapping("/config/{id}/activate")
        public synchronized Map<String, Object> activate(@PathVariable String id) {
            if (configs.stream().noneMatch(item -> item.id().equals(id))) throw new IllegalArgumentException("模型配置不存在");
            configs.replaceAll(item -> new AiConfig(item.id(), item.name(), item.baseUrl(), item.apiKey(), item.model(), item.id().equals(id)));
            jdbc.update("UPDATE model_configs SET active = (id = ?) WHERE owner_id IS NULL", id);
            return Map.of("message", "已切换当前模型", "active", publicConfig(activeConfig()));
        }

        @DeleteMapping("/config/{id}")
        public synchronized Map<String, Object> delete(@PathVariable String id) {
            if (configs.size() == 1) throw new IllegalArgumentException("至少保留一个模型配置");
            boolean deletingActive = activeConfig().id().equals(id);
            configs.removeIf(item -> item.id().equals(id));
            jdbc.update("DELETE FROM model_configs WHERE owner_id IS NULL AND id = ?", id);
            if (deletingActive) {
                AiConfig first = configs.getFirst();
                configs.replaceAll(item -> new AiConfig(item.id(), item.name(), item.baseUrl(), item.apiKey(), item.model(), item.id().equals(first.id())));
                jdbc.update("UPDATE model_configs SET active = (id = ?) WHERE owner_id IS NULL", first.id());
            }
            return Map.of("message", "模型配置已删除", "configs", configs.stream().map(this::publicConfig).toList(), "active", publicConfig(activeConfig()));
        }

        @PostMapping("/config/{id}/test")
        public Map<String, Object> test(@PathVariable String id) throws Exception {
            AiConfig target = configs.stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("模型配置不存在"));
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(target.baseUrl() + "/models")).timeout(Duration.ofSeconds(15)).GET();
            if (!target.apiKey().isBlank()) request.header("Authorization", "Bearer " + target.apiKey());
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return Map.of("ok", response.statusCode() < 400, "status", response.statusCode(), "message", response.statusCode() < 400 ? "连接成功" : "接口返回 HTTP " + response.statusCode());
        }

        @PostMapping(value = "/code/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, String> generate(@RequestBody GenerateRequest input) throws Exception {
            if (input.prompt() == null || input.prompt().isBlank()) throw new IllegalArgumentException("请输入代码需求");
            AiConfig config = activeConfig();
            String endpoint = config.baseUrl() + "/chat/completions";
            String body = "{\"model\":\"" + json(config.model()) + "\",\"temperature\":0.2,\"messages\":[{\"role\":\"system\",\"content\":\"你是星码空间的代码生成助手，只输出可运行的代码或简短说明。\"},{\"role\":\"user\",\"content\":\"" + json(input.prompt()) + "\"}]}";
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(90)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            if (!config.apiKey().isBlank()) request.header("Authorization", "Bearer " + config.apiKey());
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) throw new IllegalStateException("模型接口返回 HTTP " + response.statusCode());
            String content = extractContent(response.body());
            return Map.of("content", content, "model", config.model());
        }

        private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n"); }
        private static String extractContent(String body) {
            String marker = "\"content\":\"";
            int start = body.indexOf(marker);
            if (start < 0) return body;
            start += marker.length();
            StringBuilder result = new StringBuilder();
            boolean escaped = false;
            for (int i = start; i < body.length(); i++) {
                char c = body.charAt(i);
                if (escaped) { result.append(c == 'n' ? '\n' : c == 'r' ? '\r' : c); escaped = false; continue; }
                if (c == '\\') { escaped = true; continue; }
                if (c == '"') break;
                result.append(c);
            }
            return result.toString();
        }
        private AiConfig activeConfig() { return configs.stream().filter(AiConfig::active).findFirst().orElse(configs.getFirst()); }
        private Map<String, Object> publicConfig(AiConfig config) { return Map.of("id", config.id(), "name", config.name(), "baseUrl", config.baseUrl(), "model", config.model(), "active", config.active(), "hasApiKey", !config.apiKey().isBlank()); }
    }

    record AiConfig(String id, String name, String baseUrl, String apiKey, String model, boolean active) {}
    record GenerateRequest(String prompt) {}
    record RegisterRequest(String name, String email, String password) {}
    record LoginRequest(String email, String password) {}
    record StoredUser(String id, String name, String email, String passwordHash) {}
    record UserView(String id, String name, String email) {}
    record WorkspaceRequest(String name, String description) {}
    record WorkspaceView(String id, String name, String description, String role) {}
    record MemberRequest(String email, String role) {}
    record MemberView(String id, String name, String email, String role, String joinedAt) {}
    record ProjectRequest(String workspaceId, String name, String description, String techStack) {}
    record ProjectUpdateRequest(String name, String description, String techStack, String status) {}
    record ReviewIssueView(String id, String level, String title, String filePath, String status, String createdAt) {}
    record ProjectView(String id, String workspaceId, String name, String description, String techStack, String status, int progress, int taskCount, String createdAt) {}
    record TaskRequest(String title, String priority) {}
    record TaskView(String id, String projectId, String title, String status, String priority, String createdAt, String completedAt) {}
    record TimeSessionRequest(String projectId, String taskId) {}
    record TimeSessionView(String id, String projectId, String taskId, String startedAt, String endedAt, long durationSeconds, String status) {}
    record ProjectFileRequest(String path, String content) {}
    record ProjectFileView(String path, String content, String updatedAt) {}
    record TaskStatusRequest(String status) {}
    record CheckinRequest(String workspaceId) {}
    record DeploymentRequest(String environment) {}
    record DeploymentView(String id, String projectId, String environment, String version, String status, String previewUrl, String createdAt) {}
    record KnowledgeRequest(String workspaceId, String title, String content, String tags) {}
    record KnowledgeView(String id, String workspaceId, String title, String content, String tags, String createdAt, String updatedAt) {}
    record NotificationView(String id, String type, String title, String content, String readAt, String createdAt) {}

    record HealthResponse(String status, String message, String stage) {}
}
