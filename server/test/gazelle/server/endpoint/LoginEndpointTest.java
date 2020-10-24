package gazelle.server.endpoint;

import gazelle.auth.LogInRequest;
import gazelle.auth.LogInResponse;
import gazelle.auth.SignUpRequest;
import gazelle.model.User;
import gazelle.server.error.GazelleException;
import gazelle.server.error.InvalidTokenException;
import gazelle.server.error.LoginFailedException;
import gazelle.server.service.TokenAuthService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LoginEndpointTest {

    @Autowired
    private LoginEndpoint loginEndpoint;

    @Autowired
    private UserController userController;

    @Autowired
    private TokenAuthService tokenAuthService;

    private static final String NAME = "nass2";
    private static final String PASSWD = "nass1234";

    private User user1;
    private String token; //Test invariant: Always logged in

    @BeforeAll
    public void setup() {
        SignUpRequest signUpRequest = new SignUpRequest(NAME, PASSWD);
        LogInResponse response = loginEndpoint.signup(signUpRequest);
        user1 = response.getUser();
        token = TokenAuthService.addBearer(response.getToken());
    }

    @Test
    public void signUpUser() { //Tests that the user created in setup actually exists
        User byName = userController.findByUsername(NAME);
        assertEquals(user1, byName);

        GazelleException exception1 = assertThrows(GazelleException.class,
                () -> loginEndpoint.signup(new SignUpRequest(NAME, "dummypassword")));
        GazelleException exception2 = assertThrows(GazelleException.class,
                () -> loginEndpoint.signup(new SignUpRequest("nass4", "XXX")));
        GazelleException exception3 = assertThrows(GazelleException.class,
                () -> loginEndpoint.signup(new SignUpRequest("nas", "dummypassword")));
        assertEquals("Username taken", exception1.getReason());
        assertEquals("Password too short", exception2.getReason());
        assertEquals("Username too short", exception3.getReason());
    }

    @Test
    public void logInUserTest() {
        // We are already logged in
        assertEquals(user1, tokenAuthService.getUserForToken(token));

        LogInRequest logInRequest = new LogInRequest(NAME, PASSWD);
        LogInResponse logInResponse = loginEndpoint.login(logInRequest);

        // The previous token is invalidated
        assertThrows(InvalidTokenException.class, () -> {
            tokenAuthService.getUserForToken(token);
        });

        token = TokenAuthService.addBearer(logInResponse.getToken());
        assertEquals(logInResponse.getUser(), tokenAuthService.getUserForToken(token));

        assertThrows(LoginFailedException.class,
                () -> loginEndpoint.login(new LogInRequest("nise", "nise"))); //Unknown user
        assertThrows(LoginFailedException.class,
                () -> loginEndpoint.login(new LogInRequest(NAME, "wrongpassword")));
    }

    @Test
    public void logOutTest() {
        //We start off as logged in
        assertEquals(user1, tokenAuthService.getUserForToken(token));

        loginEndpoint.logout(token);

        // Token should now be invalid
        assertThrows(InvalidTokenException.class, () -> {
            tokenAuthService.getUserForToken(token);
        });

        // Logging out again is invalid
        assertThrows(InvalidTokenException.class, () -> {
            loginEndpoint.logout(token);
        });

        // Logging out with invalid token is invalid
        assertThrows(InvalidTokenException.class, () -> {
            loginEndpoint.logout("dummy-token");
        });

        // Log back in to satisfy test invariant
        token = TokenAuthService.addBearer(tokenAuthService.createTokenForUser(user1));
    }

    @AfterAll
    public void cleanup() {
        userController.deleteUser(user1.getId(), token);
    }
}
