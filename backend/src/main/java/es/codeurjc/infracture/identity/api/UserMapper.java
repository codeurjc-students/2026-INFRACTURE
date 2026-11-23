package es.codeurjc.infracture.identity.api;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import es.codeurjc.infracture.identity.domain.User;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    UserDTO toDTO(User user);

}
