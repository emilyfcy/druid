package com.alibaba.druid.spring.boot.demo.util;

import com.alibaba.druid.stat.DruidDataSourceStatLogger;
import com.alibaba.druid.stat.DruidDataSourceStatValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class CapturingTestStatLogger implements DruidDataSourceStatLogger {

    private int logCallCount = 0;
    private DruidDataSourceStatValue lastStatValue = null;
    private List<DruidDataSourceStatValue> allStatValues = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void log(DruidDataSourceStatValue statValue) {
        this.logCallCount++;
        this.lastStatValue = statValue;
        this.allStatValues.add(statValue.clone()); // Clone to store a snapshot
    }

    @Override
    public void configFromProperties(Properties properties) {
        // Not typically needed for test capture, can be left empty or log if called
    }

    @Override
    public void setLoggerName(String loggerName) {
        // Not typically needed for test capture
    }

    @Override
    public void setLogger(Object logger) {
        // Not typically needed for test capture
    }

    // --- Test inspection methods ---

    public int getLogCallCount() {
        return logCallCount;
    }

    public DruidDataSourceStatValue getLastStatValue() {
        return lastStatValue;
    }
    
    public List<DruidDataSourceStatValue> getAllStatValues() {
        return new ArrayList<>(allStatValues); // Return a copy
    }

    public void reset() {
        this.logCallCount = 0;
        this.lastStatValue = null;
        this.allStatValues.clear();
    }
}
