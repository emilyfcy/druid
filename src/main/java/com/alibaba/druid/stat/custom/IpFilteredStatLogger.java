package com.alibaba.druid.stat.custom;

import com.alibaba.druid.stat.DruidDataSourceStatLogger;
import com.alibaba.druid.stat.DruidDataSourceStatLoggerImpl;
import com.alibaba.druid.stat.DruidDataSourceStatValue;
import com.alibaba.druid.support.http.util.ClientIpContext;
import com.alibaba.druid.support.logging.Log;
import com.alibaba.druid.support.logging.LogFactory;

import java.util.Properties;

public class IpFilteredStatLogger implements DruidDataSourceStatLogger {

    private static final Log LOG = LogFactory.getLog(IpFilteredStatLogger.class);

    // Delegate to the default logger implementation
    private final DruidDataSourceStatLogger delegate;

    public IpFilteredStatLogger() {
        // Initialize with the default Druid logger
        this.delegate = new DruidDataSourceStatLoggerImpl();
    }

    // Constructor allowing a specific delegate to be passed (optional)
    public IpFilteredStatLogger(DruidDataSourceStatLogger delegate) {
        this.delegate = (delegate != null) ? delegate : new DruidDataSourceStatLoggerImpl();
    }

    @Override
    public void log(DruidDataSourceStatValue statValue) {
        if (ClientIpContext.hasActive127_0_0_1_Request()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Logging Druid stats because an active request from 127.0.0.1 was found.");
            }
            delegate.log(statValue);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Skipping Druid stats logging as no active request from 127.0.0.1 was found.");
            }
        }
    }

    @Override
    public void configFromProperties(Properties properties) {
        if (delegate != null) {
            delegate.configFromProperties(properties);
        }
    }

    @Override
    public void setLoggerName(String loggerName) {
        if (delegate != null) {
            delegate.setLoggerName(loggerName);
        }
    }

    @Override
    public void setLogger(Object logger) {
        if (delegate != null) {
            delegate.setLogger(logger);
        }
    }
}
