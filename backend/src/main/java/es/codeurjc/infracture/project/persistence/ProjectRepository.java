package es.codeurjc.infracture.project.persistence;

import es.codeurjc.infracture.project.domain.Project;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Page<Project> findAllByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId, Pageable pageable);

    Optional<Project> findByIdAndOwnerId(Long id, Long ownerId);
}
