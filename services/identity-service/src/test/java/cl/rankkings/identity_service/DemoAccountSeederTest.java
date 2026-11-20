package cl.rankkings.identity_service;

import cl.rankkings.identity_service.model.Account;
import cl.rankkings.identity_service.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoAccountSeederTest {
    @Mock private AccountRepository accounts;
    @InjectMocks private DemoAccountSeeder seeder;

    @Test
    void createsVerifiedLocalAccountsForDemoPostAuthors() {
        when(accounts.existsById(anyString())).thenReturn(false);
        when(accounts.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
        when(accounts.existsByEmailIgnoreCase(anyString())).thenReturn(false);

        seeder.run();

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts, org.mockito.Mockito.times(3)).save(saved.capture());
        assertEquals(3, saved.getAllValues().size());
        for (Account account : saved.getAllValues()) {
            assertTrue(account.isEmailVerified());
            assertTrue(Credentials.checkPassword(DemoAccountSeeder.DEMO_PASSWORD, account.getPasswordHash()));
        }
    }
}
