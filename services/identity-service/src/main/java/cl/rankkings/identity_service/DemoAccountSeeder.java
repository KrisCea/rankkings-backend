package cl.rankkings.identity_service;

import cl.rankkings.identity_service.model.Account;
import cl.rankkings.identity_service.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("local")
@RequiredArgsConstructor
public class DemoAccountSeeder implements CommandLineRunner {
    public static final String DEMO_PASSWORD = "DemoRankKings123!";

    private final AccountRepository accounts;

    @Override
    public void run(String... args) {
        List<DemoAccount> demoAccounts = List.of(
                new DemoAccount("demo-user-ana", "ana_demo", "ana.demo@rankkings.test", "Ana Demo",
                        "Aficionada a la música, el cine y los libros."),
                new DemoAccount("demo-user-mateo", "mateo_demo", "mateo.demo@rankkings.test", "Mateo Demo",
                        "Exploro videojuegos, podcasts y cultura pop."),
                new DemoAccount("demo-user-camila", "camila_demo", "camila.demo@rankkings.test", "Camila Demo",
                        "Me gustan el arte, los juegos de mesa y descubrir historias.")
        );

        for (DemoAccount demo : demoAccounts) {
            if (accounts.existsById(demo.id())
                    || accounts.existsByUsernameIgnoreCase(demo.username())
                    || accounts.existsByEmailIgnoreCase(demo.email())) {
                continue;
            }
            Account account = new Account();
            account.setId(demo.id());
            account.setUsername(demo.username());
            account.setEmail(demo.email());
            account.setDisplayName(demo.displayName());
            account.setPasswordHash(Credentials.hashPassword(DEMO_PASSWORD));
            account.setEmailVerified(true);
            account.setAccountType("individual");
            account.setRole("user");
            account.setBio(demo.bio());
            accounts.save(account);
        }
    }

    private record DemoAccount(String id, String username, String email, String displayName, String bio) {}
}
