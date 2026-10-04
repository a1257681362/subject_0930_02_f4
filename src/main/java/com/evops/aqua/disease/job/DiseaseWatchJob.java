package com.evops.aqua.disease.job;

import com.evops.aqua.disease.service.DisWatchService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.LocalDate;
import java.util.Map;

/**
 * 疫病巡查定时任务：每日检查海水养殖管理的「疫病观察与隔离池调度」，
 * 对未完结观察单生成当日检查记录。逻辑全部在 DisWatchService.runDailyCheck：
 * 观察单+日期唯一，重启后重跑不多写记录、不丢操作者。
 */
public class DiseaseWatchJob extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(DiseaseWatchJob.class);

    @Autowired
    private DisWatchService watchService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        Map<String, Object> result = watchService.runDailyCheck(LocalDate.now());
        log.info("疫病观察与隔离池调度巡查完成：{}", result);
    }
}
