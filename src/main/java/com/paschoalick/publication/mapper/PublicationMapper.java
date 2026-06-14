package com.paschoalick.publication.mapper;

import com.paschoalick.publication.controller.request.PublicationRequest;
import com.paschoalick.publication.domain.Publication;
import com.paschoalick.publication.repository.entity.PublicationEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PublicationMapper {

    PublicationEntity toPublicationEntity(Publication publication);

    Publication toPublication(PublicationEntity publicationEntity);

    Publication toPublication(PublicationRequest publicationRequest);

}
