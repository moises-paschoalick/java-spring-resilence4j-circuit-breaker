package com.paschoalick.publication.client;

import com.paschoalick.publication.domain.Comments;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.xml.stream.events.Comment;
import java.util.List;

@FeignClient(name = "CommentClient", url = "${client.comments.url}")
public interface CommentClient {

    @GetMapping("/comments/{publicationId}")
    List<Comments> getComments(@PathVariable("publicationId") String publicationId);

}


