package cl.rankkings.identity_service.repository;

import cl.rankkings.identity_service.model.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<LoginSession, String> {}
