package io.github.geethapriya1625.workflow.config;

import io.github.geethapriya1625.workflow.domain.ApprovalPolicy;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ApprovalPolicy approvalPolicy(AppProperties props) {
        return new ApprovalPolicy(props.policy().financeThreshold(), props.policy().directorThreshold());
    }
}
