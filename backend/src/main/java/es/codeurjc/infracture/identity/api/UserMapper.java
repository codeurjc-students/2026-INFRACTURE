package es.codeurjc.infracture.identity.api;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import es.codeurjc.infracture.identity.domain.User;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    @Mapping(target = "encodedPassword", source = "password")
    User fromRegisterDTOtoDomain(RegisterUserDTO userDTO);

    UserDTO toDTO(User user);

}
