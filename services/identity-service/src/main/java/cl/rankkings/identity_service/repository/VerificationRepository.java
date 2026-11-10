package cl.rankkings.identity_service.repository;

import cl.rankkings.identity_service.model.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationRepository extends JpaRepository<EmailVerification, String> {
    List<EmailVerification> findByAccountId(String accountId);
}
