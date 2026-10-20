package com.billion21.work;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = "DB_PASSWORD=integration-test-only")
@AutoConfigureMockMvc
class FoundationIntegrationTest {
    @Container
    @ServiceConnection
    static final MariaDBContainer<?> db = new MariaDBContainer<>("mariadb:11.4.5");

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test
    void migrationCreatesAllBusinessTables() {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_name IN
            ('TB_USER','TB_PROJECT','TB_PROJECT_MEMBER','TB_TASK',
             'TB_TASK_SUBMISSION','TB_TASK_COMMENT','TB_TASK_FILE','TB_TASK_HISTORY')
            """, Integer.class);
        assertThat(count).isEqualTo(8);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND success=1", Integer.class)).isEqualTo(1);
    }

    @Test
    void onlySystemStatusIsPublic() throws Exception {
        mvc.perform(get("/api/v1/system/info"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("billion-work"));
        mvc.perform(get("/api/v1/system/health"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void databasePreventsDuplicatePendingSubmissionsAndCrossTaskFiles() {
        jdbc.update("""
            INSERT INTO TB_USER(id,login_id,password_hash,name,created_at,updated_at)
            VALUES(9001,'fixture-user','not-a-real-password-hash','테스트',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """);
        jdbc.update("""
            INSERT INTO TB_PROJECT(id,name,created_by,created_at,updated_at)
            VALUES(9001,'fixture',9001,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """);
        for (long taskId : new long[]{9001, 9002}) {
            jdbc.update("""
                INSERT INTO TB_TASK(id,project_id,requester_id,assignee_id,title,created_at,updated_at)
                VALUES(?,9001,9001,9001,'fixture task',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, taskId);
        }
        jdbc.update("""
            INSERT INTO TB_TASK_SUBMISSION(id,task_id,round_no,submitted_by,result,submitted_at)
            VALUES(9001,9001,1,9001,'완료 결과',UTC_TIMESTAMP(6))
            """);
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO TB_TASK_SUBMISSION(task_id,round_no,submitted_by,result,submitted_at)
            VALUES(9001,2,9001,'중복 완료 요청',UTC_TIMESTAMP(6))
            """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO TB_TASK_FILE(task_id,submission_id,kind,original_name,storage_key,content_type,size_bytes,uploaded_by,created_at)
            VALUES(9002,9001,'RESULT','file.txt','fixture-key','text/plain',1,9001,UTC_TIMESTAMP(6))
            """)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("""
            UPDATE TB_TASK_SUBMISSION SET status='APPROVED',resolved_by=9001,resolved_at=UTC_TIMESTAMP(6) WHERE id=9001
            """);
        jdbc.update("""
            INSERT INTO TB_TASK_SUBMISSION(task_id,round_no,submitted_by,result,submitted_at)
            VALUES(9001,2,9001,'다음 회차',UTC_TIMESTAMP(6))
            """);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM TB_TASK_SUBMISSION WHERE task_id=9001", Integer.class)).isEqualTo(2);
    }
}
