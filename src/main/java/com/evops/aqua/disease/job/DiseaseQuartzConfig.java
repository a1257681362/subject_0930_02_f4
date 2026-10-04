package com.evops.aqua.disease.job;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.CronScheduleBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 疫病巡查定时任务调度：每日 06:30 检查「疫病观察与隔离池调度」。
 * 任务本身幂等（观察单+日期唯一），应用重启触发器重建后重跑同日检查不多写记录。
 */
@Configuration
public class DiseaseQuartzConfig {

    public static final String JOB_NAME = "diseaseWatchJob";
    public static final String JOB_GROUP = "aqua-disease";

    @Bean
    public JobDetail diseaseWatchJobDetail() {
        return JobBuilder.newJob(DiseaseWatchJob.class)
                .withIdentity(JOB_NAME, JOB_GROUP)
                .withDescription("疫病观察与隔离池调度每日巡查")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger diseaseWatchTrigger(JobDetail diseaseWatchJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(diseaseWatchJobDetail)
                .withIdentity("diseaseWatchTrigger", JOB_GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule("0 30 6 * * ?"))
                .build();
    }
}
