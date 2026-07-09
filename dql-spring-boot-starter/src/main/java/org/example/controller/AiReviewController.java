package org.example.controller;

import org.example.ai.AiReviewException;
import org.example.ai.AiReviewer;
import org.example.model.AiContext;
import org.example.model.AiReview;
import org.example.scheduler.DqlAnalysisScheduler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;


@RestController
@RequestMapping("/dql")
public class AiReviewController {

    private final DqlAnalysisScheduler scheduler;
    private final AiReviewer aiReviewer;

    private Instant cachedRun;
    private AiReview cachedReview;

    public AiReviewController(DqlAnalysisScheduler scheduler, AiReviewer aiReviewer) {
        this.scheduler = scheduler;
        this.aiReviewer = aiReviewer;
    }

    @GetMapping("/ai-review")
    public synchronized AiReview aiReview() {
        if (aiReviewer == null) {
            return new AiReview("AI review is disabled. Set dql.ai.enabled=true and dql.ai.api-key to enable.");
        }

        AiContext context = scheduler.latestAiContext();
        Instant run = scheduler.lastRun();

        if (context == null || run == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DQL analysis has not run yet"
            );
        }

        if (run.equals(cachedRun) && cachedReview != null) {
            return cachedReview;
        }

        try {
            AiReview review = aiReviewer.review(context);
            cachedRun = run;
            cachedReview = review;
            return review;
        } catch (AiReviewException e) {
            return new AiReview("AI unavailable: " + e.getMessage());
        }
    }
}
