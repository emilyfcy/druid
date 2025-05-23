package com.alibaba.druid.spring.boot.demo.configurer;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.stat.custom.IpFilteredStatLogger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component; // Or it can be registered as a @Bean in DemoApplication

@Component // Make it a component to be picked up by component scan
public class DruidCustomLoggerBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean; // No action needed before initialization
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DruidDataSource) {
            DruidDataSource druidDataSource = (DruidDataSource) bean;
            // Check if a stat logger is already set, possibly by StatFilter
            // We want our IpFilteredStatLogger to wrap the existing one if it's the default,
            // or just replace it if we are sure.
            // For simplicity here, we'll create a new IpFilteredStatLogger which, by default,
            // creates and delegates to a new DruidDataSourceStatLoggerImpl.
            // A more advanced version could check druidDataSource.getStatLogger()
            // and pass it to new IpFilteredStatLogger(existingLogger).
            druidDataSource.setStatLogger(new IpFilteredStatLogger());
        }
        return bean;
    }
}
