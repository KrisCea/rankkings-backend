package cl.rankkings.identity_service.repository;

import cl.rankkings.identity_service.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findByUsernameIgnoreCase(String username);
    Optional<Account> findByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    List<Account> findByVerificationStatusOrderByVerificationRequestedAtDesc(String verificationStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT account FROM Account account WHERE account.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") String id);
}
