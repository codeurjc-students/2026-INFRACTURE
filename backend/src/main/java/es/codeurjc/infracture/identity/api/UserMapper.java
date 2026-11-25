package es.codeurjc.infracture.identity.api;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import es.codeurjc.infracture.identity.application.AvatarService;
import es.codeurjc.infracture.identity.domain.User;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    @Mapping(target = "avatarUrl", source = "avatarKey", qualifiedByName = "avatarUrl")
    UserDTO toDTO(User user);

    @Named("avatarUrl")
    default String avatarUrl(String avatarKey) {
        return avatarKey == null ? null : "/api/v1/users/me/avatar?v=" + AvatarService.version(avatarKey);
    }
}
