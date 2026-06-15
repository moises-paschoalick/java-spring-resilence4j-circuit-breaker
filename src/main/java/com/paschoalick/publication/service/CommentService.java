package com.paschoalick.publication.service;

import com.paschoalick.publication.client.CommentClient;
import com.paschoalick.publication.domain.Comment;
import com.paschoalick.publication.domain.Publication;
import com.paschoalick.publication.exceptions.FallbackException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class CommentService {

    @Autowired
    private CommentClient commentClient;

    @Autowired
    private RedisService redisService;

    @CircuitBreaker(name = "comments", fallbackMethod = "getCommentsFallback")
    public List<Comment> getComments(String id) {
        var comments = commentClient.getComments(id);
        redisService.save(comments, id);
        return comments;
    }

    public List<Comment> getCommentsFallback(String id, Throwable cause) {
            log.warn("[WARN] fallback with id {}", id);
            //throw new FallbackException(cause);
            //return List.of();
            return redisService.findById(id);
    }


}
