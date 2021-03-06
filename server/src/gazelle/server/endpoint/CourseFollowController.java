package gazelle.server.endpoint;

import gazelle.api.CourseResponse;
import gazelle.api.UserResponse;
import gazelle.api.ValueWrapper;
import gazelle.model.Course;
import gazelle.model.User;
import gazelle.server.error.*;
import gazelle.server.repository.CourseRepository;
import gazelle.server.repository.UserRepository;
import gazelle.server.service.CourseAndUserService;
import gazelle.server.service.TokenAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
public class CourseFollowController {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseAndUserService courseAndUserService;
    private final TokenAuthService tokenAuthService;
    private final CourseController courseController;
    private final UserController userController;

    @Autowired
    public CourseFollowController(CourseRepository courseRepository,
                                  UserRepository userRepository,
                                  CourseAndUserService courseAndUserService,
                                  TokenAuthService tokenAuthService,
                                  CourseController courseController,
                                  UserController userController) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.courseAndUserService = courseAndUserService;
        this.tokenAuthService = tokenAuthService;
        this.courseController = courseController;
        this.userController = userController;
    }

    /**
     * Gets all courses followed by user.
     * Only the user itself can request this list.
     *
     * @param userId the id of the user
     * @param auth the token used to authenticate
     * @return the set of courses followed by the user
     * @throws MissingAuthorizationException if auth is null
     * @throws InvalidTokenException if auth isn't a valid token
     * @throws AuthorizationException if the requester doesn't
     *     have permission to see the user's followed courses.
     */
    @GetMapping("/users/{userId}/followedCourses")
    @Transactional
    public List<CourseResponse> getFollowedCourses(
            @PathVariable Long userId,
            @RequestHeader(name = "Authorization", required = false)
            @Nullable String auth) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        tokenAuthService.assertTokenForUser(userId, auth);
        List<CourseResponse> result = new ArrayList<>();
        for (Course c : user.getFollowing())
            result.add(courseController.makeCourseResponse(c, user));
        return result;
    }

    /**
     * Gets all users following a course.
     * Only owners of the course can get this.
     *
     * @param courseId the id of the user
     * @param auth the token used to authenticate
     * @return the set of users following the course
     * @throws MissingAuthorizationException if auth is null
     * @throws InvalidTokenException if auth isn't a valid token
     * @throws AuthorizationException if the requester doesn't
     *     have permission to see who follows the course.
     */
    @GetMapping("/courses/{courseId}/followers")
    @Transactional
    public List<UserResponse> getCourseFollowers(
            @PathVariable Long courseId,
            @RequestHeader(name = "Authorization", required = false)
            @Nullable String auth) {
        User user = tokenAuthService.getUserObjectFromToken(auth);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(CourseNotFoundException::new);
        if (!courseAndUserService.isOwning(user, course))
            throw new AuthorizationException("You do not own the course");
        List<UserResponse> result = new ArrayList<>();
        for (User u : course.getFollowers())
            result.add(userController.makeUserResponse(u));
        return result;
    }

    /**
     * Make a user follow a course.
     * Only the user can call this.
     * If user is already a follower, nothing happens.
     *
     * <p>Returns 204 (No content) on success.
     *
     * @param userId the id of the user
     * @param courseId the id of the course
     * @param auth the token used to authenticate
     * @throws MissingAuthorizationException if auth is null
     * @throws InvalidTokenException if auth isn't a valid token
     * @throws AuthorizationException if the token owner doesn't have permission
     */
    @PostMapping("/users/{userId}/followedCourses")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addCourseFollower(@PathVariable Long userId,
                                  @RequestBody ValueWrapper<Long> courseId,
                                  @RequestHeader(name = "Authorization", required = false)
                                  @Nullable String auth) {
        tokenAuthService.assertTokenForUser(userId, auth);
        courseAndUserService.addFollower(userId, courseId.getValue());
    }

    /**
     * Stop a user from following a course.
     * Only the user can call this.
     * If user isn't a follower, nothing happens.
     *
     * <p>Returns 204 (No content) on success.
     *
     * @param userId the id of the user
     * @param courseId the id of the course
     * @param auth the token used to authenticate
     * @throws MissingAuthorizationException if auth is null
     * @throws InvalidTokenException if auth isn't a valid token
     * @throws AuthorizationException if the token owner doesn't have permission
     */
    @DeleteMapping("/users/{userId}/followedCourses/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCourseFollower(@PathVariable Long userId, @PathVariable Long courseId,
                                     @RequestHeader(name = "Authorization", required = false)
                                     @Nullable String auth) {
        tokenAuthService.assertTokenForUser(userId, auth);
        courseAndUserService.removeFollower(userId, courseId);
    }
}
