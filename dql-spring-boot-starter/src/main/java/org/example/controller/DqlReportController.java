package org.example.controller;

import org.example.model.AiContext;
import org.example.model.QualityReport;
import org.example.scheduler.DqlAnalysisScheduler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestController
@RequestMapping("/dql")
public class DqlReportController {

    private final DqlAnalysisScheduler scheduler;

    public DqlReportController(DqlAnalysisScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @GetMapping("/findings")
    public QualityReport findings() {
        QualityReport report = scheduler.latestReport();

        if (report == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DQL report is not ready yet"
            );
        }

        return report;
    }

    @GetMapping("/ai-context")
    public AiContext aiContext() {
        AiContext aiContext = scheduler.latestAiContext();

        if (aiContext == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DQL AI context is not ready yet"
            );
        }

        return aiContext;
    }

    @GetMapping("/last-run")
    public Instant lastRun() {
        Instant lastRun = scheduler.lastRun();

        if (lastRun == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DQL analysis has not run yet"
            );
        }

        return lastRun;
    }
}