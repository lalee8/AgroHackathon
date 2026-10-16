package az.aqroinvest.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByMygovSub(String mygovSub);

    Optional<User> findFirstByFinIgnoreCase(String fin);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByRole(Role role);

    long countByRole(Role role);

    Optional<User> findByTelegramChatId(Long chatId);
}
