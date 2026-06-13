package cards.loxley.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
class UserRepositoryTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void saveGeneratesUuid() {
        var user = userRepository.save(new User("robin", "$2a$10$dummyhash"));

        assertThat(user.getId()).isNotNull();
        assertThat(user.getUsername()).isEqualTo("robin");
        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$dummyhash");
        assertThat(user.getHighestUnlockedStage()).isEqualTo(1);
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByUsernameReturnsUser() {
        var saved = userRepository.save(new User("marian", "$2a$10$dummyhash"));
        entityManager.flush();
        entityManager.clear();

        var found = userRepository.findByUsername("marian");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findByUsernameReturnsEmptyForUnknown() {
        var found = userRepository.findByUsername("nobody");

        assertThat(found).isEmpty();
    }

    @Test
    void findByEmailReturnsUser() {
        var user = new User("marian2", "$2a$10$dummyhash");
        user.setEmail("marian@sherwood.forest");
        var saved = userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        var found = userRepository.findByEmail("marian@sherwood.forest");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void updateHighestUnlockedStage() {
        var user = userRepository.save(new User("littlejohn", "$2a$10$dummyhash"));
        entityManager.flush();
        entityManager.clear();

        var loaded = userRepository.findById(user.getId()).orElseThrow();
        loaded.setHighestUnlockedStage(5);
        userRepository.save(loaded);
        entityManager.flush();
        entityManager.clear();

        var reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getHighestUnlockedStage()).isEqualTo(5);
    }

    @Test
    void uniqueUsernameConstraint() {
        userRepository.save(new User("friar_tuck", "$2a$10$dummyhash"));
        entityManager.flush();

        assertThatThrownBy(() -> {
            userRepository.save(new User("friar_tuck", "$2a$10$anotherhash"));
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }
}
