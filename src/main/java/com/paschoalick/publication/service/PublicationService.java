package com.paschoalick.publication.service;

import com.paschoalick.publication.client.CommentClient;
import com.paschoalick.publication.domain.Publication;
import com.paschoalick.publication.exceptions.FallbackException;
import com.paschoalick.publication.mapper.PublicationMapper;
import com.paschoalick.publication.repository.PublicationRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class PublicationService {

    @Autowired
    private PublicationRepository publicationRepository;

    @Autowired
    private PublicationMapper publicationMapper;

    @Autowired
    private CommentClient commentClient;

    public void insert(Publication publication) {
        var publicationEntity = publicationMapper.toPublicationEntity(publication);
        publicationRepository.save(publicationEntity);
    }

    public List<Publication> findAll() {
        var publications = publicationRepository.findAll();
        return publications.stream().map(publicationMapper::toPublication).toList();
    }

    @CircuitBreaker(name = "comments", fallbackMethod = "findByIdFallback")
    public Publication findById(String id) {
        var publication = publicationRepository.findById(id)
                .map(publicationMapper::toPublication)
                .orElseThrow(RuntimeException::new);

        // Enriquece com os comentários
        var comments = commentClient.getComments(id);
        publication.setComments(comments);
        return publication;
    }

    // Precisa ter a mesma assinatura do método que está anotado com
    // CircuitBreaker
    public Publication findByIdFallback(String id, Throwable cause) {
            log.warn("[WARN] fallback with id {}", id);
            throw new FallbackException(cause);
    }
}
