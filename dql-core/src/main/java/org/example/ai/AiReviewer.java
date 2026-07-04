package org.example.ai;

import org.example.model.AiContext;
import org.example.model.AiReview;

public interface AiReviewer {

    AiReview review(AiContext context);
}
