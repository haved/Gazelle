package gazelle.server.endpoint;

import gazelle.api.CourseResponse;
import gazelle.api.NewChoreRequest;
import gazelle.api.NewCourseRequest;
import gazelle.api.PostResponse;
import gazelle.model.Course;
import gazelle.model.ModelException;
import gazelle.model.User;
import gazelle.server.error.AuthorizationException;
import gazelle.server.error.CourseNotFoundException;
import gazelle.server.error.InvalidTokenException;
import gazelle.server.error.UnprocessableEntityException;
import gazelle.server.repository.ChoreRepository;
import gazelle.server.repository.CourseRepository;
import gazelle.server.repository.PostRepository;
import gazelle.server.service.CourseAndUserService;
import gazelle.server.service.TokenAuthService;
import gazelle.util.DateHelper;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/courses")
public class CourseController {

    private final CourseRepository courseRepository;
    private final TokenAuthService tokenAuthService;
    private final CourseAndUserService courseAndUserService;
    private final PostRepository postRepository;
    private final PostController postController;
    private final ChoreController choreController;
    private final ChoreRepository choreRepository;

    @Autowired
    public CourseController(CourseRepository courseRepository,
                            TokenAuthService tokenAuthService,
                            CourseAndUserService courseAndUserService,
                            PostRepository postRepository,
                            PostController postController,
                            ChoreController choreController,
                            ChoreRepository choreRepository) {
        this.courseRepository = courseRepository;
        this.tokenAuthService = tokenAuthService;
        this.courseAndUserService = courseAndUserService;
        this.postRepository = postRepository;
        this.postController = postController;
        this.choreController = choreController;
        this.choreRepository = choreRepository;
    }

    /**
     * Creates a serializable object with course data and other info related to the course.
     * If a user is provided, info about the relationship is also returned.
     * @param course the course object
     * @param user the user object, or null
     * @return CourseResponse object
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CourseResponse makeCourseResponse(Course course, @Nullable User user) {
        Boolean isOwner = null;
        Boolean isFollower = null;
        if (user != null) {
            isOwner = courseAndUserService.isOwning(user, course);
            isFollower = courseAndUserService.isFollowing(user, course);
        }

        Date today = DateHelper.today();

        CourseResponse.Builder builder = new CourseResponse.Builder();
        builder.id(course.getId())
                .name(course.getName())
                .isOwner(isOwner)
                .isFollower(isFollower)
                .currentPost(postRepository.findCurrentPostInCourse(course, today)
                        .map(it -> postController.makePostResponse(it, user))
                        .orElse(null))
                .nextPost(postRepository.findNextPostInCourse(course, today)
                        .map(it -> postController.makePostResponse(it, user))
                        .orElse(null))
                .previousPost(postRepository.findPreviousPostInCourse(course, today)
                        .map(it -> postController.makePostResponse(it, user))
                        .orElse(null))
                .nextChoreDue(choreRepository.findNextDueDateInCourse(course, today)
                        .map(it -> choreController.makeChoreResponse(it, user))
                        .orElse(null));

        return builder.build();
    }

    /**
     * Creates a new Course object from a NewCourseRequest.
     * Does not persist the new Course.
     *
     * @param r the NewCourseRequest
     * @return Course the new Course
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Course buildCourse(NewCourseRequest r) {
        return new Course(r.getName());
    }

    /**
     * Gets a list of all courses.
     * Does not require the Authorization header.
     * If none is supplied, isOwning will be null for all courses,
     * and no chores will be completed.
     *
     * @param auth an optional token, or null
     * @return CourseResponse objects for every course
     */
    @GetMapping
    @Transactional
    public List<CourseResponse> findAll(@RequestHeader(name = "Authorization", required = false)
                                            @Nullable String auth) {
        User user = null;
        if (auth != null)
            user = tokenAuthService.getUserObjectFromToken(auth);
        List<CourseResponse> result = new ArrayList<>();
        for (Course c : courseRepository.findAll())
            result.add(makeCourseResponse(c, user));
        return result;
    }

    /**
     * Gets a CourseResponse for a single course.
     * Optionally takes an Authorization token for a user.
     * If none is supplied, isOwning will be null,
     * and no chores will be completed.
     *
     * @param id of the course in question
     * @param auth an optional token, or null
     * @return CourseResponse for the course with the given id
     */
    @GetMapping("/{id}")
    @Transactional
    public CourseResponse findById(@PathVariable Long id,
                                   @RequestHeader(name = "Authorization", required = false)
                                   @Nullable String auth) {
        Course course = courseRepository.findById(id).orElseThrow(CourseNotFoundException::new);
        User user = null;
        if (auth != null)
            user = tokenAuthService.getUserObjectFromToken(auth);
        return makeCourseResponse(course, user);
    }

    /**
     * Makes a new course, and makes the creating user the owner
     * @param newCourse the data for the new course
     * @param auth the token for the user
     * @return CourseResponse for the new course
     */
    @PostMapping
    @Transactional
    public CourseResponse addNewCourse(@RequestBody NewCourseRequest newCourse,
                                       @RequestHeader(name = "Authorization", required = false)
                                       @Nullable String auth) {
        User user = tokenAuthService.getUserObjectFromToken(auth);

        Course course = buildCourse(newCourse);
        try {
            course.validate();
        } catch (ModelException e) {
            throw new UnprocessableEntityException(e.getMessage());
        }

        courseRepository.save(course);
        courseAndUserService.addOwner(user, course);

        return makeCourseResponse(course, user);
    }

    /**
     * Permanently delete a course from the system
     *
     * @param id the id of the course to delete
     * @param auth the Authorization header for the logged in user
     * @throws CourseNotFoundException if the course doesn't exist
     * @throws InvalidTokenException if the token is invalid
     * @throws AuthorizationException if the user doesn't have access
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteCourse(@PathVariable Long id,
                             @RequestHeader(name = "Authorization", required = false)
                             @Nullable String auth) {
        Course course = courseRepository.findById(id)
                .orElseThrow(CourseNotFoundException::new);

        User user = tokenAuthService.getUserObjectFromToken(auth);

        if (!courseAndUserService.isOwning(user, course))
            throw new AuthorizationException();

        courseRepository.deleteById(id);
    }
}
