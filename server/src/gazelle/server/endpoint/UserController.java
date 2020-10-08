package gazelle.server.endpoint;

import gazelle.model.User;
import gazelle.server.error.UserNotFoundException;
import gazelle.server.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;

    @Autowired
    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public Iterable findAll() {
        return userRepository.findAll();
    }

    @GetMapping("/username/{username}")
    public User findByUsername(@PathVariable String username) {
        return unwrapOrThrow(userRepository.findByUsername(username));
    }

    @GetMapping("/{id}")
    public User findOne(@PathVariable Long id) {
        return unwrapOrThrow(userRepository.findById(id));
    }

    static User unwrapOrThrow(Optional<User> user) {
        return user.orElseThrow(UserNotFoundException::new);
    }
}
