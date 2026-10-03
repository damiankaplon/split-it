package pl.damiankaplon.splitit.project

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.rest.core.annotation.RepositoryRestResource
import java.util.UUID

/** Spring Data REST exposes CRUD endpoints for [Project] under `/projects`. */
@RepositoryRestResource(path = "projects", collectionResourceRel = "projects")
interface ProjectRepository : JpaRepository<Project, UUID>
