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
public class CourseOwnController {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final UserController userController;
    private final CourseController courseController;
    private final CourseAndUserService courseAndUserService;
    private final TokenAuthService tokenAuthService;

    @Autowired
    public CourseOwnController(CourseRepository courseRepository,
                                  UserRepository userRepository,
                                  UserController userController,
                                  CourseController courseController,
                                  CourseAndUserService courseAndUserService,
                                  TokenAuthService tokenAuthService) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.userController = userController;
        this.courseController = courseController;
        this.courseAndUserService = courseAndUserService;
        this.tokenAuthService = tokenAuthService;
    }

    /**
     * Gets all courses owned by user.
     *
     * @param userId the id of the user
     * @return the set of courses owned by the user
     */
    @GetMapping("/users/{userId}/ownedCourses")
    @Transactional
    public List<CourseResponse> getOwnedCourses(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        List<CourseResponse> result = new ArrayList<>();
        for (Course c : user.getOwning())
            result.add(courseController.makeCourseResponse(c, user));
        return result;
    }

    /**
     * Gets all users owning a course.
     *
     * @param courseId the id of the user
     * @return the set of users owning the course
     */
    @GetMapping("/courses/{courseId}/owners")
    @Transactional
    public List<UserResponse> getCourseOwners(@PathVariable Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(CourseNotFoundException::new);
        List<UserResponse> result = new ArrayList<>();
        for (User u : course.getOwners())
            result.add(userController.makeUserResponse(u));
        return result;
    }

    /**
     * Make a user own a course.
     * Only an existing Course owner can call this.
     * If user is already an owner, nothing happens.
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
    @PostMapping("/users/{userId}/ownedCourses")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void addCourseOwner(@PathVariable Long userId,
                               @RequestBody ValueWrapper<Long> courseId,
                               @RequestHeader(name = "Authorization", required = false)
                                   @Nullable String auth) {
        User caller = tokenAuthService.getUserObjectFromToken(auth);
        Course course = courseRepository.findById(courseId.getValue())
                .orElseThrow(CourseNotFoundException::new);
        if (!courseAndUserService.isOwning(caller, course))
            throw new AuthorizationException("You don't own the course");
        courseAndUserService.addOwner(userId, courseId.getValue());
    }

    /**
     * Stop a user from owning a course.
     * Any owner of the course can call this.
     * If the user isn't owning the course, nothing happens.
     * If the user is the last owner of the course, nothing happens
     * and 422 (Unprocessable Entity) is returned.
     *
     * <p>Returns 204 (No content) on success.
     *
     * @param userId the id of the user
     * @param courseId the id of the course
     * @param auth the token used to authenticate
     * @throws MissingAuthorizationException if auth is null
     * @throws InvalidTokenException if auth isn't a valid token
     * @throws AuthorizationException if the token owner doesn't have permission
     * @throws GazelleException if the course would be orphaned
     */
    @DeleteMapping("/users/{userId}/ownedCourses/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void removeCourseOwner(@PathVariable Long userId, @PathVariable Long courseId,
                                  @RequestHeader(name = "Authorization", required = false)
                                  @Nullable String auth) {
        User caller = tokenAuthService.getUserObjectFromToken(auth);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(CourseNotFoundException::new);
        if (!courseAndUserService.isOwning(caller, course))
            throw new AuthorizationException("You don't own the course");

        courseAndUserService.removeOwner(userId, courseId);

        if (course.getOwners().isEmpty()) //This will revert the transaction
            throw new GazelleException(
                    "This is the last owner of the course",
                    HttpStatus.UNPROCESSABLE_ENTITY);
    }
}