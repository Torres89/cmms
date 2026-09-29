package com.grash.configuration;

import com.grash.job.DeleteDemoCompaniesJob;
import com.grash.job.IntervalMaintenanceJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Bean
    public JobDetail deleteDemoCompaniesJobDetail() {
        return JobBuilder.newJob(DeleteDemoCompaniesJob.class)
                .withIdentity("deleteDemoCompaniesJob")
                .storeDurably()
                .build();
    }
    @Bean
    public Trigger deleteDemoCompaniesTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(deleteDemoCompaniesJobDetail())
                .withIdentity("deleteDemoCompaniesTrigger")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInHours(1)
                        .repeatForever())
                .build();
    }

    @Bean
    public JobDetail intervalMaintenanceJobDetail() {
        return JobBuilder.newJob(IntervalMaintenanceJob.class)
                .withIdentity("intervalMaintenanceJob")
                .storeDurably()
                .build();
    }

    /**
     * Early morning, so a PM whose calendar counter ran out overnight is on the
     * list before the first shift starts.
     */
    @Bean
    public Trigger intervalMaintenanceTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(intervalMaintenanceJobDetail())
                .withIdentity("intervalMaintenanceTrigger")
                .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(5, 30)
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}
