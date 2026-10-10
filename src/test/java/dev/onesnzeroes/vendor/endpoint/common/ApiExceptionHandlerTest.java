package dev.onesnzeroes.vendor.endpoint.common;

import dev.onesnzeroes.vendor.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void responseStatusKeepsStatusAndReason() {
        ProblemDetail problem = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        assertEquals(404, problem.getStatus());
        assertEquals("Order not found", problem.getDetail());
    }

    @Test
    void foreignKeyViolationIs409WithTheDatabaseMessage() {
        ProblemDetail problem = handler.handleDataIntegrityViolation(TestData.foreignKeyViolation());

        assertEquals(409, problem.getStatus());
        assertTrue(problem.getDetail().startsWith("Operation violates a database constraint: "));
        assertTrue(problem.getDetail().contains("foreign key constraint fails"));
    }

    @Test
    void triggerSignalIs409WithoutTheConnectionPrefix() {
        ProblemDetail problem = handler.handleDatabaseError(TestData.triggerError("Insufficient stock for product 8"));

        assertEquals(409, problem.getStatus());
        assertEquals("Insufficient stock for product 8", problem.getDetail());
    }

    @Test
    void triggerSignalDeepInsideATransactionExceptionIsFound() {
        SQLException sql = new SQLException("Order status cannot change", "45000");
        TransactionSystemException ex = new TransactionSystemException("commit failed",
                new RuntimeException("wrapper", sql));

        ProblemDetail problem = handler.handleDatabaseError(ex);

        assertEquals(409, problem.getStatus());
        assertEquals("Order status cannot change", problem.getDetail());
    }

    @Test
    void triggerSignalWithoutAMessageGetsADefault() {
        UncategorizedSQLException ex = new UncategorizedSQLException("trigger", "UPDATE orders",
                new SQLException(null, "45000"));

        ProblemDetail problem = handler.handleDatabaseError(ex);

        assertEquals(409, problem.getStatus());
        assertEquals("Business rule violated", problem.getDetail());
    }

    @Test
    void otherSqlStatesAre500() {
        UncategorizedSQLException ex = new UncategorizedSQLException("query", "SELECT 1",
                new SQLException("Deadlock found", "40001"));

        ProblemDetail problem = handler.handleDatabaseError(ex);

        assertEquals(500, problem.getStatus());
        assertEquals("Database error", problem.getDetail());
    }

    @Test
    void databaseErrorWithoutSqlExceptionIs500() {
        ProblemDetail problem = handler.handleDatabaseError(new DataAccessResourceFailureException("connection lost"));

        assertEquals(500, problem.getStatus());
        assertEquals("Database error", problem.getDetail());
    }
}
