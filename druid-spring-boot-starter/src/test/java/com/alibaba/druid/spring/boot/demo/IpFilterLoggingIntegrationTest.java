package com.alibaba.druid.spring.boot.demo;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.spring.boot.demo.util.CapturingTestStatLogger;
import com.alibaba.druid.stat.DruidDataSourceStatLogger;
import com.alibaba.druid.stat.custom.IpFilteredStatLogger;
import com.alibaba.druid.support.http.util.ClientIpContext; // For manual cleanup in edge cases
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    classes = DemoApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.druid.time-between-log-stats-millis=100" // Short interval for testing
})
public class IpFilterLoggingIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DruidDataSource druidDataSource;

    private CapturingTestStatLogger testDelegateLogger;
    private IpFilteredStatLogger ipFilteredStatLogger; // The actual bean

    // Helper method to clear the static map in ClientIpContext for test isolation
    private void fullyClearClientIpContextMap() throws Exception {
        Field field = ClientIpContext.class.getDeclaredField("active127_0_0_1_Threads");
        field.setAccessible(true);
        ConcurrentHashMap<String, Boolean> map = (ConcurrentHashMap<String, Boolean>) field.get(null);
        map.clear();
        ClientIpContext.currentClientIp.remove();
    }

    @BeforeEach
    void setUp() throws Exception {
        // Get the IpFilteredStatLogger bean that was configured by BeanPostProcessor
        DruidDataSourceStatLogger currentLogger = druidDataSource.getStatLogger();
        if (!(currentLogger instanceof IpFilteredStatLogger)) {
            fail("DruidDataSource is not using IpFilteredStatLogger. Check BeanPostProcessor setup. Found: " +
                 (currentLogger != null ? currentLogger.getClass().getName() : "null"));
        }
        ipFilteredStatLogger = (IpFilteredStatLogger) currentLogger;

        // Create and set our test delegate
        testDelegateLogger = new CapturingTestStatLogger();
        // Use reflection to set the delegate since the setter is package-private
        // and this test class is in a different package.
        Field delegateField = IpFilteredStatLogger.class.getDeclaredField("delegate");
        delegateField.setAccessible(true);
        delegateField.set(ipFilteredStatLogger, testDelegateLogger);
        
        testDelegateLogger.reset();
        fullyClearClientIpContextMap(); // Ensure ClientIpContext is clean
    }
    
    @AfterEach
    void tearDown() throws Exception {
        // Restore original delegate? Not strictly necessary if each test re-sets it.
        // Ensure ClientIpContext is clean after each test run
        fullyClearClientIpContextMap();
    }

    @Test
    void testStatsLogged_whenRequestFrom127_0_0_1() throws Exception {
        // Make a request from 127.0.0.1
        // Assuming /user/list is an endpoint in DemoApplication's UserController
        // and it takes some time or we make the thread sleep.
        // For simplicity, we rely on the filter setting the IP and the logger checking it.
        // The controller needs to be active for a bit for the hasActive127_0_0_1_Request to be true
        // when the logger thread runs.
        
        mvc.perform(MockMvcRequestBuilders.get("/users").with(request -> {
            request.setRemoteAddr("127.0.0.1");
            // Simulate a slightly longer request processing time if necessary,
            // or ensure controller method itself takes a moment.
            // For this test, we'll assume the filter correctly sets the context.
            return request;
        })).andReturn();

        // Wait for the logging thread to run (timeBetweenLogStatsMillis is 100ms)
        Thread.sleep(300); // Wait for at least a couple of logging cycles

        assertTrue(testDelegateLogger.getLogCallCount() >= 1,
                   "Log method should have been called for 127.0.0.1 request. Count: " + testDelegateLogger.getLogCallCount());
    }

    @Test
    void testStatsNotLogged_whenRequestFromOtherIp() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/users").with(request -> {
            request.setRemoteAddr("192.168.10.20");
            return request;
        })).andReturn();

        Thread.sleep(300); // Wait for logging cycles

        assertEquals(0, testDelegateLogger.getLogCallCount(),
                     "Log method should NOT have been called for request from other IP. Count: " + testDelegateLogger.getLogCallCount());
    }

    @Test
    void testStatsLogging_AlternatingIpRequests() throws Exception {
        // 1. Request from other IP
        mvc.perform(MockMvcRequestBuilders.get("/users").with(request -> {
            request.setRemoteAddr("192.168.1.5");
            return request;
        })).andReturn();
        Thread.sleep(300); // Logging interval is 100ms
        assertEquals(0, testDelegateLogger.getLogCallCount(), "Should not log for initial other IP");

        // 2. Request from 127.0.0.1
        testDelegateLogger.reset(); // Reset for this part of the test
        mvc.perform(MockMvcRequestBuilders.get("/users").with(request -> {
            request.setRemoteAddr("127.0.0.1");
            return request;
        })).andReturn();
        Thread.sleep(300);
        assertTrue(testDelegateLogger.getLogCallCount() >= 1, "Should log for 127.0.0.1 IP. Count: " + testDelegateLogger.getLogCallCount());

        // 3. Request from other IP again, after 127.0.0.1 request has completed
        // Ensure ClientIpContext related to the 127.0.0.1 request is cleared by its filter's finally block.
        // The MockMvc request/response cycle should ensure the filter's finally block has run.
        testDelegateLogger.reset();
        fullyClearClientIpContextMap(); // Extra clear to be sure, as thread name reuse can be tricky

        mvc.perform(MockMvcRequestBuilders.get("/users").with(request -> {
            request.setRemoteAddr("192.168.1.6");
            return request;
        })).andReturn();
        Thread.sleep(300);
        assertEquals(0, testDelegateLogger.getLogCallCount(), "Should not log for subsequent other IP after 127.0.0.1 context is cleared. Count: " + testDelegateLogger.getLogCallCount());
    }
}
