package com.alibaba.druid.stat.custom;

import com.alibaba.druid.stat.DruidDataSourceStatLogger;
import com.alibaba.druid.stat.DruidDataSourceStatLoggerImpl;
import com.alibaba.druid.stat.DruidDataSourceStatValue;
import com.alibaba.druid.support.http.util.ClientIpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IpFilteredStatLoggerTest {

    @Mock
    private DruidDataSourceStatLogger mockDelegateLogger;

    @Mock
    private DruidDataSourceStatValue mockStatValue;
    
    private IpFilteredStatLogger ipFilteredStatLogger;

    // Helper to clear the static map in ClientIpContext for test isolation
    private void fullyClearClientIpContextMap() throws Exception {
        Field field = ClientIpContext.class.getDeclaredField("active127_0_0_1_Threads");
        field.setAccessible(true);
        ConcurrentHashMap<String, Boolean> map = (ConcurrentHashMap<String, Boolean>) field.get(null);
        map.clear();
        ClientIpContext.currentClientIp.remove();
    }

    @BeforeEach
    void setUp() throws Exception {
        // Manually create instance to inject mock or use default
        ipFilteredStatLogger = new IpFilteredStatLogger(mockDelegateLogger);
        fullyClearClientIpContextMap(); // Ensure clean state for ClientIpContext
    }

    @AfterEach
    void tearDown() throws Exception {
        fullyClearClientIpContextMap(); // Ensure clean state after each test
    }

    @Test
    void testLog_whenActive127Request_shouldLog() {
        // Simulate active 127.0.0.1 request
        Thread.currentThread().setName("TestThread-127"); // Set a name for the map key
        ClientIpContext.setCurrentClientIp("127.0.0.1");

        ipFilteredStatLogger.log(mockStatValue);

        verify(mockDelegateLogger, times(1)).log(mockStatValue);
        
        ClientIpContext.clearCurrentClientIp(); // Cleanup
        Thread.currentThread().setName("test"); // Reset thread name
    }

    @Test
    void testLog_whenNoActive127Request_shouldNotLog() {
        // Ensure no active 127.0.0.1 request (default state after cleanup)
        // ClientIpContext.hasActive127_0_0_1_Request() should be false

        ipFilteredStatLogger.log(mockStatValue);

        verify(mockDelegateLogger, never()).log(mockStatValue);
    }
    
    @Test
    void testLog_whenActiveNon127Request_shouldNotLog() {
        Thread.currentThread().setName("TestThread-Other");
        ClientIpContext.setCurrentClientIp("192.168.0.5");

        ipFilteredStatLogger.log(mockStatValue);

        verify(mockDelegateLogger, never()).log(mockStatValue);
        
        ClientIpContext.clearCurrentClientIp();
        Thread.currentThread().setName("test"); 
    }


    @Test
    void testConfigFromProperties_shouldDelegate() {
        Properties props = new Properties();
        ipFilteredStatLogger.configFromProperties(props);
        verify(mockDelegateLogger, times(1)).configFromProperties(props);
    }

    @Test
    void testSetLoggerName_shouldDelegate() {
        String loggerName = "testLogger";
        ipFilteredStatLogger.setLoggerName(loggerName);
        verify(mockDelegateLogger, times(1)).setLoggerName(loggerName);
    }

    @Test
    void testSetLogger_shouldDelegate() {
        Object logger = new Object(); // Mock or dummy object
        ipFilteredStatLogger.setLogger(logger);
        verify(mockDelegateLogger, times(1)).setLogger(logger);
    }

    @Test
    void testConstructor_withNullDelegate_usesDefaultImpl() throws NoSuchFieldException, IllegalAccessException {
        IpFilteredStatLogger customLogger = new IpFilteredStatLogger(null);
        // Access the private delegate field for verification
        Field delegateField = IpFilteredStatLogger.class.getDeclaredField("delegate");
        delegateField.setAccessible(true);
        Object delegateInstance = delegateField.get(customLogger);
        
        assertNotNull(delegateInstance);
        assertInstanceOf(DruidDataSourceStatLoggerImpl.class, delegateInstance);
    }

    @Test
    void testConstructor_default_usesDefaultImpl() throws NoSuchFieldException, IllegalAccessException {
        IpFilteredStatLogger customLogger = new IpFilteredStatLogger();
        Field delegateField = IpFilteredStatLogger.class.getDeclaredField("delegate");
        delegateField.setAccessible(true);
        Object delegateInstance = delegateField.get(customLogger);

        assertNotNull(delegateInstance);
        assertInstanceOf(DruidDataSourceStatLoggerImpl.class, delegateInstance);
    }
}
