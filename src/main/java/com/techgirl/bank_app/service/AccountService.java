package com.techgirl.bank_app.service;

import com.techgirl.bank_app.config.PasswordEncoderConfig;
import com.techgirl.bank_app.model.Account;
import com.techgirl.bank_app.model.Transaction;
import com.techgirl.bank_app.repository.AccountRepository;
import com.techgirl.bank_app.repository.TransactionRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Service
public class AccountService implements UserDetailsService {

    private final PasswordEncoderConfig passwordEncoderConfig;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(PasswordEncoderConfig passwordEncoderConfig, AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.passwordEncoderConfig = passwordEncoderConfig;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public Account findAccountByUsername(String username) {
        return accountRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Account not found"));
    }

    public Account registerAccount(String username, String password) {
        if(accountRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Account already exists");
        }
        Account account = new Account();
        account.setUsername(username);
        account.setPassword(passwordEncoderConfig.passwordEncoder().encode(password));
        account.setBalance(BigDecimal.ZERO);
        return accountRepository.save(account);
    }

    public void deposit(Account account, BigDecimal amount) {
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        Transaction transaction = new Transaction(
                amount,
                "Deposit",
                LocalDateTime.now(),
                account
        );

        transactionRepository.save(transaction);

    }

    public void withdraw(Account account, BigDecimal amount) {

        if(account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        Transaction transaction = new Transaction(
                amount,
                "Withdraw",
                LocalDateTime.now(),
                account
        );
        transactionRepository.save(transaction);
    }

    public List<Transaction> getTransactionHistory(Account account) {
        return transactionRepository.findByAccountId(account.getId());
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = findAccountByUsername(username);

        if(account == null) {
            throw new UsernameNotFoundException("Account not found");
        }
        return new Account(
                account.getUsername(),
                account.getPassword(),
                account.getBalance(),
                account.getTransactions(),
                authorities()
        );
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return Arrays.asList(new SimpleGrantedAuthority("User"));
    }

    public void transferAmount(Account fromAccount, String toUsername, BigDecimal amount) {
        if(fromAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        Account toAccount = accountRepository.findByUsername(toUsername)
                .orElseThrow(() -> new RuntimeException("Reception account not found"));

        //deduct
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        accountRepository.save(fromAccount);

        //add
        toAccount.setBalance(toAccount.getBalance().add(amount));
        accountRepository.save(toAccount);

        //create transaction records
        Transaction debitTransaction = new Transaction(
                amount,
                "Transfer Out to "+ toAccount.getUsername(),
                LocalDateTime.now(),
                fromAccount
        );
        transactionRepository.save(debitTransaction);

        Transaction creditTransaction = new Transaction(
                amount,
                "Received from "+ fromAccount.getUsername(),
                LocalDateTime.now(),
                toAccount
        );
        transactionRepository.save(creditTransaction);
    }
}


