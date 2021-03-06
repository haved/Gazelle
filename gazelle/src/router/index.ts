import Vue from "vue";
import VueRouter, { RawLocation, Route, RouteConfig } from "vue-router";
import FocusList from "@/views/FocusList.vue";
import store, { LogInStatus } from "@/store";
import LoginView from "@/views/LoginView.vue";
import CoursePage from "@/views/CoursePage.vue";
import ErrorPage from "@/views/ErrorPage.vue";
import PostView from "@/views/PostView.vue";
import CoursesView from "@/views/CoursesView.vue";

Vue.use(VueRouter);

const routes: Array<RouteConfig> = [
  {
    path: "/",
    redirect: "my-courses"
  },
  {
    path: "/my-courses",
    name: "My courses",
    component: CoursesView,
    props: { mode: "myCourses" },
    meta: {
      requiresAuth: true
    }
  },

  {
    path: "/followed-courses",
    name: "Followed courses",
    component: CoursesView,
    props: { mode: "followedCourses" },
    meta: {
      requiresAuth: true
    }
  },
  {
    path: "/focus-list",
    name: "Focus list",
    component: FocusList,
    meta: {
      requiresAuth: true
    }
  },
  {
    path: "/login",
    name: "Login Page",
    component: LoginView,
    props: route => ({
      mode: "login",
      invalidated: route.query.reason === "invalidated"
    }),
    meta: {
      requiresLoggedOut: true
    }
  },
  {
    path: "/signUp",
    name: "Sign Up Page",
    component: LoginView,
    props: { mode: "signUp" },
    meta: {
      requiresLoggedOut: true
    }
  },
  {
    path: "/courses/:id",
    name: "Course",
    component: CoursePage,
    props: route => ({
      courseId: +route.params.id
    })
  },
  {
    path: "/posts/:postId",
    name: "Post",
    component: PostView,
    props: route => ({
      id: +route.params.postId,
      mode: route.query.edit === "true" ? "edit" : "view"
    })
  },
  {
    path: "/courses/:courseId/posts/new",
    name: "New post",
    component: PostView,
    props: route => ({
      id: +route.params.courseId,
      mode: "new"
    })
  },
  {
    path: "/*",
    name: "404 Page Not Found",
    component: ErrorPage
  }
];

const router = new VueRouter({
  mode: "history",
  base: process.env.BASE_URL,
  routes
});

/**
 * Checks if the user can be on the current route, given the LogInStatus.
 * If the user can't, it is redirected using redirect. If it can, true is returned
 *
 * @param route the current route / the route the user is trying to reach
 * @param loginStatus the current logInStatus
 * @param redirect the function used to change route
 * @return true if the route is legal
 */
const checkLoggedInStateForPage = (
  route: Route,
  loginStatus: LogInStatus,
  redirect: (loc: RawLocation) => void
): boolean => {
  if (loginStatus == "loading") return true;
  if (route.matched.some(r => r.meta.requiresAuth)) {
    if (loginStatus == "loggedIn" || loginStatus == "fakeLoggedIn") return true;
    redirect("/login?reason=invalidated");
    return false;
  } else if (route.matched.some(r => r.meta.requiresLoggedOut)) {
    if (loginStatus == "loggedOut") return true;
    redirect("/");
    return false;
  }
  return true;
};

store.watch(
  (state, getters) => getters.authStatus,
  authStatus => {
    checkLoggedInStateForPage(
      router.currentRoute,
      authStatus,
      router.replace.bind(router)
    );
  }
);

router.beforeEach((to, from, next) => {
  if (checkLoggedInStateForPage(to, store.getters.authStatus, next)) next();
});

export default router;
