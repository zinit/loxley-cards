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
        var user = userRepository.save(new User("robin@sherwood.forest"));

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("robin@sherwood.forest");
        assertThat(user.getHighestUnlockedStage()).isEqualTo(1);
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByEmailReturnsUser() {
        var saved = userRepository.save(new User("marian@sherwood.forest"));
        entityManager.flush();
        entityManager.clear();

        var found = userRepository.findByEmail("marian@sherwood.forest");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findByEmailReturnsEmptyForUnknown() {
        var found = userRepository.findByEmail("nobody@sherwood.forest");

        assertThat(found).isEmpty();
    }

    @Test
    void updateHighestUnlockedStage() {
        var user = userRepository.save(new User("littlejohn@sherwood.forest"));
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
    void uniqueEmailConstraint() {
        userRepository.save(new User("friar@sherwood.forest"));
        entityManager.flush();

        assertThatThrownBy(() -> {
            userRepository.save(new User("friar@sherwood.forest"));
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }
}
